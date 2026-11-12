package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.ForwardToInbox
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.window.Dialog
import com.example.data.local.BookEntity
import com.example.data.local.StudentEntity
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.LoanEntity
import com.example.data.repository.AvailabilityCheckResult
import com.example.ui.components.EmailNotificationDialog
import com.example.ui.components.QuickScanOption
import com.example.ui.components.QrScannerModal
import com.example.ui.components.ScanTargetType
import com.example.viewmodel.LibraryViewModel
import com.example.viewmodel.LoanFilter
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun GerantScreen(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier
) {
    var selectedGerantTab by remember { mutableIntStateOf(0) }
    val overdueCount by viewModel.overdueLoansCount.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedGerantTab,
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedGerantTab == 0,
                onClick = { selectedGerantTab = 0 },
                text = { Text("Nouvel emprunt") },
                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                modifier = Modifier.testTag("tab_register_loan")
            )
            Tab(
                selected = selectedGerantTab == 1,
                onClick = { selectedGerantTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Prêts & Retards")
                        if (overdueCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ) {
                                Text("$overdueCount")
                            }
                        }
                    }
                },
                icon = { Icon(Icons.Default.EventBusy, contentDescription = null) },
                modifier = Modifier.testTag("tab_track_loans")
            )
            Tab(
                selected = selectedGerantTab == 2,
                onClick = { selectedGerantTab = 2 },
                text = { Text("Historique relances") },
                icon = { Icon(Icons.Default.MarkEmailRead, contentDescription = null) },
                modifier = Modifier.testTag("tab_email_logs")
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            when (selectedGerantTab) {
                0 -> LoanRegistrationSection(viewModel)
                1 -> LoansTrackingSection(viewModel)
                2 -> EmailNotificationLogsSection(viewModel)
            }
        }
    }
}

/**
 * Enregistrement d'un emprunt (par QR Code)
 * 1. Scan de la carte d'étudiant
 * 2. Scan du QR Code de l'ouvrage
 * 3. Contrôle de la disponibilité et validation du prêt
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoanRegistrationSection(viewModel: LibraryViewModel) {
    val registrationState by viewModel.registrationState.collectAsStateWithLifecycle()
    val allStudents by viewModel.allStudents.collectAsStateWithLifecycle()
    val allBooks by viewModel.allBooks.collectAsStateWithLifecycle()

    var activeScannerTarget by remember { mutableStateOf<ScanTargetType?>(null) }
    var selectedDurationDays by remember { mutableIntStateOf(14) }

    // Scanner Modal
    activeScannerTarget?.let { target ->
        val quickOptions = if (target == ScanTargetType.STUDENT_CARD) {
            allStudents.map {
                QuickScanOption(
                    label = it.fullName,
                    code = it.studentCardQr,
                    subtitle = "${it.studentNumber} • ${it.department}"
                )
            }
        } else {
            allBooks.map {
                val stockText = if (it.availableCopies > 0) "En stock (${it.availableCopies})" else "Indisponible"
                QuickScanOption(
                    label = it.title,
                    code = it.qrCode,
                    subtitle = "${it.author} • $stockText"
                )
            }
        }

        QrScannerModal(
            targetType = target,
            quickOptions = quickOptions,
            onCodeScanned = { scannedCode ->
                activeScannerTarget = null
                if (target == ScanTargetType.STUDENT_CARD) {
                    viewModel.onScanStudentCard(scannedCode)
                } else {
                    viewModel.onScanBookQr(scannedCode)
                }
            },
            onDismiss = { activeScannerTarget = null }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // En-tête de section
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enregistrement d'un emprunt",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Identification étudiant, sélection de l'ouvrage et validation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Messages d'état
        if (registrationState.successMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = registrationState.successMessage ?: "",
                            color = Color(0xFF1B5E20),
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        if (registrationState.errorMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = registrationState.errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // ÉTAPE 1 : Scanner la carte d'étudiant
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("step_scan_student_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (registrationState.student != null)
                        MaterialTheme.colorScheme.surfaceVariant
                    else
                        MaterialTheme.colorScheme.surface
                ),
                border = if (registrationState.student != null)
                    null
                else
                    borderStroke(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (registrationState.student != null) Color(0xFF2E7D32)
                                    else MaterialTheme.colorScheme.primary,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (registrationState.student != null) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Text("1", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "1. Carte d'étudiant",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Scanner la carte pour identifier l'emprunteur",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { activeScannerTarget = ScanTargetType.STUDENT_CARD },
                            modifier = Modifier.testTag("button_scan_student")
                        ) {
                            Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (registrationState.student == null) "Scanner" else "Re-scanner")
                        }
                    }

                    if (registrationState.student != null) {
                        val s = registrationState.student!!
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.fullName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("${s.studentNumber} • ${s.department}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(s.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // ÉTAPE 2 : Scanner le QR Code de l'ouvrage
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("step_scan_book_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (registrationState.book != null)
                        MaterialTheme.colorScheme.surfaceVariant
                    else
                        MaterialTheme.colorScheme.surface
                ),
                border = if (registrationState.book != null)
                    null
                else
                    borderStroke(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (registrationState.book != null) Color(0xFF2E7D32)
                                    else MaterialTheme.colorScheme.primary,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (registrationState.book != null) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Text("2", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "2. QR Code de l'ouvrage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Scanner le code pour identifier le livre",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { activeScannerTarget = ScanTargetType.BOOK_QR },
                            modifier = Modifier.testTag("button_scan_book")
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (registrationState.book == null) "Scanner" else "Re-scanner")
                        }
                    }

                    if (registrationState.book != null) {
                        val b = registrationState.book!!
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(b.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("${b.author} (${b.publishYear}) • ${b.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("ISBN : ${b.isbn} • ${b.shelfLocation}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // ÉTAPE 3 : Contrôle de la disponibilité et choix de durée
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("step_check_availability_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (registrationState.availability) {
                        is AvailabilityCheckResult.Available -> Color(0xFFF1F8E9)
                        is AvailabilityCheckResult.NotAvailable -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                border = borderStroke(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    when (registrationState.availability) {
                                        is AvailabilityCheckResult.Available -> Color(0xFF2E7D32)
                                        is AvailabilityCheckResult.NotAvailable -> MaterialTheme.colorScheme.error
                                        else -> Color.Gray
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("3", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "3. Vérification de la disponibilité",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Contrôle automatique du stock en temps réel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    when (val check = registrationState.availability) {
                        is AvailabilityCheckResult.Available -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFDCEDC8))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Ouvrage disponible pour le prêt !",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20)
                                    )
                                    Text(
                                        text = "${check.remainingCopies} exemplaire(s) en rayon prêt(s) à être emprunté(s).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text("Durée du prêt :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            ) {
                                items(listOf(7, 14, 21, 30)) { days ->
                                    FilterChip(
                                        selected = selectedDurationDays == days,
                                        onClick = { selectedDurationDays = days },
                                        label = { Text("$days jours") }
                                    )
                                }
                            }
                        }

                        is AvailabilityCheckResult.NotAvailable -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Ouvrage indisponible !",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    val loan = check.currentLoan
                                    if (loan != null) {
                                        val df = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
                                        Text(
                                            text = "Actuellement emprunté par ${loan.studentName}.\nDate de retour prévue : ${df.format(loan.dueDate)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    } else {
                                        Text(
                                            text = "Aucun exemplaire disponible en stock.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }

                        null -> {
                            Text(
                                text = "En attente du scan d'un ouvrage pour vérification...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AvailabilityCheckResult.NotFound -> {
                            Text(
                                text = "Ouvrage non répertorié dans la base.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // BOUTONS D'ACTION
        item {
            val isReady = registrationState.student != null &&
                    registrationState.book != null &&
                    registrationState.availability is AvailabilityCheckResult.Available

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.resetRegistration() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Réinitialiser")
                }

                Button(
                    onClick = { viewModel.confirmLoanRegistration(selectedDurationDays) },
                    enabled = isReady && !registrationState.isRegistering,
                    modifier = Modifier
                        .weight(1.6f)
                        .testTag("button_confirm_loan"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Valider l'emprunt")
                }
            }
        }
    }
}

/**
 * Suivi des emprunts et des retards
 * - Liste des prêts avec filtres fluides
 * - Relance par email des retards (unitaire ou groupée)
 */
@Composable
private fun LoansTrackingSection(viewModel: LibraryViewModel) {
    val currentFilter by viewModel.loanFilter.collectAsStateWithLifecycle()
    val loans by viewModel.filteredLoans.collectAsStateWithLifecycle()
    val allLoans by viewModel.allLoans.collectAsStateWithLifecycle()
    val selectedLoanForEmail by viewModel.selectedLoanForEmail.collectAsStateWithLifecycle()

    val now = System.currentTimeMillis()
    val activeLoansCount = remember(allLoans) { allLoans.count { !it.isReturned } }
    val overdueLoansCount = remember(allLoans, now) { allLoans.count { !it.isReturned && it.dueDate < now } }
    val returnedLoansCount = remember(allLoans) { allLoans.count { it.isReturned } }

    // Dialog d'envoi d'email
    selectedLoanForEmail?.let { loan ->
        EmailNotificationDialog(
            loan = loan,
            onSendEmail = { recipient, subject, body ->
                viewModel.dispatchEmailReminder(recipient, subject, body)
            },
            onDismiss = { viewModel.closeEmailReminderDialog() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Alerte et relance groupée pour les retards
        if (overdueLoansCount > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Relance des retards constatés",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "$overdueLoansCount emprunt(s) dépassent la date limite.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { viewModel.triggerBulkOverdueReminders() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("button_bulk_email_reminders")
                    ) {
                        Icon(Icons.Default.ForwardToInbox, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tout relancer")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Filtres défilables (LazyRow responsive)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = currentFilter == LoanFilter.ALL,
                    onClick = { viewModel.setLoanFilter(LoanFilter.ALL) },
                    label = { Text("Tous (${allLoans.size})") },
                    modifier = Modifier.testTag("filter_loans_all")
                )
            }
            item {
                FilterChip(
                    selected = currentFilter == LoanFilter.ACTIVE,
                    onClick = { viewModel.setLoanFilter(LoanFilter.ACTIVE) },
                    label = { Text("En cours ($activeLoansCount)") },
                    modifier = Modifier.testTag("filter_loans_active")
                )
            }
            item {
                FilterChip(
                    selected = currentFilter == LoanFilter.OVERDUE,
                    onClick = { viewModel.setLoanFilter(LoanFilter.OVERDUE) },
                    label = { Text("En retard ($overdueLoansCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.testTag("filter_loans_overdue")
                )
            }
            item {
                FilterChip(
                    selected = currentFilter == LoanFilter.RETURNED,
                    onClick = { viewModel.setLoanFilter(LoanFilter.RETURNED) },
                    label = { Text("Retournés ($returnedLoansCount)") },
                    modifier = Modifier.testTag("filter_loans_returned")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (loans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun emprunt dans cette catégorie.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(loans, key = { it.id }) { loan ->
                    LoanItemCard(
                        loan = loan,
                        onMarkReturned = { viewModel.markLoanAsReturned(loan.id) },
                        onSendEmail = { viewModel.openEmailReminderDialog(loan) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoanItemCard(
    loan: LoanEntity,
    onMarkReturned: () -> Unit,
    onSendEmail: () -> Unit
) {
    val now = System.currentTimeMillis()
    val isOverdue = !loan.isReturned && loan.dueDate < now
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE) }

    val overdueDays = if (isOverdue) {
        ((now - loan.dueDate) / 86400000L).coerceAtLeast(1)
    } else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("loan_card_${loan.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                loan.isReturned -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = borderStroke(
            if (isOverdue) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Ligne supérieure : Titre et badge de statut
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loan.bookTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Emprunteur : ${loan.studentName} (${loan.studentEmail})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Badge de statut
                when {
                    loan.isReturned -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE0E0E0))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Retourné", style = MaterialTheme.typography.labelSmall, color = Color(0xFF424242))
                        }
                    }
                    isOverdue -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.error)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("$overdueDays j de retard", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val daysLeft = ((loan.dueDate - now) / 86400000L).coerceAtLeast(0)
                            Text("En cours ($daysLeft j restants)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dates d'emprunt et limite
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Prêté le : ${dateFormat.format(loan.loanDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "À rendre le : ${dateFormat.format(loan.dueDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal,
                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (loan.lastEmailReminderSent != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.MarkEmailRead,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Relancé le ${dateFormat.format(loan.lastEmailReminderSent)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            // Actions d'enregistrement de retour et de relance par email
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!loan.isReturned) {
                    OutlinedButton(
                        onClick = onMarkReturned,
                        modifier = Modifier.testTag("button_return_loan_${loan.id}")
                    ) {
                        Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enregistrer retour")
                    }
                }

                if (isOverdue) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSendEmail,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("button_email_reminder_${loan.id}")
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Relancer par email")
                    }
                }
            }
        }
    }
}

/**
 * Historique des notifications d'emprunt transmises par email
 */
@Composable
private fun EmailNotificationLogsSection(viewModel: LibraryViewModel) {
    val emailLogs by viewModel.emailLogs.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ForwardToInbox, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Historique des notifications email",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Suivi des rappels et relances expédiés aux étudiants",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (emailLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucune relance envoyée pour l'instant.\nUtilisez le bouton 'Relancer par email' sur un emprunt en retard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(emailLogs, key = { it.id }) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = log.recipientName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = dateFormat.format(log.sentTimestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Destinataire : ${log.recipientEmail}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Ouvrage : ${log.bookTitle}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = log.subject,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = log.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun borderStroke(color: Color) = androidx.compose.foundation.BorderStroke(1.dp, color)
