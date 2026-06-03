package com.example.driverassist.login

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.driverassist.R
import android.widget.Button
import com.example.driverassist.MainPage
import android.content.Intent

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val LoginButton = findViewById<Button>(R.id.LoginButton)
        LoginButton.setOnClickListener {
            val intent = Intent(this, MainPage::class.java)
            startActivity(intent)
            finish()
        }
    }
}