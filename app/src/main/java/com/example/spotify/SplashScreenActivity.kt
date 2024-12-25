package com.example.spotify

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SplashScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        // Menunggu beberapa detik sebelum memeriksa status login
        Handler(Looper.getMainLooper()).postDelayed({
            // Memeriksa apakah pengguna sudah login
            val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
            val isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false)

            if (isLoggedIn) {
                // Jika sudah login, arahkan ke Dashboard
                val userId = sharedPreferences.getInt("userId", -1)
                val intent = Intent(this, DashboardActivity::class.java)
                startActivity(intent)
            } else {
                // Jika belum login, arahkan ke LoginActivity
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
            }

            // Menutup Splash Screen setelah pengalihan
            finish()

        }, 3000)  // Tunda selama 3 detik
    }
}