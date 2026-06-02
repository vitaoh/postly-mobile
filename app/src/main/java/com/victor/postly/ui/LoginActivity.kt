package com.victor.postly.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseUser
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.victor.postly.R
import com.victor.postly.auth.UserAuth
import com.victor.postly.dao.UserDao
import com.victor.postly.databinding.ActivityLoginBinding
import com.victor.postly.model.User
import com.victor.postly.security.AppUnlockHelper
import com.victor.postly.security.AppUnlockManager
import com.victor.postly.utils.Base64Converter
import java.util.Locale
import kotlin.concurrent.thread

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = UserAuth()
    private val userDao = UserDao()
    private val base64Converter = Base64Converter()

    private val googleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(this, options)
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleGoogleSignInResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupListeners()

        if (auth.isLoggedIn()) {
            requireUnlockAndGoHome()
        }
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            if (validateFields()) loginUser()
        }

        binding.btnCreateUser.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        binding.btnGoogleLogin.setOnClickListener {
            startGoogleLogin()
        }

        binding.txtForgotPassword.setOnClickListener {
            sendPasswordReset()
        }
    }

    private fun validateFields(): Boolean {
        val input = binding.edtEmail.text.toString().trim()
        val senha = binding.edtSenha.text.toString()

        binding.tilEmail.error = null
        binding.tilSenha.error = null

        if (input.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_field_email_empty)
            return false
        }
        if (senha.isEmpty()) {
            binding.tilSenha.error = getString(R.string.error_field_password_empty)
            return false
        }
        return true
    }

    private fun loginUser() {
        val input = binding.edtEmail.text.toString().trim()
        val senha = binding.edtSenha.text.toString()

        setLoading(true)

        // Se contiver "@" trata como e-mail, senão busca o e-mail pelo username
        if (input.contains("@")) {
            doLogin(email = input, senha = senha)
        } else {
            resolveUsernameAndLogin(username = input, senha = senha)
        }
    }

    /**
     * Busca o e-mail associado ao username no Firestore
     * e usa esse e-mail para autenticar no Firebase Auth.
     */
    private fun resolveUsernameAndLogin(username: String, senha: String) {
        userDao.getUserByUsername(username) { user ->
            if (user == null) {
                setLoading(false)
                binding.tilEmail.error = getString(R.string.error_username_not_found)
                return@getUserByUsername
            }
            doLogin(email = user.email, senha = senha)
        }
    }

    private fun doLogin(email: String, senha: String) {
        auth.login(email, senha) { sucesso, erro ->
            setLoading(false)
            if (sucesso) {
                AppUnlockManager.markUnlocked()
                goToHome()
            } else {
                Toast.makeText(
                    this,
                    erro ?: getString(R.string.error_login),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startGoogleLogin() {
        setLoading(true)
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun handleGoogleSignInResult(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account = try {
            task.getResult(ApiException::class.java)
        } catch (e: ApiException) {
            setLoading(false)
            Toast.makeText(
                this,
                getString(R.string.google_login_failed),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val idToken = account.idToken
        if (idToken.isNullOrBlank()) {
            setLoading(false)
            Toast.makeText(this, getString(R.string.google_login_failed), Toast.LENGTH_LONG).show()
            return
        }

        auth.loginWithGoogle(idToken) { success, error ->
            if (success) {
                saveGoogleUserIfNeeded()
            } else {
                setLoading(false)
                Toast.makeText(
                    this,
                    error ?: getString(R.string.google_login_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveGoogleUserIfNeeded() {
        val firebaseUser = auth.getCurrentUser()
        if (firebaseUser == null) {
            setLoading(false)
            Toast.makeText(this, getString(R.string.google_login_failed), Toast.LENGTH_LONG).show()
            return
        }

        userDao.getUser(firebaseUser.uid) { existingUser ->
            if (existingUser != null) {
                updateGooglePhotoIfMissing(existingUser, firebaseUser)
                return@getUser
            }

            showGoogleUsernameDialog(firebaseUser)
        }
    }

    private fun showGoogleUsernameDialog(firebaseUser: FirebaseUser) {
        setLoading(false)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_google_username, null)
        val tilUsername = dialogView.findViewById<TextInputLayout>(R.id.tilGoogleUsername)
        val edtUsername = dialogView.findViewById<TextInputEditText>(R.id.edtGoogleUsername)

        edtUsername.setText(suggestGoogleUsername(firebaseUser))
        edtUsername.setSelection(edtUsername.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.google_username_dialog_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.continue_action), null)
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> cancelGoogleLogin() }
            .create()

        dialog.setOnCancelListener { cancelGoogleLogin() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val username = edtUsername.text.toString().trim().lowercase(Locale.getDefault())
                val usernameError = validateGoogleUsername(username)

                tilUsername.error = usernameError
                if (usernameError != null) return@setOnClickListener

                saveNewGoogleUser(firebaseUser, username, tilUsername, dialog)
            }
        }
        dialog.show()
    }

    private fun saveNewGoogleUser(
        firebaseUser: FirebaseUser,
        username: String,
        tilUsername: TextInputLayout,
        dialog: AlertDialog
    ) {
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.isEnabled = false
        setLoading(true)

        loadGooglePhotoBase64(firebaseUser) { photo ->
            userDao.saveUniqueUsername(
                user = buildGoogleUser(firebaseUser, username, photo),
                onSuccess = {
                    dialog.dismiss()
                    finishGoogleLogin()
                },
                onError = { msg ->
                    setLoading(false)
                    positiveButton.isEnabled = true
                    tilUsername.error = msg
                }
            )
        }
    }

    private fun buildGoogleUser(firebaseUser: FirebaseUser, username: String, photo: String?): User {
        val email = firebaseUser.email.orEmpty()
        val fallbackName = email.substringBefore("@").ifBlank { getString(R.string.unknown_user) }
        val name = firebaseUser.displayName?.takeIf { it.isNotBlank() } ?: fallbackName

        return User(
            uid = firebaseUser.uid,
            name = name,
            username = username,
            email = email,
            photo = photo
        )
    }

    private fun suggestGoogleUsername(firebaseUser: FirebaseUser): String {
        val email = firebaseUser.email.orEmpty()
        val name = firebaseUser.displayName.orEmpty()
        val source = email.substringBefore("@").ifBlank { name }
        return source
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9._]"), "")
            .ifBlank { "user" }
            .take(18)
    }

    private fun validateGoogleUsername(username: String): String? {
        return when {
            username.isBlank() -> getString(R.string.error_field_username_empty)
            username.length < 3 -> getString(R.string.error_field_username_short)
            !username.matches(Regex("^[a-z0-9._]+$")) -> getString(R.string.error_field_username_invalid)
            else -> null
        }
    }

    private fun updateGooglePhotoIfMissing(existingUser: User, firebaseUser: FirebaseUser) {
        if (!existingUser.photo.isNullOrBlank() || firebaseUser.photoUrl == null) {
            finishGoogleLogin()
            return
        }

        loadGooglePhotoBase64(firebaseUser) { photo ->
            if (photo.isNullOrBlank()) {
                finishGoogleLogin()
                return@loadGooglePhotoBase64
            }

            userDao.save(
                user = existingUser.copy(photo = photo),
                onSuccess = { finishGoogleLogin() },
                onError = { finishGoogleLogin() }
            )
        }
    }

    private fun loadGooglePhotoBase64(firebaseUser: FirebaseUser, onResult: (String?) -> Unit) {
        val photoUrl = firebaseUser.photoUrl
        if (photoUrl == null) {
            onResult(null)
            return
        }

        thread {
            val encodedPhoto = try {
                val bitmap = Glide.with(applicationContext)
                    .asBitmap()
                    .load(photoUrl)
                    .submit(256, 256)
                    .get()
                base64Converter.bitmapToString(bitmap)
            } catch (e: Exception) {
                null
            }

            runOnUiThread { onResult(encodedPhoto) }
        }
    }

    private fun cancelGoogleLogin() {
        auth.logout()
        googleSignInClient.signOut()
        setLoading(false)
    }

    private fun finishGoogleLogin() {
        setLoading(false)
        AppUnlockManager.markUnlocked()
        goToHome()
    }

    private fun sendPasswordReset() {
        val input = binding.edtEmail.text.toString().trim()

        if (input.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_field_email_reset)
            return
        }

        // Se for username, resolve o e-mail antes de enviar o reset
        if (!input.contains("@")) {
            userDao.getUserByUsername(input) { user ->
                if (user == null) {
                    binding.tilEmail.error = getString(R.string.error_username_not_found)
                    return@getUserByUsername
                }
                sendReset(user.email)
            }
        } else {
            sendReset(input)
        }
    }

    private fun sendReset(email: String) {
        auth.sendPasswordReset(email) { sucesso ->
            val msg = if (sucesso)
                getString(R.string.reset_email_sent)
            else
                getString(R.string.reset_email_error)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun goToHome() {
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    private fun requireUnlockAndGoHome() {
        AppUnlockHelper.requireUnlock(
            activity = this,
            onUnlocked = { goToHome() },
            onCanceled = { finish() }
        )
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.btnCreateUser.isEnabled = !isLoading
        binding.btnGoogleLogin.isEnabled = !isLoading
        binding.btnLogin.text = if (isLoading)
            getString(R.string.logging_in)
        else
            getString(R.string.login)
    }
}
