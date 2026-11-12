package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ForwardToInbox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.local.LoanEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun EmailNotificationDialog(
    loan: LoanEntity,
    onSendEmail: (recipientEmail: String, subject: String, body: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val now = System.currentTimeMillis()
    val overdueDays = ((now - loan.dueDate) / 86400000L).coerceAtLeast(1)

    var recipientEmail by remember {
        mutableStateOf(loan.studentEmail)
    }

    var subject by remember {
        mutableStateOf("Rappel de bibliothèque USMBA : Restitution d'ouvrage en retard (${loan.bookTitle})")
    }

    var messageBody by remember {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        val initialBody = """
Bonjour ${loan.studentName},

Nous vous informons que votre emprunt pour l'ouvrage suivant auprès de la bibliothèque universitaire USMBA est en retard de restitution de $overdueDays jour(s) :
• Titre de l'ouvrage : ${loan.bookTitle}
• Date d'emprunt : ${dateFormat.format(loan.loanDate)}
• Date limite de retour : ${dateFormat.format(loan.dueDate)}

Merci de bien vouloir rapporter l'exemplaire au guichet de la bibliothèque dès que possible afin d'éviter toute pénalité sur vos futurs emprunts.

Cordialement,
Le Service de Bibliothèque Universitaire
Université Sidi Mohamed Ben Abdellah (USMBA)
        """.trimIndent()
        mutableStateOf(initialBody)
    }

    var statusFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var statusFeedbackIsSuccess by remember { mutableStateOf(true) }

    fun copyToClipboard(showToast: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val fullContent = "Destinataire: $recipientEmail\nObjet: $subject\n\n$messageBody"
        val clip = ClipData.newPlainText("Relance Bibliothèque", fullContent)
        clipboard?.setPrimaryClip(clip)
        if (showToast) {
            Toast.makeText(context, "Texte et adresse copiés dans le presse-papiers !", Toast.LENGTH_SHORT).show()
        }
        statusFeedbackMessage = "Message et destinataire ($recipientEmail) copiés dans le presse-papiers !"
        statusFeedbackIsSuccess = true
    }

    fun launchSystemEmailApp() {
        onSendEmail(recipientEmail, subject, messageBody)

        val mailtoUri = Uri.parse("mailto:${Uri.encode(recipientEmail)}?subject=${Uri.encode(subject)}&body=${Uri.encode(messageBody)}")
        val emailIntent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, messageBody)
        }

        try {
            val chooser = Intent.createChooser(emailIntent, "Envoyer la relance par email")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            statusFeedbackMessage = "Client email ouvert. Appuyez sur Envoyer dans l'application pour expédier le message à $recipientEmail."
            statusFeedbackIsSuccess = true
        } catch (e: Exception) {
            // Fallback si aucune app de messagerie installée sur l'émulateur
            copyToClipboard(showToast = false)
            statusFeedbackMessage = "Aucun client email configuré sur cet appareil. Le texte a été copié dans le presse-papiers pour envoi via Webmail."
            statusFeedbackIsSuccess = false
        }
    }

    fun launchGmailWebCompose() {
        onSendEmail(recipientEmail, subject, messageBody)

        val webComposeUrl = "https://mail.google.com/mail/?view=cm&fs=1&to=${Uri.encode(recipientEmail)}&su=${Uri.encode(subject)}&body=${Uri.encode(messageBody)}"
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webComposeUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            statusFeedbackMessage = "Gmail Web ouvert dans le navigateur avec le message pré-rempli !"
            statusFeedbackIsSuccess = true
        } catch (e: Exception) {
            copyToClipboard(showToast = false)
            statusFeedbackMessage = "Impossible d'ouvrir le navigateur. Texte copié dans le presse-papiers."
            statusFeedbackIsSuccess = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 580.dp)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = "Email",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Relance par email",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Envoi réel vers l'adresse académique de l'étudiant",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Overdue warning banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Retard constaté : $overdueDays jour(s)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Étudiant : ${loan.studentName} • Ouvrage : ${loan.bookTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Destinataire modifiable
                OutlinedTextField(
                    value = recipientEmail,
                    onValueChange = { recipientEmail = it },
                    label = { Text("Adresse email du destinataire") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("email_recipient_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Objet
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Objet de l'email") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("email_subject_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Corps du message
                OutlinedTextField(
                    value = messageBody,
                    onValueChange = { messageBody = it },
                    label = { Text("Corps du message") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .testTag("email_body_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Feedback Banner
                AnimatedVisibility(visible = statusFeedbackMessage != null) {
                    statusFeedbackMessage?.let { msg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (statusFeedbackIsSuccess) Color(0xFFE8F5E9)
                                    else Color(0xFFFFF3E0)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (statusFeedbackIsSuccess) Icons.Default.CheckCircle else Icons.Default.Email,
                                contentDescription = null,
                                tint = if (statusFeedbackIsSuccess) Color(0xFF2E7D32) else Color(0xFFE65100),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (statusFeedbackIsSuccess) Color(0xFF1B5E20) else Color(0xFFBF360C),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Boutons d'action réels
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1 : Application de messagerie par défaut (Gmail, Outlook)
                    Button(
                        onClick = { launchSystemEmailApp() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("button_open_email_app")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ForwardToInbox,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Envoyer avec l'app Email (Gmail / Mail)")
                    }

                    // Option 2 : Ouvrir directement dans Gmail Web Compose
                    OutlinedButton(
                        onClick = { launchGmailWebCompose() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("button_open_gmail_web")
                    ) {
                        Icon(
                            Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ouvrir dans Gmail Web (Navigateur)")
                    }

                    // Option 3 : Copier le texte
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { copyToClipboard() },
                            modifier = Modifier.testTag("button_copy_email_text")
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copier pour Webmail USMBA")
                        }

                        TextButton(onClick = onDismiss) {
                            Text("Fermer")
                        }
                    }
                }
            }
        }
    }
}
