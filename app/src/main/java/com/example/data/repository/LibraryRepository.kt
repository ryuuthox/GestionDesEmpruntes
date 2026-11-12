package com.example.data.repository

import com.example.data.local.BookEntity
import com.example.data.local.EmailNotificationLogEntity
import com.example.data.local.LibraryDao
import com.example.data.local.LoanEntity
import com.example.data.local.StudentEntity
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

sealed class AvailabilityCheckResult {
    data class Available(val book: BookEntity, val remainingCopies: Int) : AvailabilityCheckResult()
    data class NotAvailable(val book: BookEntity, val currentLoan: LoanEntity?) : AvailabilityCheckResult()
    object NotFound : AvailabilityCheckResult()
}

class LibraryRepository(private val dao: LibraryDao) {

    val allBooks: Flow<List<BookEntity>> = dao.getAllBooks()
    val allStudents: Flow<List<StudentEntity>> = dao.getAllStudents()
    val allLoans: Flow<List<LoanEntity>> = dao.getAllLoans()
    val activeLoans: Flow<List<LoanEntity>> = dao.getActiveLoans()
    val emailLogs: Flow<List<EmailNotificationLogEntity>> = dao.getAllEmailLogs()
    val categories: Flow<List<String>> = dao.getAllCategories()

    fun getOverdueLoans(currentTime: Long = System.currentTimeMillis()): Flow<List<LoanEntity>> {
        return dao.getOverdueLoans(currentTime)
    }

    fun searchBooks(
        query: String,
        category: String,
        availability: String,
        sortBy: String
    ): Flow<List<BookEntity>> {
        return dao.searchBooks(query.trim(), category, availability, sortBy)
    }

    suspend fun getStudentByCardQr(qrCode: String): StudentEntity? {
        val cleaned = qrCode.trim()
        return dao.getStudentByQrCode(cleaned) ?: dao.findStudentFlexible(cleaned)
    }

    suspend fun getBookByQrCode(qrCode: String): BookEntity? {
        val cleaned = qrCode.trim()
        return dao.getBookByQrCode(cleaned) ?: dao.getBookByIsbn(cleaned) ?: dao.findBookFlexible(cleaned)
    }

    suspend fun insertBook(book: BookEntity): Long {
        return dao.insertBook(book)
    }

    suspend fun checkBookAvailability(qrCode: String): AvailabilityCheckResult {
        val book = getBookByQrCode(qrCode) ?: return AvailabilityCheckResult.NotFound
        return if (book.availableCopies > 0) {
            AvailabilityCheckResult.Available(book, book.availableCopies)
        } else {
            val activeLoan = dao.getActiveLoanForBook(book.id)
            AvailabilityCheckResult.NotAvailable(book, activeLoan)
        }
    }

    suspend fun checkBookAvailabilityById(bookId: Long): AvailabilityCheckResult {
        val book = dao.getBookById(bookId) ?: return AvailabilityCheckResult.NotFound
        return if (book.availableCopies > 0) {
            AvailabilityCheckResult.Available(book, book.availableCopies)
        } else {
            val activeLoan = dao.getActiveLoanForBook(book.id)
            AvailabilityCheckResult.NotAvailable(book, activeLoan)
        }
    }

    suspend fun registerLoan(
        student: StudentEntity,
        book: BookEntity,
        durationDays: Int = 14
    ): Result<LoanEntity> {
        val availability = checkBookAvailabilityById(book.id)
        if (availability !is AvailabilityCheckResult.Available) {
            return Result.failure(IllegalStateException("L'ouvrage '${book.title}' n'est pas disponible pour l'emprunt."))
        }

        val now = System.currentTimeMillis()
        val dueDate = now + TimeUnit.DAYS.toMillis(durationDays.toLong())

        val newLoan = LoanEntity(
            bookId = book.id,
            bookTitle = book.title,
            bookQrCode = book.qrCode,
            studentId = student.id,
            studentName = student.fullName,
            studentEmail = student.email,
            studentCardQr = student.studentCardQr,
            loanDate = now,
            dueDate = dueDate,
            returnDate = null,
            isReturned = false,
            lastEmailReminderSent = null
        )

        val insertedId = dao.insertLoan(newLoan)

        // Decrement available copies
        dao.updateBook(book.copy(availableCopies = (book.availableCopies - 1).coerceAtLeast(0)))

        return Result.success(newLoan.copy(id = insertedId))
    }

    suspend fun returnLoan(loanId: Long): Result<Unit> {
        val loan = dao.getLoanById(loanId) ?: return Result.failure(IllegalArgumentException("Emprunt introuvable"))
        if (loan.isReturned) {
            return Result.failure(IllegalStateException("Cet emprunt a déjà été retourné."))
        }

        val updatedLoan = loan.copy(
            isReturned = true,
            returnDate = System.currentTimeMillis()
        )
        dao.updateLoan(updatedLoan)

        val book = dao.getBookById(loan.bookId)
        if (book != null) {
            dao.updateBook(book.copy(availableCopies = (book.availableCopies + 1).coerceAtMost(book.totalCopies)))
        }

        return Result.success(Unit)
    }

    suspend fun sendEmailReminder(
        loan: LoanEntity,
        recipientEmail: String = loan.studentEmail,
        subject: String = "Rappel de bibliothèque : Retard de restitution d'ouvrage",
        customMessage: String? = null
    ): Result<EmailNotificationLogEntity> {
        val now = System.currentTimeMillis()
        val overdueDays = ((now - loan.dueDate) / 86400000L).coerceAtLeast(1)

        val body = customMessage ?: """
            Bonjour ${loan.studentName},
            
            Sauf erreur de notre part, l'ouvrage suivant emprunté auprès de la bibliothèque universitaire est en retard de restitution de $overdueDays jour(s) :
            
            - Titre : ${loan.bookTitle}
            - Date d'emprunt : ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRANCE).format(loan.loanDate)}
            - Date limite de retour : ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRANCE).format(loan.dueDate)}
            
            Merci de rapporter cet ouvrage à la bibliothèque dans les plus brefs délais ou de contacter le gérant.
            
            Cordialement,
            Le Service Bibliothèque Universitaire
        """.trimIndent()

        val log = EmailNotificationLogEntity(
            loanId = loan.id,
            recipientEmail = recipientEmail.trim(),
            recipientName = loan.studentName,
            bookTitle = loan.bookTitle,
            subject = subject,
            messageBody = body,
            sentTimestamp = now,
            status = "Transmis au client de messagerie"
        )

        dao.insertEmailLog(log)
        dao.updateLoan(loan.copy(lastEmailReminderSent = now))

        return Result.success(log)
    }

    suspend fun sendBulkEmailReminders(overdueLoans: List<LoanEntity>): Int {
        var count = 0
        for (loan in overdueLoans) {
            val result = sendEmailReminder(loan)
            if (result.isSuccess) count++
        }
        return count
    }

    suspend fun getStudentByEmail(email: String): StudentEntity? {
        return dao.getStudentByEmail(email.trim())
    }

    suspend fun insertStudent(student: StudentEntity): Long {
        return dao.insertStudent(student)
    }

    suspend fun getSavedSessionEmail(): String? {
        return dao.getSetting("logged_in_email")
    }

    suspend fun saveSessionEmail(email: String) {
        dao.setSetting(com.example.data.local.AppSettingEntity("logged_in_email", email.trim()))
    }

    suspend fun clearSessionEmail() {
        dao.deleteSetting("logged_in_email")
    }
}
