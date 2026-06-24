package com.victor.postly.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.victor.postly.R
import com.victor.postly.adapter.MessageAdapter
import com.victor.postly.auth.UserAuth
import com.victor.postly.dao.ChatDao
import com.victor.postly.dao.UserDao
import com.victor.postly.databinding.ActivityChatBinding
import com.victor.postly.databinding.DialogImageSourceBinding
import com.victor.postly.model.ChatMessage
import com.victor.postly.model.User
import com.victor.postly.utils.Base64Converter
import com.victor.postly.utils.SoundEffectHelper
import java.io.File

class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_USER_ID = "extra_user_id"
    }

    private lateinit var binding: ActivityChatBinding
    private lateinit var messageAdapter: MessageAdapter

    private val auth = UserAuth()
    private val chatDao = ChatDao()
    private val userDao = UserDao()
    private val converter = Base64Converter()

    private var chatId: String = ""
    private var targetUserId: String = ""
    private var isSendingMessage = false
    private var isSendingMedia = false

    // ── Áudio ──────────────────────────────────────────────────────────────
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var recordingSeconds = 0
    private val recordingHandler = Handler(Looper.getMainLooper())
    private val recordingTicker = object : Runnable {
        override fun run() {
            if (isRecording) {
                recordingSeconds++
                val min = recordingSeconds / 60
                val sec = recordingSeconds % 60
                binding.txtRecordingTime.text = "Gravando… %d:%02d".format(min, sec)
                recordingHandler.postDelayed(this, 1000)
            }
        }
    }

    // ── Launchers ──────────────────────────────────────────────────────────
    private val publicProfileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { sendPhotoFromUri(it) }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { sendPhotoBitmap(it) }
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else
            Toast.makeText(this, "Permissão de microfone necessária", Toast.LENGTH_SHORT).show()
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null) else
            Toast.makeText(this, "Permissão de câmera necessária", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        targetUserId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()

        if (!auth.isLoggedIn() || targetUserId.isBlank()) {
            finish()
            return
        }

        setupInsets()
        setupMessages()
        setupListeners()
        loadTargetUser()
        openConversation()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecordingAndDiscard()
        if (::messageAdapter.isInitialized) {
            messageAdapter.releasePlayer()
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, topInset, view.paddingRight, view.paddingBottom)
            insets
        }

        val composerStartPadding = binding.messageComposer.paddingLeft
        val composerTopPadding = binding.messageComposer.paddingTop
        val composerEndPadding = binding.messageComposer.paddingRight
        val composerBottomPadding = binding.messageComposer.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.messageComposer) { view, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = navInsets.bottom.coerceAtLeast(imeInsets.bottom)
            view.setPadding(
                composerStartPadding + navInsets.left,
                composerTopPadding,
                composerEndPadding + navInsets.right,
                composerBottomPadding + bottomInset
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.messageComposer)
    }

    private fun setupMessages() {
        messageAdapter = MessageAdapter(auth.getCurrentUid().orEmpty())
        binding.recyclerMessages.apply {
            adapter = messageAdapter
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.layoutChatHeader.setOnClickListener { openPublicProfile() }
        binding.imgChatAvatar.setOnClickListener { openPublicProfile() }
        binding.btnSendMessage.setOnClickListener { sendMessage() }
        binding.edtMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        // Botão de foto: abre galeria ou câmera
        binding.btnSendPhoto.setOnClickListener { showPhotoSourceDialog() }

        // Botão de áudio: pressionar e segurar para gravar
        binding.btnRecordAudio.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    requestMicAndRecord()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecordingAndSend()
                    true
                }
                else -> false
            }
        }

        binding.btnCancelRecording.setOnClickListener {
            stopRecordingAndDiscard()
        }
    }

    // ── Foto ───────────────────────────────────────────────────────────────

    private fun showPhotoSourceDialog() {
        if (isSendingMedia || isRecording) return

        val sourceBinding = DialogImageSourceBinding.inflate(layoutInflater)
        val dialog = Dialog(this)
        dialog.setContentView(sourceBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        sourceBinding.cardTakePhoto.setOnClickListener {
            dialog.dismiss()
            openCamera()
        }

        sourceBinding.cardChooseGallery.setOnClickListener {
            dialog.dismiss()
            galleryLauncher.launch("image/*")
        }

        dialog.show()
        val width = (resources.displayMetrics.widthPixels * 0.88).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun sendPhotoBitmap(bitmap: Bitmap) {
        val base64 = converter.bitmapToString(bitmap)
        sendMedia(base64, "image/jpeg", ChatMessage.TYPE_IMAGE)
    }

    private fun sendPhotoFromUri(uri: Uri) {
        try {
            val stream = contentResolver.openInputStream(uri) ?: return
            val bytes = stream.readBytes()
            stream.close()
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return
            sendPhotoBitmap(bmp)
        } catch (_: Exception) {
            Toast.makeText(this, "Erro ao carregar imagem", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Áudio ──────────────────────────────────────────────────────────────

    private fun requestMicAndRecord() {
        if (isSendingMedia) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        if (isRecording) return

        val file = File.createTempFile("rec_", ".m4a", cacheDir)
        audioFile = file

        try {
            mediaRecorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            recordingSeconds = 0
            binding.layoutRecordingBar.visibility = View.VISIBLE
            binding.btnSendPhoto.isEnabled = false
            binding.btnSendMessage.isEnabled = false
            binding.btnSendPhoto.alpha = 0.45f
            binding.btnSendMessage.alpha = 0.45f
            recordingHandler.post(recordingTicker)

            // Animar dot vermelho
            blinkRecordingDot()

        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao iniciar gravação", Toast.LENGTH_SHORT).show()
            audioFile?.delete()
            audioFile = null
        }
    }

    private fun stopRecordingAndSend() {
        if (!isRecording) return
        finishRecording()

        val file = audioFile ?: return
        if (file.length() < 500) {   // arquivo muito pequeno = clique acidental
            file.delete()
            audioFile = null
            return
        }

        try {
            val base64 = Base64.encodeToString(file.readBytes(), Base64.DEFAULT)
            sendMedia(base64, "audio/mp4", ChatMessage.TYPE_AUDIO)
        } catch (_: Exception) {
            Toast.makeText(this, "Erro ao processar áudio", Toast.LENGTH_SHORT).show()
        } finally {
            file.delete()
            audioFile = null
        }
    }

    private fun stopRecordingAndDiscard() {
        finishRecording()
        audioFile?.delete()
        audioFile = null
    }

    private fun finishRecording() {
        if (!isRecording) return
        isRecording = false
        recordingHandler.removeCallbacks(recordingTicker)
        binding.layoutRecordingBar.visibility = View.GONE
        binding.btnSendPhoto.isEnabled = !isSendingMedia
        binding.btnSendMessage.isEnabled = !isSendingMessage
        binding.btnSendPhoto.alpha = if (isSendingMedia) 0.45f else 1f
        binding.btnSendMessage.alpha = if (isSendingMessage) 0.45f else 1f

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
    }

    private fun blinkRecordingDot() {
        val dot = binding.recordingDot
        dot.animate().alpha(0f).setDuration(500).withEndAction {
            dot.animate().alpha(1f).setDuration(500).withEndAction {
                if (isRecording) blinkRecordingDot()
            }.start()
        }.start()
    }

    // ── Envio de mídia ─────────────────────────────────────────────────────

    private fun sendMedia(base64: String, mimeType: String, type: String) {
        if (chatId.isBlank() || isSendingMedia) return
        val currentUid = auth.getCurrentUid().orEmpty()
        if (currentUid.isBlank()) return

        setMediaSending(true)
        chatDao.sendMediaMessage(
            chatId = chatId,
            senderId = currentUid,
            mediaBase64 = base64,
            mediaMimeType = mimeType,
            type = type,
            onSuccess = { message ->
                setMediaSending(false)
                SoundEffectHelper.playTap()
                addMessage(message)
                setResult(RESULT_OK)
            },
            onError = { msg ->
                setMediaSending(false)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun setMediaSending(sending: Boolean) {
        isSendingMedia = sending
        binding.btnSendPhoto.isEnabled = !sending
        binding.btnRecordAudio.isEnabled = !sending
        binding.btnSendPhoto.alpha = if (sending) 0.45f else 1f
        binding.btnRecordAudio.alpha = if (sending) 0.45f else 1f
    }

    // ── Chat base ──────────────────────────────────────────────────────────

    private fun openConversation() {
        val currentUid = auth.getCurrentUid().orEmpty()
        if (chatId.isNotBlank()) {
            loadMessages()
            return
        }

        showLoading(true)
        chatDao.createOrGetChat(
            currentUserId = currentUid,
            targetUserId = targetUserId,
            onSuccess = { id ->
                chatId = id
                loadMessages()
            },
            onError = { msg ->
                showLoading(false)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                finish()
            }
        )
    }

    private fun loadTargetUser() {
        userDao.getUser(targetUserId) { user ->
            if (isFinishing || isDestroyed) return@getUser
            if (user == null) {
                binding.txtChatName.text = getString(R.string.unknown_user)
                binding.txtChatUsername.text = ""
                return@getUser
            }
            bindUser(user)
        }
    }

    private fun bindUser(user: User) {
        binding.txtChatName.text = user.name.ifBlank { getString(R.string.unknown_user) }
        binding.txtChatUsername.text =
            if (user.username.isBlank()) "" else "@${user.username}"
        if (!user.photo.isNullOrEmpty()) {
            val bitmap = converter.stringToBitmap(user.photo)
            binding.imgChatAvatar.setImageBitmap(bitmap)
        } else {
            binding.imgChatAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
        }
    }

    private fun loadMessages() {
        showLoading(true)
        chatDao.getMessages(chatId) { messages ->
            showLoading(false)
            if (isFinishing || isDestroyed) return@getMessages
            messageAdapter.setMessages(messages)
            updateEmptyState(messages.size)
            scrollToLastMessage()
        }
    }

    private fun sendMessage() {
        if (isSendingMessage) return

        val text = binding.edtMessage.text.toString().trim()
        if (text.isBlank() || chatId.isBlank()) return

        val currentUid = auth.getCurrentUid().orEmpty()
        if (currentUid.isBlank()) return

        isSendingMessage = true
        binding.btnSendMessage.isEnabled = false

        chatDao.sendMessage(
            chatId = chatId,
            senderId = currentUid,
            text = text,
            onSuccess = { message ->
                isSendingMessage = false
                binding.btnSendMessage.isEnabled = true
                binding.edtMessage.setText("")
                SoundEffectHelper.playTap()
                addMessage(message)
                setResult(RESULT_OK)
            },
            onError = { msg ->
                isSendingMessage = false
                binding.btnSendMessage.isEnabled = true
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun addMessage(message: ChatMessage) {
        messageAdapter.addMessage(message)
        updateEmptyState(messageAdapter.itemCount)
        scrollToLastMessage()
    }

    private fun updateEmptyState(count: Int) {
        binding.txtNoMessages.visibility = if (count == 0) View.VISIBLE else View.GONE
        binding.recyclerMessages.visibility = if (count == 0) View.GONE else View.VISIBLE
    }

    private fun scrollToLastMessage() {
        if (messageAdapter.itemCount > 0) {
            binding.recyclerMessages.scrollToPosition(messageAdapter.itemCount - 1)
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressMessages.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun openPublicProfile() {
        if (targetUserId.isBlank()) return
        publicProfileLauncher.launch(
            Intent(this, PublicProfileActivity::class.java).putExtra(
                PublicProfileActivity.EXTRA_USER_ID,
                targetUserId
            )
        )
    }
}
