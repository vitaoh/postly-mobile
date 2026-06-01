package com.victor.postly.ui

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.firebase.firestore.DocumentSnapshot
import com.victor.postly.R
import com.victor.postly.adapter.ConversationAdapter
import com.victor.postly.adapter.PostAdapter
import com.victor.postly.auth.UserAuth
import com.victor.postly.dao.ChatDao
import com.victor.postly.dao.PostDao
import com.victor.postly.dao.UserDao
import com.victor.postly.databinding.ActivityHomeBinding
import com.victor.postly.model.ChatThread
import com.victor.postly.model.Post
import com.victor.postly.notifications.LocalNotificationWatcher
import com.victor.postly.notifications.NotificationHelper
import com.victor.postly.utils.Base64Converter

class HomeActivity : AppCompatActivity() {

    companion object {
        private const val TAB_FEED = "feed"
        private const val TAB_CHATS = "chats"
        private const val FEED_FOR_YOU = "for_you"
        private const val FEED_FOLLOWING = "following"
    }

    private lateinit var binding: ActivityHomeBinding
    private lateinit var adapter: PostAdapter
    private lateinit var conversationAdapter: ConversationAdapter

    private val auth = UserAuth()
    private val postDao = PostDao()
    private val chatDao = ChatDao()
    private val userDao = UserDao()
    private val converter = Base64Converter()

    private var lastDoc: DocumentSnapshot? = null
    private var isLoading = false
    private var hasMorePages = true
    private var currentSearchQuery: String = ""
    private var currentTab = TAB_FEED
    private var currentFeedMode = FEED_FOR_YOU
    private var localNotificationWatcher: LocalNotificationWatcher? = null

    private val publicProfileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadAvatar()
            adapter.clearUserCache()
            loadFirstPage()
            if (currentTab == TAB_CHATS) loadConversations()
        }
    }

    private val postLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadFirstPage()
        }
    }

    private val chatLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && currentTab == TAB_CHATS) {
            loadConversations()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, topInset, view.paddingRight, view.paddingBottom)
            insets
        }

        if (!auth.isLoggedIn()) {
            goToLogin()
            return
        }

        setupLocalNotifications()
        setupAdapter()
        setupRecycler()
        setupConversations()
        setupSearch()
        setupFeedToggle()
        applySearchBarColor()
        setupListeners()
        setupBackNavigation()
        loadFirstPage()
    }

    override fun onResume() {
        super.onResume()
        loadAvatar()
        if (currentTab == TAB_CHATS) loadConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        localNotificationWatcher?.stop()
        localNotificationWatcher = null
    }

    private fun setupAdapter() {
        val currentUid = auth.getCurrentUid() ?: ""
        adapter = PostAdapter(
            currentUid = currentUid,
            onEdit = { post -> openEditDialog(post) },
            onDelete = { post -> confirmDelete(post) },
            onComment = { post -> openCommentsDialog(post) },
            onAuthorClick = { userId -> openPublicProfile(userId) },
            onPostClick = { post -> openPost(post) },
            onLike = { post -> toggleLike(post) }
        )
    }

    private fun setupRecycler() {
        val layoutManager = LinearLayoutManager(this)
        binding.recyclerFeed.apply {
            adapter = this@HomeActivity.adapter
            this.layoutManager = layoutManager
        }

        binding.recyclerFeed.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || currentSearchQuery.isNotBlank() || currentFeedMode != FEED_FOR_YOU) return

                val totalItems = layoutManager.itemCount
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (!isLoading && hasMorePages && lastVisible >= totalItems - 3) {
                    loadNextPage()
                }
            }
        })
    }

    private fun setupConversations() {
        conversationAdapter = ConversationAdapter(
            currentUid = auth.getCurrentUid().orEmpty(),
            onClick = { conversation -> openChat(conversation) }
        )

        binding.recyclerConversations.apply {
            adapter = conversationAdapter
            layoutManager = LinearLayoutManager(this@HomeActivity)
        }
    }

    private fun setupLocalNotifications() {
        NotificationHelper.createChannels(this)
        startLocalNotificationWatcher()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startLocalNotificationWatcher() {
        val uid = auth.getCurrentUid().orEmpty()
        if (uid.isBlank()) return

        if (localNotificationWatcher == null) {
            localNotificationWatcher = LocalNotificationWatcher(this, uid).also { it.start() }
        }
    }

    private fun setupSearch() {
        binding.edtSearch.addTextChangedListener { editable ->
            val query = editable?.toString() ?: ""
            currentSearchQuery = query
            adapter.filterPosts(query)
            if (currentTab == TAB_FEED) updateFeedEmptyState()
        }
    }

    private fun setupFeedToggle() {
        binding.feedToggleGroup.check(binding.btnForYou.id)
        updateFeedToggleColors()

        binding.feedToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val nextMode = when (checkedId) {
                binding.btnFollowingFeed.id -> FEED_FOLLOWING
                else -> FEED_FOR_YOU
            }

            if (nextMode == currentFeedMode) {
                updateFeedToggleColors()
                return@addOnButtonCheckedListener
            }

            currentFeedMode = nextMode
            updateFeedToggleColors()
            binding.edtSearch.setText("")
            binding.recyclerFeed.scrollToPosition(0)
            loadFirstPage()
        }
    }

    private fun applySearchBarColor() {
        val surface = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSurface
        )
        binding.tilSearch.boxBackgroundColor = surface
    }

    private fun setupListeners() {
        binding.imgAvatar.setOnClickListener {
            openPublicProfile(auth.getCurrentUid() ?: "")
        }
        binding.btnBackConversations.setOnClickListener {
            showFeedTab()
        }
        binding.imgLogo.setOnClickListener {
            binding.edtSearch.setText("")
            if (currentTab == TAB_FEED) {
                binding.recyclerFeed.smoothScrollToPosition(0)
            } else {
                binding.recyclerConversations.smoothScrollToPosition(0)
                loadConversations()
            }
        }
        binding.fabNewPost.setOnClickListener {
            val dialog = NewPostDialog().apply { onPostSaved = { loadFirstPage() } }
            dialog.show(supportFragmentManager, "new_post")
        }
        binding.btnConversations.setOnClickListener {
            if (currentTab == TAB_CHATS) {
                binding.recyclerConversations.smoothScrollToPosition(0)
                loadConversations()
            } else {
                showChatsTab()
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTab == TAB_CHATS) {
                    showFeedTab()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun loadFirstPage() {
        if (currentFeedMode == FEED_FOLLOWING) {
            loadFollowingFeed()
            return
        }

        setLoading(true)
        hasMorePages = true
        lastDoc = null

        postDao.getFirstPage { posts, cursor ->
            setLoading(false)
            lastDoc = cursor
            hasMorePages = posts.size >= PostDao.PAGE_SIZE.toInt()
            adapter.updatePosts(posts)
            if (currentSearchQuery.isNotBlank()) adapter.filterPosts(currentSearchQuery)
            if (currentTab == TAB_FEED) updateFeedEmptyState()
        }
    }

    private fun loadNextPage() {
        if (currentFeedMode != FEED_FOR_YOU) return

        val cursor = lastDoc ?: return
        setLoading(true)

        postDao.getNextPage(cursor) { posts, newCursor ->
            setLoading(false)
            if (posts.isNotEmpty()) {
                lastDoc = newCursor
                hasMorePages = posts.size >= PostDao.PAGE_SIZE.toInt()
                adapter.appendPosts(posts)
            } else {
                hasMorePages = false
            }
        }
    }

    private fun loadFollowingFeed() {
        val uid = auth.getCurrentUid().orEmpty()
        if (uid.isBlank()) return

        setLoading(true)
        hasMorePages = false
        lastDoc = null

        userDao.getFollowingIds(uid) { followingIds ->
            if (isFinishing || isDestroyed || currentFeedMode != FEED_FOLLOWING) return@getFollowingIds

            if (followingIds.isEmpty()) {
                setLoading(false)
                adapter.updatePosts(emptyList())
                if (currentTab == TAB_FEED) updateFeedEmptyState()
                return@getFollowingIds
            }

            postDao.getPostsByUsers(followingIds) { posts ->
                if (isFinishing || isDestroyed || currentFeedMode != FEED_FOLLOWING) return@getPostsByUsers

                setLoading(false)
                adapter.updatePosts(posts)
                if (currentSearchQuery.isNotBlank()) adapter.filterPosts(currentSearchQuery)
                if (currentTab == TAB_FEED) updateFeedEmptyState()
            }
        }
    }

    private fun loadConversations() {
        val uid = auth.getCurrentUid().orEmpty()
        chatDao.getConversations(uid) { conversations ->
            if (isFinishing || isDestroyed) return@getConversations
            conversationAdapter.updateConversations(conversations)
            if (currentTab == TAB_CHATS) updateConversationEmptyState(conversations.isEmpty())
        }
    }

    private fun loadAvatar() {
        val uid = auth.getCurrentUid() ?: return
        userDao.getUser(uid) { user ->
            if (!user?.photo.isNullOrEmpty()) {
                val bitmap = converter.stringToBitmap(user!!.photo!!)
                binding.imgAvatar.setImageBitmap(bitmap)
            }
        }
    }

    private fun openCommentsDialog(post: Post) {
        val dialog = CommentsDialog().apply {
            postId = post.id
            onCommentCountChanged = { delta ->
                adapter.updateCommentCount(post.id, delta)
            }
            onAuthorClick = { userId -> openPublicProfile(userId) }
        }
        dialog.show(supportFragmentManager, "comments_${post.id}")
    }

    private fun openPublicProfile(userId: String) {
        if (userId.isBlank()) return
        publicProfileLauncher.launch(
            Intent(this, PublicProfileActivity::class.java).putExtra(
                PublicProfileActivity.EXTRA_USER_ID,
                userId
            )
        )
    }

    private fun openPost(post: Post) {
        if (post.id.isBlank()) return
        postLauncher.launch(
            Intent(this, PostActivity::class.java).putExtra(
                PostActivity.EXTRA_POST_ID,
                post.id
            )
        )
    }

    private fun openChat(conversation: ChatThread) {
        val currentUid = auth.getCurrentUid().orEmpty()
        val otherUserId = conversation.participants.firstOrNull { it != currentUid }.orEmpty()
        if (conversation.id.isBlank() || otherUserId.isBlank()) return

        chatLauncher.launch(
            Intent(this, ChatActivity::class.java)
                .putExtra(ChatActivity.EXTRA_CHAT_ID, conversation.id)
                .putExtra(ChatActivity.EXTRA_USER_ID, otherUserId)
        )
    }

    private fun openEditDialog(post: Post) {
        val dialog = NewPostDialog().apply {
            editPost = post
            onPostSaved = { loadFirstPage() }
        }
        dialog.show(supportFragmentManager, "edit_post")
    }

    private fun toggleLike(post: Post) {
        val uid = auth.getCurrentUid() ?: return
        postDao.toggleLike(
            post = post,
            userId = uid,
            onSuccess = { updatedPost ->
                adapter.updatePost(updatedPost)
            },
            onError = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun confirmDelete(post: Post) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_post))
            .setMessage(getString(R.string.delete_post_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> deletePost(post) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deletePost(post: Post) {
        postDao.deletePost(
            postId = post.id,
            onSuccess = {
                adapter.removePost(post)
                if (currentTab == TAB_FEED) updateFeedEmptyState()
            },
            onError = { msg ->
                Toast.makeText(this, getString(R.string.error_delete_post, msg), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showFeedTab() {
        currentTab = TAB_FEED
        binding.btnBackConversations.visibility = View.GONE
        binding.imgAvatar.visibility = View.VISIBLE
        binding.btnConversations.visibility = View.VISIBLE
        binding.layoutSearch.visibility = View.VISIBLE
        binding.feedToggleGroup.visibility = View.VISIBLE
        binding.recyclerFeed.visibility = View.VISIBLE
        binding.recyclerConversations.visibility = View.GONE
        binding.fabNewPost.visibility = View.VISIBLE
        updateFeedToggleColors()
        updateFeedEmptyState()
    }

    private fun showChatsTab() {
        currentTab = TAB_CHATS
        binding.btnBackConversations.visibility = View.VISIBLE
        binding.imgAvatar.visibility = View.GONE
        binding.btnConversations.visibility = View.GONE
        binding.layoutSearch.visibility = View.GONE
        binding.feedToggleGroup.visibility = View.GONE
        binding.recyclerFeed.visibility = View.GONE
        binding.recyclerConversations.visibility = View.VISIBLE
        binding.fabNewPost.visibility = View.GONE
        updateConversationEmptyState(conversationAdapter.itemCount == 0)
        loadConversations()
    }

    private fun updateFeedToggleColors() {
        val primary = ContextCompat.getColor(this, R.color.primary)
        val onPrimary = ContextCompat.getColor(this, R.color.secundary)
        val surface = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSurface
        )

        val selectedButton = when (currentFeedMode) {
            FEED_FOLLOWING -> binding.btnFollowingFeed
            else -> binding.btnForYou
        }

        listOf(binding.btnForYou, binding.btnFollowingFeed).forEach { button ->
            val isSelected = button == selectedButton
            button.backgroundTintList = ColorStateList.valueOf(if (isSelected) surface else primary)
            button.strokeColor = ColorStateList.valueOf(if (isSelected) primary else onPrimary)
            button.setTextColor(if (isSelected) primary else onPrimary)
        }
    }

    private fun updateFeedEmptyState() {
        binding.txtEmptyTitle.text = when {
            currentSearchQuery.isNotBlank() -> getString(R.string.no_results_title)
            currentFeedMode == FEED_FOLLOWING -> getString(R.string.no_following_posts_title)
            else -> getString(R.string.no_posts_title)
        }
        binding.txtEmptySubtitle.text = when {
            currentSearchQuery.isNotBlank() -> getString(R.string.no_results_subtitle)
            currentFeedMode == FEED_FOLLOWING -> getString(R.string.no_following_posts_subtitle)
            else -> getString(R.string.no_posts_subtitle)
        }
        binding.layoutEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun updateConversationEmptyState(isEmpty: Boolean) {
        binding.txtEmptyTitle.text = getString(R.string.no_conversations_title)
        binding.txtEmptySubtitle.text = getString(R.string.no_conversations_subtitle)
        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.progressPagination.visibility = if (loading && currentTab == TAB_FEED) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
}
