package com.victor.postly.ui

import android.content.res.ColorStateList
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.victor.postly.R
import com.victor.postly.adapter.PostAdapter
import com.victor.postly.auth.UserAuth
import com.victor.postly.dao.PostDao
import com.victor.postly.dao.UserDao
import com.victor.postly.databinding.LayoutPublicProfileBinding
import com.victor.postly.model.Post
import com.victor.postly.model.User
import com.victor.postly.utils.Base64Converter
import com.victor.postly.utils.ConfirmDialogHelper
import com.victor.postly.utils.SoundEffectHelper

class PublicProfileActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
    }

    private lateinit var binding: LayoutPublicProfileBinding
    private lateinit var adapter: PostAdapter

    private val auth = UserAuth()
    private val userDao = UserDao()
    private val postDao = PostDao()
    private val converter = Base64Converter()

    private var profileUserId: String = ""
    private var isFollowingProfile = false
    private var followersCount = 0

    private val publicProfileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
            loadFollowInfo()
        }
    }

    private val postLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
            loadPosts()
        }
    }

    private val profileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
            adapter.clearUserCache()
            loadUser()
            loadFollowInfo()
            loadPosts()
        }
    }

    private val chatLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = LayoutPublicProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profileUserId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        if (profileUserId.isBlank()) {
            Toast.makeText(this, getString(R.string.unknown_user), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupInsets()
        setupRecycler()
        setupListeners()
        loadUser()
        loadFollowInfo()
        loadPosts()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, topInset, view.paddingRight, view.paddingBottom)
            insets
        }
    }

    private fun setupRecycler() {
        val isOwnProfile = profileUserId == auth.getCurrentUid()
        adapter = PostAdapter(
            currentUid = auth.getCurrentUid() ?: "",
            onEdit = { post -> openEditDialog(post) },
            onDelete = { post -> confirmDeletePost(post) },
            onComment = { post -> openCommentsDialog(post) },
            onAuthorClick = { userId ->
                if (userId != profileUserId) openPublicProfile(userId)
            },
            onPostClick = { post -> openPost(post) },
            onLike = { post -> toggleLike(post) },
            showOwnerActions = isOwnProfile
        )

        binding.recyclerUserPosts.apply {
            adapter = this@PublicProfileActivity.adapter
            layoutManager = LinearLayoutManager(this@PublicProfileActivity)
            isNestedScrollingEnabled = false
        }
    }

    private fun setupListeners() {
        val isOwnProfile = profileUserId == auth.getCurrentUid()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSettings.visibility = if (isOwnProfile) View.VISIBLE else View.GONE
        binding.btnSettings.setOnClickListener {
            profileLauncher.launch(Intent(this, ProfileActivity::class.java))
        }
        binding.imgLogo.setOnClickListener {
            binding.scrollView.smoothScrollTo(0, 0)
            adapter.clearUserCache()
            loadUser()
            loadFollowInfo()
            loadPosts()
        }
        binding.profileActions.visibility = if (isOwnProfile) View.GONE else View.VISIBLE
        binding.btnFollow.visibility = if (isOwnProfile) View.GONE else View.VISIBLE
        binding.btnMessage.visibility = if (isOwnProfile) View.GONE else View.VISIBLE
        binding.btnFollow.isEnabled = false
        binding.btnFollow.setOnClickListener { toggleFollow() }
        binding.btnMessage.setOnClickListener { openChat() }
        binding.txtBio.visibility = View.GONE
    }

    private fun loadUser() {
        userDao.getUser(profileUserId) { user ->
            if (isFinishing || isDestroyed) return@getUser
            if (user == null) {
                Toast.makeText(this, getString(R.string.unknown_user), Toast.LENGTH_SHORT).show()
                finish()
                return@getUser
            }
            bindUser(user)
        }
    }

    private fun loadFollowInfo() {
        userDao.getFollowersCount(profileUserId) { count ->
            if (isFinishing || isDestroyed) return@getFollowersCount
            followersCount = count
            binding.txtFollowersCount.text = count.toString()
        }

        userDao.getFollowingCount(profileUserId) { count ->
            if (isFinishing || isDestroyed) return@getFollowingCount
            binding.txtFollowingCount.text = count.toString()
        }

        val currentUid = auth.getCurrentUid().orEmpty()
        if (currentUid.isBlank() || currentUid == profileUserId) return

        userDao.isFollowing(currentUid, profileUserId) { following ->
            if (isFinishing || isDestroyed) return@isFollowing
            isFollowingProfile = following
            updateFollowButton()
        }
    }

    private fun bindUser(user: User) {
        binding.txtName.text = user.name.ifBlank { getString(R.string.unknown_user) }
        binding.txtUsername.text = if (user.username.isBlank()) "" else "@${user.username}"

        if (!user.photo.isNullOrEmpty()) {
            val bitmap = converter.stringToBitmap(user.photo)
            binding.imgAvatar.setImageBitmap(bitmap)
        } else {
            binding.imgAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
        }
    }

    private fun loadPosts() {
        postDao.getPostsByUser(profileUserId) { posts ->
            if (isFinishing || isDestroyed) return@getPostsByUser
            adapter.updatePosts(posts)
            binding.txtPostCount.text = posts.size.toString()
            binding.txtSectionPosts.text = if (posts.isEmpty()) {
                getString(R.string.no_posts_title)
            } else {
                getString(R.string.posts)
            }
        }
    }

    private fun openEditDialog(post: Post) {
        val dialog = NewPostDialog().apply {
            editPost = post
            onPostSaved = {
                setResult(RESULT_OK)
                loadPosts()
            }
        }
        dialog.show(supportFragmentManager, "edit_post_${post.id}")
    }

    private fun confirmDeletePost(post: Post) {
        ConfirmDialogHelper.showDeleteDialog(
            context = this,
            title = getString(R.string.delete_post),
            message = getString(R.string.delete_post_message),
            onConfirm = { deletePost(post) }
        )
    }

    private fun deletePost(post: Post) {
        postDao.deletePost(
            postId = post.id,
            onSuccess = {
                adapter.removePost(post)
                setResult(RESULT_OK)
                loadPosts()
            },
            onError = { msg ->
                Toast.makeText(
                    this,
                    getString(R.string.error_delete_post, msg),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    private fun openCommentsDialog(post: Post) {
        val dialog = CommentsDialog().apply {
            postId = post.id
            onCommentCountChanged = { delta ->
                adapter.updateCommentCount(post.id, delta)
                setResult(RESULT_OK)
            }
            onAuthorClick = { userId -> openPublicProfile(userId) }
        }
        dialog.show(supportFragmentManager, "comments_${post.id}")
    }

    private fun openPublicProfile(userId: String) {
        if (userId.isBlank()) return
        publicProfileLauncher.launch(
            Intent(this, PublicProfileActivity::class.java).putExtra(EXTRA_USER_ID, userId)
        )
    }

    private fun toggleLike(post: Post) {
        val uid = auth.getCurrentUid() ?: return
        postDao.toggleLike(
            post = post,
            userId = uid,
            onSuccess = { updatedPost ->
                if (updatedPost.likedBy.contains(uid)) SoundEffectHelper.playTap()
                adapter.updatePost(updatedPost)
                setResult(RESULT_OK)
            },
            onError = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun toggleFollow() {
        val currentUid = auth.getCurrentUid().orEmpty()
        if (currentUid.isBlank() || currentUid == profileUserId) return

        val wasFollowing = isFollowingProfile
        binding.btnFollow.isEnabled = false

        userDao.toggleFollow(
            currentUserId = currentUid,
            targetUserId = profileUserId,
            isFollowing = wasFollowing,
            onSuccess = {
                isFollowingProfile = !wasFollowing
                followersCount = (followersCount + if (isFollowingProfile) 1 else -1)
                    .coerceAtLeast(0)
                binding.txtFollowersCount.text = followersCount.toString()
                updateFollowButton()
                setResult(RESULT_OK)
            },
            onError = { msg ->
                binding.btnFollow.isEnabled = true
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun updateFollowButton() {
        val primary = ContextCompat.getColor(this, R.color.primary)
        val onPrimary = ContextCompat.getColor(this, R.color.secundary)
        val surface = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSurface
        )

        binding.btnFollow.text = getString(
            if (isFollowingProfile) R.string.following else R.string.follow
        )
        binding.btnFollow.alpha = 1f
        binding.btnFollow.strokeColor = ColorStateList.valueOf(primary)
        binding.btnFollow.strokeWidth = if (isFollowingProfile) {
            resources.displayMetrics.density.toInt().coerceAtLeast(1)
        } else {
            0
        }
        binding.btnFollow.backgroundTintList = ColorStateList.valueOf(
            if (isFollowingProfile) surface else primary
        )
        binding.btnFollow.setTextColor(if (isFollowingProfile) primary else onPrimary)
        binding.btnFollow.isEnabled = true
    }

    private fun openChat() {
        val currentUid = auth.getCurrentUid().orEmpty()
        if (currentUid.isBlank() || currentUid == profileUserId) return

        chatLauncher.launch(
            Intent(this, ChatActivity::class.java).putExtra(
                ChatActivity.EXTRA_USER_ID,
                profileUserId
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
}
