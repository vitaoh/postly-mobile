package com.victor.postly.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.victor.postly.R
import com.victor.postly.adapter.PostAdapter
import com.victor.postly.auth.UserAuth
import com.victor.postly.dao.PostDao
import com.victor.postly.dao.UserDao
import com.victor.postly.databinding.LayoutPublicProfileBinding
import com.victor.postly.model.Post
import com.victor.postly.model.User
import com.victor.postly.utils.Base64Converter

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

    private val publicProfileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
        }
    }

    private val profileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
            adapter.clearUserCache()
            loadUser()
            loadPosts()
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
        adapter = PostAdapter(
            currentUid = "",
            onEdit = {},
            onDelete = {},
            onComment = { post -> openCommentsDialog(post) },
            onAuthorClick = { userId ->
                if (userId != profileUserId) openPublicProfile(userId)
            }
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
            loadPosts()
        }
        binding.btnFollow.visibility = View.GONE
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

    private fun bindUser(user: User) {
        binding.txtName.text = user.name.ifBlank { getString(R.string.unknown_user) }
        binding.txtUsername.text = if (user.username.isBlank()) "" else "@${user.username}"
        binding.txtFollowersCount.text = "0"
        binding.txtFollowingCount.text = "0"

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
}
