package com.victor.postly.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.victor.postly.R
import com.victor.postly.auth.UserAuth
import com.victor.postly.dao.UserDao
import com.victor.postly.databinding.ActivitySignupBinding
import com.victor.postly.model.User
import com.victor.postly.security.AppUnlockManager

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private val auth = UserAuth()
    private val userDao = UserDao()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnRegister.setOnClickListener {
            if (validateFields()) registerUser()
        }

        binding.btnBackLogin.setOnClickListener {
            finish()
        }
    }

    private fun validateFields(): Boolean {
        val username = binding.edtUsername.text.toString().trim()
        val name = binding.edtName.text.toString().trim()
        val email = binding.edtEmail.text.toString().trim()
        val password = binding.edtPassword.text.toString()
        val confirmPassword = binding.edtConfirmPassword.text.toString()

        binding.tilUsername.error = null
        binding.tilName.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null

        if (username.isEmpty()) {
            binding.tilUsername.error = "Informe um nome de usuário"
            return false
        }
        if (username.length < 3) {
            binding.tilUsername.error = "Mínimo de 3 caracteres"
            return false
        }
        if (name.isEmpty()) {
            binding.tilName.error = "Informe seu nome"
            return false
        }
        if (email.isEmpty()) {
            binding.tilEmail.error = "Informe seu e-mail"
            return false
        }
        if (password.isEmpty()) {
            binding.tilPassword.error = "Informe uma senha"
            return false
        }
        if (password.length < 6) {
            binding.tilPassword.error = "Mínimo de 6 caracteres"
            return false
        }
        if (confirmPassword != password) {
            binding.tilConfirmPassword.error = "As senhas não coincidem"
            return false
        }
        return true
    }

    private fun registerUser() {
        val email = binding.edtEmail.text.toString().trim()
        val password = binding.edtPassword.text.toString()

        setLoading(true)

        auth.register(email, password) { sucesso, erro ->
            if (sucesso) {
                saveUserToFirestore(auth.getCurrentUid()!!)
            } else {
                setLoading(false)
                Toast.makeText(this, erro ?: "Erro ao cadastrar", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveUserToFirestore(uid: String) {
        val user = User(
            uid = uid,
            name = binding.edtName.text.toString().trim(),
            username = binding.edtUsername.text.toString().trim().lowercase(),
            email = binding.edtEmail.text.toString().trim()
        )

        userDao.save(
            user = user,
            onSuccess = {
                setLoading(false)
                Toast.makeText(this, "Conta criada com sucesso!", Toast.LENGTH_SHORT).show()
                goToHome()
            },
            onError = { msg ->
                setLoading(false)
                Toast.makeText(this, "Erro ao salvar dados: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun goToHome() {
        AppUnlockManager.markUnlocked()
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnRegister.isEnabled = !isLoading
        binding.btnBackLogin.isEnabled = !isLoading
        binding.btnRegister.text =
            if (isLoading) "Aguarde..." else getString(R.string.create_account)
    }
}
