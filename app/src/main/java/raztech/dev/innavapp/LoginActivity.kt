package raztech.dev.innavapp

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private val adminUsername = "admin"
    private val adminPassword = "admin123"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val username = findViewById<EditText>(R.id.etUsername)
        val password = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGuest = findViewById<Button>(R.id.btnGuest)

        btnLogin.setOnClickListener {
            val user = username.text.toString()
            val pass = password.text.toString()

            if (user == adminUsername && pass == adminPassword) {
                startActivity(Intent(this, AdminActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Login gagal. Coba lagi.", Toast.LENGTH_SHORT).show()
            }
        }

        btnGuest.setOnClickListener {
            startActivity(Intent(this, UserActivity::class.java))
            finish()
        }
    }
}
