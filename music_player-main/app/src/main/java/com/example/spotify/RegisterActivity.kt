package com.example.spotify
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.spotify.database.DatabaseHelper

class RegisterActivity : AppCompatActivity() {

    private lateinit var usernameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var registerButton: Button
    private lateinit var loginText: TextView
    private lateinit var databaseHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.register_activity) // Pastikan menggunakan layout register yang sudah diberikan

        // Inisialisasi komponen UI
        usernameInput = findViewById(R.id.username_input)
        emailInput = findViewById(R.id.email_input)
        passwordInput = findViewById(R.id.password_input)
        registerButton = findViewById(R.id.register_button)
        loginText = findViewById(R.id.login_text)

        // Inisialisasi DatabaseHelper
        databaseHelper = DatabaseHelper(this)

        // Fungsi untuk registrasi pengguna
        registerButton.setOnClickListener {
            val username = usernameInput.text.toString()
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()

            // Validasi form input
            if (TextUtils.isEmpty(username) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Semua kolom harus diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validasi email format
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Format email tidak valid", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validasi panjang password
            if (password.length < 8) {
                Toast.makeText(this, "Password harus minimal 8 karakter", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Cek apakah email sudah terdaftar
            if (isEmailExist(email)) {
                Toast.makeText(this, "Email sudah terdaftar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Menambahkan pengguna baru
            val isAdded = databaseHelper.addUser(email, password)
            if (isAdded) {
                Toast.makeText(this, "Pendaftaran berhasil", Toast.LENGTH_SHORT).show()
                finish() // Menutup RegisterActivity setelah berhasil
            } else {
                Toast.makeText(this, "Pendaftaran gagal", Toast.LENGTH_SHORT).show()
            }
        }

        // Arahkan ke halaman login jika sudah punya akun
        loginText.setOnClickListener {
            finish() // Tutup RegisterActivity dan kembali ke LoginActivity
        }
        loginText.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    // Fungsi untuk mengecek apakah email sudah ada di database
    private fun isEmailExist(email: String): Boolean {
        return databaseHelper.isEmailExist(email)
    }
}