package com.victor.postly.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.victor.postly.R
import com.victor.postly.adapter.MessageAdapter
import com.victor.postly.auth.UserAuth
import com.victor.postly.dao.ChatDao
import com.victor.postly.dao.UserDao
import com.victor.postly.databinding.ActivityChatBinding
import com.victor.postly.model.ChatMessage
import com.victor.postly.model.User
import com.victor.postly.utils.Base64Converter

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

    private val publicProfileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

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

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, topInset, view.paddingRight, view.paddingBottom)
            insets
        }
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
    }

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
        binding.txtChatUsername.text = if (user.username.isBlank()) "" else "@${user.username}"
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
