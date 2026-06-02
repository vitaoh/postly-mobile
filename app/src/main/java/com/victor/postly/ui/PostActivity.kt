package com.victor.postly.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.victor.postly.R
import com.victor.postly.adapter.CommentAdapter
import com.victor.postly.auth.UserAuth
import com.victor.postly.dao.CommentDao
import com.victor.postly.dao.PostDao
import com.victor.postly.dao.UserDao
import com.victor.postly.databinding.ActivityPostBinding
import com.victor.postly.databinding.ItemPostBinding
import com.victor.postly.model.Comment
import com.victor.postly.model.Post
import com.victor.postly.model.User
import com.victor.postly.utils.Base64Converter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PostActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_POST_ID = "extra_post_id"
    }

    private lateinit var binding: ActivityPostBinding
    private lateinit var commentAdapter: CommentAdapter

    private val auth = UserAuth()
    private val postDao = PostDao()
    private val commentDao = CommentDao()
    private val userDao = UserDao()
    private val converter = Base64Converter()

    private var postId: String = ""
    private var currentPost: Post? = null
    private var isSendingComment = false

    private val publicProfileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
            loadPost()
            loadComments()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityPostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        postId = intent.getStringExtra(EXTRA_POST_ID).orEmpty()
        if (postId.isBlank()) {
            finish()
            return
        }

        setupInsets()
        setupComments()
        setupListeners()
        loadCurrentUserAvatar()
        loadPost()
        loadComments()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, topInset, view.paddingRight, view.paddingBottom)
            insets
        }

        val composerStartPadding = binding.commentComposer.paddingLeft
        val composerTopPadding = binding.commentComposer.paddingTop
        val composerEndPadding = binding.commentComposer.paddingRight
        val composerBottomPadding = binding.commentComposer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.commentComposer) { view, insets ->
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
        ViewCompat.requestApplyInsets(binding.commentComposer)
    }

    private fun setupComments() {
        commentAdapter = CommentAdapter(
            currentUid = auth.getCurrentUid() ?: "",
            onDelete = { comment -> confirmDeleteComment(comment) },
            onAuthorClick = { userId -> openPublicProfile(userId) }
        )

        binding.recyclerComments.apply {
            adapter = commentAdapter
            layoutManager = LinearLayoutManager(this@PostActivity)
            isNestedScrollingEnabled = false
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.imgLogo.setOnClickListener {
            binding.scrollView.smoothScrollTo(0, 0)
            loadPost()
            loadComments()
        }
        binding.btnSendComment.setOnClickListener { sendComment() }
        binding.edtComment.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendComment()
                true
            } else {
                false
            }
        }
    }

    private fun loadPost() {
        showPostLoading(true)
        postDao.getPost(postId) { post ->
            showPostLoading(false)
            if (isFinishing || isDestroyed) return@getPost
            if (post == null) {
                Toast.makeText(this, getString(R.string.error_comment, "Post nao encontrado"), Toast.LENGTH_SHORT).show()
                finish()
                return@getPost
            }
            currentPost = post
            bindPost(post)
        }
    }

    private fun bindPost(post: Post) = with(binding.postContent) {
        root.setOnClickListener(null)
        root.isClickable = false
        root.isFocusable = false

        txtContent.text = post.description
        txtTimestamp.text = formatTime(post.timestamp)

        if (!post.image.isNullOrEmpty()) {
            val bitmap = converter.stringToBitmap(post.image)
            imgPost.setImageBitmap(bitmap)
            imgPost.visibility = View.VISIBLE
        } else {
            imgPost.visibility = View.GONE
        }

        if (!post.locationName.isNullOrEmpty()) {
            chipLocation.text = post.locationName
            chipLocation.visibility = View.VISIBLE
        } else {
            chipLocation.visibility = View.GONE
        }

        val isOwner = post.userId == auth.getCurrentUid()
        btnEdit.visibility = if (isOwner) View.VISIBLE else View.GONE
        btnDelete.visibility = if (isOwner) View.VISIBLE else View.GONE
        btnEdit.setOnClickListener { openEditDialog(post) }
        btnDelete.setOnClickListener { confirmDeletePost(post) }

        btnComment.setOnClickListener {
            binding.scrollView.smoothScrollTo(0, binding.commentsSection.top)
            binding.edtComment.requestFocus()
        }
        btnLike.setOnClickListener { toggleLike(post) }

        bindActionCounts(this, post)
        bindAuthorClick(this, post.userId)
        loadAuthor(this, post.userId)
    }

    private fun loadAuthor(postBinding: ItemPostBinding, userId: String) {
        if (userId.isBlank()) {
            postBinding.txtName.text = getString(R.string.unknown_user)
            postBinding.txtUsername.text = ""
            postBinding.imgAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
            return
        }

        postBinding.txtName.text = getString(R.string.loading_user)
        postBinding.txtUsername.text = ""
        postBinding.imgAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)

        userDao.getUser(userId) { user ->
            if (isFinishing || isDestroyed || currentPost?.userId != userId) return@getUser
            if (user == null) {
                postBinding.txtName.text = getString(R.string.unknown_user)
                postBinding.txtUsername.text = ""
                return@getUser
            }
            applyUser(postBinding, user)
        }
    }

    private fun applyUser(postBinding: ItemPostBinding, user: User) {
        postBinding.txtName.text = user.name
        postBinding.txtUsername.text = "@${user.username}"
        if (!user.photo.isNullOrEmpty()) {
            val bitmap = converter.stringToBitmap(user.photo)
            postBinding.imgAvatar.setImageBitmap(bitmap)
        } else {
            postBinding.imgAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
        }
    }

    private fun bindAuthorClick(postBinding: ItemPostBinding, userId: String) {
        val hasUser = userId.isNotBlank()
        val listener = View.OnClickListener { openPublicProfile(userId) }
        listOf<View>(postBinding.imgAvatar, postBinding.txtName, postBinding.txtUsername)
            .forEach { view ->
                view.isClickable = hasUser
                view.isFocusable = hasUser
                view.setOnClickListener(if (hasUser) listener else null)
            }
    }

    private fun bindActionCounts(postBinding: ItemPostBinding, post: Post) {
        val normalColor = MaterialColors.getColor(
            postBinding.root,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        val likedColor = ContextCompat.getColor(this, R.color.error)
        val isLiked = post.likedBy.contains(auth.getCurrentUid())

        postBinding.txtCommentCount.text = post.commentCount.toString()
        postBinding.imgCommentIcon.setColorFilter(normalColor)
        postBinding.txtCommentCount.setTextColor(normalColor)

        postBinding.txtLikeCount.text = post.likeCount.toString()
        postBinding.imgLikeIcon.setColorFilter(if (isLiked) likedColor else normalColor)
        postBinding.txtLikeCount.setTextColor(if (isLiked) likedColor else normalColor)
    }

    private fun toggleLike(post: Post) {
        val uid = auth.getCurrentUid() ?: return
        postDao.toggleLike(
            post = post,
            userId = uid,
            onSuccess = { updatedPost ->
                currentPost = updatedPost
                bindActionCounts(binding.postContent, updatedPost)
                setResult(RESULT_OK)
            },
            onError = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun loadComments() {
        showCommentsLoading(true)
        commentDao.getComments(postId) { comments ->
            showCommentsLoading(false)
            if (isFinishing || isDestroyed) return@getComments
            commentAdapter.setComments(comments)
            updateCommentsState(comments.size)
        }
    }

    private fun sendComment() {
        if (isSendingComment) return

        val text = binding.edtComment.text.toString().trim()
        if (text.isEmpty()) return

        val uid = auth.getCurrentUid() ?: return
        isSendingComment = true
        binding.btnSendComment.isEnabled = false

        val comment = Comment(
            postId = postId,
            userId = uid,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        commentDao.addComment(
            postId = postId,
            comment = comment,
            onSuccess = {
                isSendingComment = false
                binding.btnSendComment.isEnabled = true
                binding.edtComment.setText("")
                updateLocalCommentCount(+1)
                loadComments()
                setResult(RESULT_OK)
            },
            onError = { msg ->
                isSendingComment = false
                binding.btnSendComment.isEnabled = true
                Toast.makeText(this, getString(R.string.error_comment, msg), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun confirmDeleteComment(comment: Comment) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_comment))
            .setMessage(getString(R.string.delete_comment_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> deleteComment(comment) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteComment(comment: Comment) {
        commentDao.deleteComment(
            postId = postId,
            commentId = comment.id,
            onSuccess = {
                commentAdapter.removeComment(comment)
                updateLocalCommentCount(-1)
                updateCommentsState(commentAdapter.itemCount)
                setResult(RESULT_OK)
            },
            onError = { msg ->
                Toast.makeText(this, getString(R.string.error_delete_comment, msg), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun updateLocalCommentCount(delta: Int) {
        currentPost = currentPost?.copy(
            commentCount = ((currentPost?.commentCount ?: 0) + delta).coerceAtLeast(0)
        )
        currentPost?.let { bindActionCounts(binding.postContent, it) }
    }

    private fun updateCommentsState(count: Int) {
        binding.txtCommentsTitle.text = when (count) {
            0 -> getString(R.string.comments)
            1 -> getString(R.string.comment_count_one)
            else -> getString(R.string.comments_count, count)
        }
        binding.txtNoComments.visibility = if (count == 0) View.VISIBLE else View.GONE
        binding.recyclerComments.visibility = if (count == 0) View.GONE else View.VISIBLE
    }

    private fun openEditDialog(post: Post) {
        val dialog = NewPostDialog().apply {
            editPost = post
            onPostSaved = {
                setResult(RESULT_OK)
                loadPost()
            }
        }
        dialog.show(supportFragmentManager, "edit_post")
    }

    private fun confirmDeletePost(post: Post) {
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
                setResult(RESULT_OK)
                finish()
            },
            onError = { msg ->
                Toast.makeText(this, getString(R.string.error_delete_post, msg), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun loadCurrentUserAvatar() {
        val uid = auth.getCurrentUid() ?: return
        userDao.getUser(uid) { user ->
            if (isFinishing || isDestroyed || user?.photo.isNullOrEmpty()) return@getUser
            val bitmap = converter.stringToBitmap(user!!.photo!!)
            binding.imgCurrentUserAvatar.setImageBitmap(bitmap)
        }
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

    private fun showPostLoading(loading: Boolean) {
        binding.progressPost.visibility = if (loading) View.VISIBLE else View.GONE
        binding.postContent.root.visibility = if (loading) View.INVISIBLE else View.VISIBLE
    }

    private fun showCommentsLoading(loading: Boolean) {
        binding.progressComments.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000L -> "agora"
            diff < 3_600_000L -> "${diff / 60_000}min"
            diff < 86_400_000L -> "${diff / 3_600_000}h"
            diff < 7 * 86_400_000L -> "${diff / 86_400_000}d"
            else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
