package com.example.driverassist.login

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.driverassist.MainPage
import com.example.driverassist.ui.theme.DriverAssistTheme
import com.example.driverassist.util.printSigningFingerprint
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

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
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { idToken ->
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener { authTask ->
                        if (authTask.isSuccessful) {
                            authTask.result?.user?.let { viewModel.onSignInSuccess(it) }
                            onLoginSuccess()
                        } else {
                            viewModel.onSignInFailure(authTask.exception?.message ?: "Firebase Auth Failed")
                        }
                    }
            }
        } catch (e: ApiException) {
            viewModel.onSignInFailure("Google Sign-In Failed: ${e.message}")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Driver Assist", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))
        
        if (viewModel.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    viewModel.setLoadingState(true)
                    // Note: You must enable Google Sign-In in Firebase Console and use your Web Client ID here.
                    val webClientId = "769881213094-1j1ga27sro61hamdu92j2egn7bil4ke5.apps.googleusercontent.com"
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(webClientId)
                        .requestEmail()
                        .build()
                    val googleSignInClient = GoogleSignIn.getClient(context, gso)
                    launcher.launch(googleSignInClient.signInIntent)
                }
            ) {
                Text(text = "Sign in with Google")
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
            Text("Skip Log-In")
        }
    }
}
