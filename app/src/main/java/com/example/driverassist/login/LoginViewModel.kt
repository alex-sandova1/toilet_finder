package com.example.driverassist.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.driverassist.data.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()

    var user by mutableStateOf(auth.currentUser)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun onSignInSuccess(firebaseUser: FirebaseUser) {
        user = firebaseUser
        errorMessage = null
        
        // Ensure user profile exists in Firestore
        viewModelScope.launch {
            val profile = userRepository.fetchUserProfile(firebaseUser.uid)
            if (profile == null) {
                userRepository.createUserProfile(
                    uid = firebaseUser.uid,
                    displayName = firebaseUser.displayName ?: "User",
                    email = firebaseUser.email ?: ""
                )
            }
        }
    }

    fun onSignInFailure(error: String) {
        errorMessage = error
        isLoading = false
    }
    
    fun setLoadingState(loading: Boolean) {
        isLoading = loading
    }
}
