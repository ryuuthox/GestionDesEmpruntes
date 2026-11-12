package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

object GoogleAuthHelper {

    private const val TAG = "GoogleAuthHelper"

    /**
     * Lance la véritable boîte de dialogue de connexion Google (Credential Manager)
     */
    suspend fun signInWithGoogle(
        context: Context
    ): Result<AuthResult> {
        return try {
            val credentialManager = CredentialManager.create(context)
            val serverClientId = try {
                context.getString(R.string.default_web_client_id)
            } catch (e: Exception) {
                Log.e(TAG, "default_web_client_id manquant dans les ressources", e)
                return Result.failure(IllegalStateException("Configuration Google Sign-In introuvable."))
            }

            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId = serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val activity = context as? Activity
                ?: return Result.failure(IllegalStateException("Le contexte doit être une Activity."))

            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                Result.success(authResult)
            } else {
                Result.failure(IllegalStateException("Type d'identifiant Google inattendu."))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Connexion Google annulée par l'utilisateur: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'authentification Google", e)
            Result.failure(e)
        }
    }

    /**
     * Déconnexion complète de Google Credential Manager et Firebase Auth
     */
    suspend fun signOut(context: Context) {
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lors du nettoyage de l'état des identifiants: ${e.message}")
        } finally {
            Firebase.auth.signOut()
        }
    }
}
