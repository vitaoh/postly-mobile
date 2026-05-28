package com.victor.postly.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.DocumentSnapshot
import com.victor.postly.adapter.PostAdapter
import com.victor.postly.auth.UserAuth
import com.victor.postly.dao.PostDao
import com.victor.postly.dao.UserDao
import com.victor.postly.databinding.ActivityHomeBinding
import com.victor.postly.model.Post
import com.victor.postly.utils.Base64Converter
import androidx.activity.result.contract.ActivityResultContracts

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val auth = UserAuth()
    private val postDao = PostDao()
    private val userDao = UserDao()
    private val converter = Base64Converter()

    private lateinit var adapter: PostAdapter

    private var lastDoc: DocumentSnapshot? = null
    private var isLoading = false
    private var hasMorePages = true
    private var currentSearchQuery: String = ""

    private val publicProfileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadAvatar()
            adapter.clearUserCache()
            loadFirstPage()
        }
    }

    private val postLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadFirstPage()
        }
    }

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
            goToLogin(); return
        }

        setupAdapter()
        setupRecycler()
        setupSearch()
        loadFirstPage()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadAvatar()
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

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
                if (dy <= 0 || currentSearchQuery.isNotBlank()) return
                val totalItems = layoutManager.itemCount
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (!isLoading && hasMorePages && lastVisible >= totalItems - 3) loadNextPage()
            }
        })
    }

    private fun setupSearch() {
        binding.edtSearch.addTextChangedListener { editable ->
            val query = editable?.toString() ?: ""
            currentSearchQuery = query
            adapter.filterPosts(query)
            binding.layoutEmpty.visibility =
                if (adapter.itemCount == 0) View.VISIBLE else View.GONE
            if (query.isBlank()) {
                binding.txtEmptyTitle.text = "Nenhum post ainda"
                binding.txtEmptySubtitle.text = "Seja o primeiro a postar! 🚀"
            } else {
                binding.txtEmptyTitle.text = "Nenhum resultado"
                binding.txtEmptySubtitle.text = "Tente buscar por outro termo"
            }
        }
    }

    // ─── Dados ────────────────────────────────────────────────────────────────

    private fun loadFirstPage() {
        setLoading(true)
        hasMorePages = true
        lastDoc = null

        postDao.getFirstPage { posts, cursor ->
            setLoading(false)
            lastDoc = cursor
            hasMorePages = posts.size >= PostDao.PAGE_SIZE.toInt()
            adapter.updatePosts(posts)
            if (currentSearchQuery.isNotBlank()) adapter.filterPosts(currentSearchQuery)
            binding.layoutEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        }
    }

    private fun loadNextPage() {
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

    private fun loadAvatar() {
        val uid = auth.getCurrentUid() ?: return
        userDao.getUser(uid) { user ->
            if (!user?.photo.isNullOrEmpty()) {
                val bitmap = converter.stringToBitmap(user!!.photo!!)
                binding.imgAvatar.setImageBitmap(bitmap)
            }
        }
    }

    // ─── Comentários ──────────────────────────────────────────────────────────

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

    // ─── Edição e exclusão ────────────────────────────────────────────────────

    private fun openPost(post: Post) {
        if (post.id.isBlank()) return
        postLauncher.launch(
            Intent(this, PostActivity::class.java).putExtra(
                PostActivity.EXTRA_POST_ID,
                post.id
            )
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
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun confirmDelete(post: Post) {
        AlertDialog.Builder(this)
            .setTitle("Excluir post")
            .setMessage("Tem certeza que deseja excluir este post?")
            .setPositiveButton("Excluir") { _, _ -> deletePost(post) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deletePost(post: Post) {
        postDao.deletePost(
            postId = post.id,
            onSuccess = {
                adapter.removePost(post)
                if (adapter.itemCount == 0) binding.layoutEmpty.visibility = View.VISIBLE
            },
            onError = { msg ->
                android.widget.Toast.makeText(
                    this,
                    "Erro ao excluir: $msg",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // ─── Listeners ────────────────────────────────────────────────────────────

    private fun setupListeners() {
        binding.imgAvatar.setOnClickListener {
            openPublicProfile(auth.getCurrentUid() ?: "")
        }
        binding.imgLogo.setOnClickListener {
            binding.edtSearch.setText("")
            binding.recyclerFeed.smoothScrollToPosition(0)
        }
        binding.fabNewPost.setOnClickListener {
            val dialog = NewPostDialog().apply { onPostSaved = { loadFirstPage() } }
            dialog.show(supportFragmentManager, "new_post")
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.progressPagination.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
