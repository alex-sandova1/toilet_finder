package com.example.driverassist.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.driverassist.MainPage
import com.example.driverassist.ui.components.AppLogo
import com.example.driverassist.ui.theme.DriverAssistTheme
import com.example.driverassist.util.printSigningFingerprint
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        printSigningFingerprint(this)

        // If user is already logged in, go straight to MainPage
        if (FirebaseAuth.getInstance().currentUser != null) {
            startActivity(Intent(this, MainPage::class.java))
            finish()
            return
        }

        setContent {
            DriverAssistTheme {
                LoginScreen(
                    onLoginSuccess = {
                        startActivity(Intent(this, MainPage::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    
    // Web Client ID from Firebase Console
    val webClientId = stringResource(id = com.example.driverassist.R.string.default_web_client_id)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppLogo(modifier = Modifier.padding(bottom = 32.dp))
        Spacer(modifier = Modifier.height(32.dp))
        
        if (viewModel.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    viewModel.setLoadingState(true)
                    
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(webClientId)
                        .setAutoSelectEnabled(false)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    coroutineScope.launch {
                        try {
                            val result = credentialManager.getCredential(
                                context = context,
                                request = request
                            )
                            
                            when (val credential = result.credential) {
                                is GoogleIdTokenCredential -> {
                                    val idToken = credential.idToken
                                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                                    auth.signInWithCredential(firebaseCredential)
                                        .addOnCompleteListener { authTask ->
                                            if (authTask.isSuccessful) {
                                                authTask.result?.user?.let { viewModel.onSignInSuccess(it) }
                                                onLoginSuccess()
                                            } else {
                                                viewModel.onSignInFailure(authTask.exception?.message ?: "Firebase Auth Failed")
                                            }
                                        }
                                }
                                else -> {
                                    Log.e("LoginActivity", "Unexpected credential type: ${credential.type}")
                                    viewModel.onSignInFailure("Unexpected sign-in error")
                                }
                            }
                        } catch (e: GetCredentialException) {
                            Log.e("LoginActivity", "Credential Manager error", e)
                            viewModel.onSignInFailure("Sign-in failed: ${e.message}")
                        }
                    }
                }
            ) {
                Text(text = stringResource(id = com.example.driverassist.R.string.sign_in_google))
            }
        }

        viewModel.errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        
        // Skip for development if needed
        TextButton(
            onClick = { onLoginSuccess() },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(stringResource(id = com.example.driverassist.R.string.skip_login))
        }
    }
}
