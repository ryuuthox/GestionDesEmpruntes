package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.BookEntity
import com.example.data.local.EmailNotificationLogEntity
import com.example.data.local.LoanEntity
import com.example.data.local.StudentEntity
import com.example.data.repository.AvailabilityCheckResult
import com.example.data.repository.LibraryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppRole {
    GERANT,
    UTILISATEUR
}

data class AuthenticatedUser(
    val email: String,
    val role: AppRole,
    val displayName: String,
    val studentProfile: StudentEntity? = null
)

sealed class AuthState {
    object Loading : AuthState()
    object LoggedOut : AuthState()
    data class Authenticated(val user: AuthenticatedUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

enum class LoanFilter {
    ALL,
    ACTIVE,
    OVERDUE,
    RETURNED
}

data class LoanRegistrationState(
    val student: StudentEntity? = null,
    val book: BookEntity? = null,
    val availability: AvailabilityCheckResult? = null,
    val isRegistering: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LibraryRepository

    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = LibraryRepository(database.libraryDao())
        restoreSavedSession()
    }

    // -------------------------------------------------------------
    // AUTHENTICATION & SESSION STATE
    // -------------------------------------------------------------
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentRole = MutableStateFlow(AppRole.GERANT)
    val currentRole: StateFlow<AppRole> = _currentRole.asStateFlow()

    private val _loggedInUser = MutableStateFlow<AuthenticatedUser?>(null)
    val loggedInUser: StateFlow<AuthenticatedUser?> = _loggedInUser.asStateFlow()

    private fun restoreSavedSession() {
        viewModelScope.launch {
            val savedEmail = repository.getSavedSessionEmail()
            if (!savedEmail.isNullOrBlank()) {
                val user = resolveUserByEmail(savedEmail)
                if (user != null) {
                    _loggedInUser.value = user
                    _currentRole.value = user.role
                    _authState.value = AuthState.Authenticated(user)
                    return@launch
                }
            }
            _authState.value = AuthState.LoggedOut
        }
    }

    private suspend fun resolveUserByEmail(email: String, displayNameOverride: String? = null): AuthenticatedUser? {
        val cleaned = email.trim().lowercase()
        // Special Manager Account (including developer admin and official USMBA aliases)
        if (cleaned == "gerant@usmba.ac.ma" || cleaned == "admin@usmba.ac.ma" || cleaned == "bibliotheque@usmba.ac.ma"
            || cleaned == "ryuuthox@gmail.com" || cleaned.startsWith("gerant@") || cleaned.startsWith("admin@")) {
            return AuthenticatedUser(
                email = email.trim(),
                role = AppRole.GERANT,
                displayName = displayNameOverride ?: "Gérant de Bibliothèque USMBA"
            )
        }

        // Student Account lookup in database
        val student = repository.getStudentByEmail(cleaned)
        if (student != null) {
            return AuthenticatedUser(
                email = student.email,
                role = AppRole.UTILISATEUR,
                displayName = displayNameOverride ?: student.fullName,
                studentProfile = student
            )
        }

        // Test student exceptions (enttaim8@gmail.com, musixa360@gmail.com) or @usmba.ac.ma domain
        if (cleaned == "enttaim8@gmail.com" || cleaned == "musixa360@gmail.com" || cleaned.endsWith("@usmba.ac.ma")) {
            val prefix = if (cleaned.endsWith("@usmba.ac.ma")) cleaned.substringBefore("@usmba.ac.ma") else cleaned.substringBefore("@")
            val nameParts = prefix.split(".", "_", "-")
            val derivedName = displayNameOverride ?: nameParts.joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            val cleanHash = (cleaned.hashCode().let { if (it < 0) -it else it } % 9000) + 1000
            val dynamicStudent = StudentEntity(
                studentCardQr = "STUDENT-USMBA-$cleanHash",
                studentNumber = "USMBA-$cleanHash",
                fullName = if (derivedName.isNotBlank() && derivedName != "Enttaim8" && derivedName != "Musixa360") derivedName else (if (cleaned == "enttaim8@gmail.com") "Enttaim Test" else "Musixa Test"),
                email = cleaned,
                department = "Université Sidi Mohamed Ben Abdellah",
                phone = "06 00 00 00 00"
            )
            val newId = repository.insertStudent(dynamicStudent)
            val savedProfile = dynamicStudent.copy(id = newId)
            return AuthenticatedUser(
                email = cleaned,
                role = AppRole.UTILISATEUR,
                displayName = savedProfile.fullName,
                studentProfile = savedProfile
            )
        }

        return null
    }

    fun handleFirebaseGoogleUser(firebaseUser: com.google.firebase.auth.FirebaseUser) {
        val email = firebaseUser.email?.trim()?.lowercase() ?: ""
        val displayName = firebaseUser.displayName

        if (email.isBlank()) {
            _authState.value = AuthState.Error("Impossible de récupérer l'adresse email associée à ce compte Google.")
            return
        }

        viewModelScope.launch {
            val user = resolveUserByEmail(email, displayName)
            if (user != null) {
                repository.saveSessionEmail(user.email)
                _loggedInUser.value = user
                _currentRole.value = user.role
                _authState.value = AuthState.Authenticated(user)
            } else {
                _authState.value = AuthState.Error(
                    "Compte Google ($email) connecté : L'accès à la bibliothèque est réservé aux comptes académiques @usmba.ac.ma ou aux gérants autorisés."
                )
            }
        }
    }

    fun loginWithGoogleAcademic(accountEmail: String) {
        val trimmed = accountEmail.trim().lowercase()
        if (!trimmed.endsWith("@usmba.ac.ma") && trimmed != "enttaim8@gmail.com" && trimmed != "musixa360@gmail.com") {
            _authState.value = AuthState.Error(
                "Accès restreint : Veuillez choisir votre compte institutionnel Google (@usmba.ac.ma)."
            )
            return
        }

        loginWithEmail(trimmed)
    }

    fun setAuthError(errorMessage: String) {
        _authState.value = AuthState.Error(errorMessage)
    }

    fun loginWithEmail(email: String) {
        val trimmed = email.trim().lowercase()
        if (trimmed.isBlank()) {
            _authState.value = AuthState.Error("Veuillez saisir votre adresse email académique USMBA.")
            return
        }

        if (!trimmed.contains("@")) {
            _authState.value = AuthState.Error("Format d'adresse email invalide.")
            return
        }

        if (!trimmed.endsWith("@usmba.ac.ma") && trimmed != "enttaim8@gmail.com" && trimmed != "musixa360@gmail.com") {
            _authState.value = AuthState.Error(
                "Le domaine doit être @usmba.ac.ma (Université Sidi Mohamed Ben Abdellah)."
            )
            return
        }

        viewModelScope.launch {
            val user = resolveUserByEmail(trimmed)
            if (user != null) {
                repository.saveSessionEmail(user.email)
                _loggedInUser.value = user
                _currentRole.value = user.role
                _authState.value = AuthState.Authenticated(user)
            } else {
                _authState.value = AuthState.Error(
                    "Aucun compte académique trouvé pour '$trimmed'."
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.clearSessionEmail()
            _loggedInUser.value = null
            _authState.value = AuthState.LoggedOut
        }
    }

    fun switchRole(role: AppRole) {
        _currentRole.value = role
    }

    // Data streams
    val allStudents: StateFlow<List<StudentEntity>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBooks: StateFlow<List<BookEntity>> = repository.allBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val emailLogs: StateFlow<List<EmailNotificationLogEntity>> = repository.emailLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // -------------------------------------------------------------
    // USE CASE 1: Enregistrer un emprunt (via QR Code)
    // -------------------------------------------------------------
    private val _registrationState = MutableStateFlow(LoanRegistrationState())
    val registrationState: StateFlow<LoanRegistrationState> = _registrationState.asStateFlow()

    fun onScanStudentCard(scannedCode: String) {
        val trimmed = scannedCode.trim()
        viewModelScope.launch {
            // 1. Recherche normale de carte étudiante
            val student = repository.getStudentByCardQr(trimmed)
            if (student != null) {
                _registrationState.value = _registrationState.value.copy(
                    student = student,
                    errorMessage = null
                )
                return@launch
            }

            // 2. Détection intelligente : l'utilisateur a-t-il scanné un livre existant ?
            val possibleBook = repository.getBookByQrCode(trimmed)
            if (possibleBook != null) {
                val availability = repository.checkBookAvailabilityById(possibleBook.id)
                _registrationState.value = _registrationState.value.copy(
                    book = possibleBook,
                    availability = availability,
                    errorMessage = "Ouvrage '${possibleBook.title}' reconnu et sélectionné ! Scannez maintenant la carte de l'étudiant."
                )
                return@launch
            }

            // 3. Détection intelligente : l'utilisateur a-t-il scanné une URL de livre ou un ISBN ?
            if (trimmed.contains("/book/", ignoreCase = true) ||
                trimmed.contains("jarir", ignoreCase = true) ||
                trimmed.contains("isbn", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.matches(Regex("^[0-9\\-]{10,17}$"))
            ) {
                _registrationState.value = _registrationState.value.copy(
                    errorMessage = "Code de livre détecté ('$trimmed') au lieu d'une carte étudiante. Scannez la carte étudiante de l'emprunteur ou choisissez-le dans la liste ci-dessous."
                )
                return@launch
            }

            _registrationState.value = _registrationState.value.copy(
                errorMessage = "Carte étudiante non reconnue : '$trimmed'. Vous pouvez également choisir l'étudiant dans la liste ci-dessous."
            )
        }
    }

    fun onScanBookQr(scannedCode: String) {
        val trimmed = scannedCode.trim()
        viewModelScope.launch {
            // 1. Recherche normale de livre
            val book = repository.getBookByQrCode(trimmed)
            if (book != null) {
                val availability = repository.checkBookAvailabilityById(book.id)
                _registrationState.value = _registrationState.value.copy(
                    book = book,
                    availability = availability,
                    errorMessage = null
                )
                return@launch
            }

            // 2. Détection intelligente : l'utilisateur a-t-il scanné une carte étudiante ?
            val possibleStudent = repository.getStudentByCardQr(trimmed)
            if (possibleStudent != null) {
                _registrationState.value = _registrationState.value.copy(
                    student = possibleStudent,
                    errorMessage = "Carte de ${possibleStudent.fullName} reconnue et enregistrée ! Scannez maintenant le QR code du livre."
                )
                return@launch
            }

            // 3. Code livre inconnu (ex: jarirreader.com ou nouvel ISBN)
            _registrationState.value = _registrationState.value.copy(
                errorMessage = "Ouvrage non répertorié dans la bibliothèque pour le code : '$trimmed'. Vous pouvez le choisir dans le catalogue ou l'ajouter."
            )
        }
    }

    fun selectStudent(student: StudentEntity) {
        _registrationState.value = _registrationState.value.copy(
            student = student,
            errorMessage = null
        )
    }

    fun selectBook(book: BookEntity) {
        viewModelScope.launch {
            val availability = repository.checkBookAvailabilityById(book.id)
            _registrationState.value = _registrationState.value.copy(
                book = book,
                availability = availability,
                errorMessage = null
            )
        }
    }

    fun clearStudent() {
        _registrationState.value = _registrationState.value.copy(student = null)
    }

    fun clearBook() {
        _registrationState.value = _registrationState.value.copy(
            book = null,
            availability = null
        )
    }

    fun quickAddBook(title: String, author: String, qrCode: String, isbn: String = "", category: String = "Informatique & Sciences") {
        viewModelScope.launch {
            val cleanQr = qrCode.trim()
            val newBook = BookEntity(
                title = title.trim(),
                author = author.trim().ifBlank { "Auteur USMBA" },
                isbn = isbn.trim().ifBlank { cleanQr.substringAfterLast("/").ifBlank { "ISBN-${System.currentTimeMillis() % 100000}" } },
                category = category,
                publishYear = 2024,
                totalCopies = 3,
                availableCopies = 3,
                shelfLocation = "Rayon Général - Guichet",
                description = "Ouvrage enregistré via le lecteur de codes",
                qrCode = cleanQr
            )
            val newId = repository.insertBook(newBook)
            selectBook(newBook.copy(id = newId))
        }
    }

    fun confirmLoanRegistration(loanDurationDays: Int = 14) {
        val current = _registrationState.value
        val student = current.student
        val book = current.book

        if (student == null) {
            _registrationState.value = current.copy(errorMessage = "Veuillez scanner la carte d'étudiant.")
            return
        }
        if (book == null) {
            _registrationState.value = current.copy(errorMessage = "Veuillez scanner le QR Code de l'ouvrage.")
            return
        }
        if (current.availability !is AvailabilityCheckResult.Available) {
            _registrationState.value = current.copy(errorMessage = "L'ouvrage n'est pas disponible pour l'emprunt.")
            return
        }

        viewModelScope.launch {
            _registrationState.value = current.copy(isRegistering = true)
            val result = repository.registerLoan(student, book, loanDurationDays)
            if (result.isSuccess) {
                _registrationState.value = LoanRegistrationState(
                    successMessage = "Emprunt validé avec succès pour '${book.title}' par ${student.fullName} !"
                )
            } else {
                _registrationState.value = current.copy(
                    isRegistering = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Erreur lors de l'enregistrement."
                )
            }
        }
    }

    fun resetRegistration() {
        _registrationState.value = LoanRegistrationState()
    }

    // -------------------------------------------------------------
    // USE CASE 2: Suivre les emprunts et les retards
    // -------------------------------------------------------------
    private val _loanFilter = MutableStateFlow(LoanFilter.ALL)
    val loanFilter: StateFlow<LoanFilter> = _loanFilter.asStateFlow()

    fun setLoanFilter(filter: LoanFilter) {
        _loanFilter.value = filter
    }

    val allLoans: StateFlow<List<LoanEntity>> = repository.allLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredLoans: StateFlow<List<LoanEntity>> = combine(
        repository.allLoans,
        _loanFilter
    ) { loans, filter ->
        val now = System.currentTimeMillis()
        when (filter) {
            LoanFilter.ALL -> loans
            LoanFilter.ACTIVE -> loans.filter { !it.isReturned }
            LoanFilter.OVERDUE -> loans.filter { !it.isReturned && it.dueDate < now }
            LoanFilter.RETURNED -> loans.filter { it.isReturned }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val overdueLoansCount: StateFlow<Int> = repository.allLoans.combine(MutableStateFlow(Unit)) { loans, _ ->
        val now = System.currentTimeMillis()
        loans.count { !it.isReturned && it.dueDate < now }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markLoanAsReturned(loanId: Long) {
        viewModelScope.launch {
            repository.returnLoan(loanId)
        }
    }

    // Email dispatch via Service Email
    private val _selectedLoanForEmail = MutableStateFlow<LoanEntity?>(null)
    val selectedLoanForEmail: StateFlow<LoanEntity?> = _selectedLoanForEmail.asStateFlow()

    fun openEmailReminderDialog(loan: LoanEntity) {
        _selectedLoanForEmail.value = loan
    }

    fun closeEmailReminderDialog() {
        _selectedLoanForEmail.value = null
    }

    fun dispatchEmailReminder(recipientEmail: String, subject: String, body: String) {
        val loan = _selectedLoanForEmail.value ?: return
        viewModelScope.launch {
            repository.sendEmailReminder(loan, recipientEmail, subject, body)
        }
    }

    fun triggerBulkOverdueReminders() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val overdue = allLoans.value.filter { !it.isReturned && it.dueDate < now }
            repository.sendBulkEmailReminders(overdue)
        }
    }

    // -------------------------------------------------------------
    // USE CASE 3: Rechercher un ouvrage (Multicritère) [Utilisateur]
    // -------------------------------------------------------------
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("Tous")
    val selectedAvailability = MutableStateFlow("Tous")
    val selectedSortBy = MutableStateFlow("title")

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<BookEntity>> = combine(
        searchQuery,
        selectedCategory,
        selectedAvailability,
        selectedSortBy
    ) { query, category, availability, sortBy ->
        SearchCriteria(query, category, availability, sortBy)
    }.flatMapLatest { criteria ->
        repository.searchBooks(
            query = criteria.query,
            category = criteria.category,
            availability = criteria.availability,
            sortBy = criteria.sortBy
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateSelectedCategory(category: String) {
        selectedCategory.value = category
    }

    fun updateSelectedAvailability(availability: String) {
        selectedAvailability.value = availability
    }

    fun updateSelectedSortBy(sortBy: String) {
        selectedSortBy.value = sortBy
    }

    private data class SearchCriteria(
        val query: String,
        val category: String,
        val availability: String,
        val sortBy: String
    )
}
