package com.victor.postly.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.victor.postly.R
import com.victor.postly.adapter.CommentAdapter
import com.victor.postly.auth.UserAuth
import com.victor.postly.dao.CommentDao
import com.victor.postly.dao.UserDao
import com.victor.postly.databinding.DialogCommentsBinding
import com.victor.postly.model.Comment
import com.victor.postly.utils.Base64Converter

class CommentsDialog : BottomSheetDialogFragment() {

    private var _binding: DialogCommentsBinding? = null
    private val binding get() = _binding!!

    /** ID do post cujos comentários serão exibidos — obrigatório definir antes de show() */
    var postId: String = ""

    /** Callback chamado sempre que o total de comentários muda (para atualizar o card no feed) */
    var onCommentCountChanged: ((delta: Int) -> Unit)? = null

    private val auth = UserAuth()
    private val commentDao = CommentDao()
    private val userDao = UserDao()
    private val converter = Base64Converter()

    private lateinit var adapter: CommentAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentUid = auth.getCurrentUid() ?: ""

        adapter = CommentAdapter(
            currentUid = currentUid,
            onDelete = { comment -> confirmDeleteComment(comment) }
        )

        binding.recyclerComments.apply {
            this.adapter = this@CommentsDialog.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        loadComments()
        loadCurrentUserAvatar(currentUid)

        binding.btnCloseComments.setOnClickListener { dismiss() }

        binding.btnSendComment.setOnClickListener { sendComment() }

        // Permite enviar pelo teclado (imeOptions="actionSend")
        binding.edtComment.setOnEditorActionListener { _, _, _ ->
            sendComment()
            true
        }
    }

    override fun onStart() {
        super.onStart()
        // Garante que o dialog expanda sem que o teclado o sobreponha
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─── Carregar comentários ─────────────────────────────────────────────────

    private fun loadComments() {
        if (postId.isEmpty()) return
        showLoading(true)

        commentDao.getComments(postId) { comments ->
            if (_binding == null) return@getComments
            showLoading(false)

            adapter.setComments(comments)
            updateHeader(comments.size)

            binding.txtNoComments.visibility =
                if (comments.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerComments.visibility =
                if (comments.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    // ─── Enviar comentário ────────────────────────────────────────────────────

    private fun sendComment() {
        val text = binding.edtComment.text.toString().trim()
        if (text.isEmpty()) return

        val uid = auth.getCurrentUid() ?: return
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
                if (_binding == null) return@addComment
                binding.btnSendComment.isEnabled = true
                binding.edtComment.setText("")

                adapter.addComment(comment)
                binding.recyclerComments.scrollToPosition(adapter.itemCount - 1)
                binding.txtNoComments.visibility = View.GONE
                binding.recyclerComments.visibility = View.VISIBLE

                updateHeader(adapter.itemCount)
                onCommentCountChanged?.invoke(+1)
            },
            onError = { msg ->
                if (_binding == null) return@addComment
                binding.btnSendComment.isEnabled = true
                Toast.makeText(context, "Erro: $msg", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // ─── Excluir comentário ───────────────────────────────────────────────────

    private fun confirmDeleteComment(comment: Comment) {
        AlertDialog.Builder(requireContext())
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
                if (_binding == null) return@deleteComment
                adapter.removeComment(comment)
                updateHeader(adapter.itemCount)
                onCommentCountChanged?.invoke(-1)

                if (adapter.itemCount == 0) {
                    binding.txtNoComments.visibility = View.VISIBLE
                    binding.recyclerComments.visibility = View.GONE
                }
            },
            onError = { msg ->
                if (_binding == null) return@deleteComment
                Toast.makeText(context, "Erro: $msg", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // ─── Avatar do usuário atual ──────────────────────────────────────────────

    private fun loadCurrentUserAvatar(uid: String) {
        userDao.getUser(uid) { user ->
            if (_binding == null || user?.photo.isNullOrEmpty()) return@getUser
            val bmp = converter.stringToBitmap(user!!.photo!!)
            binding.imgCurrentUserAvatar.setImageBitmap(bmp)
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun updateHeader(count: Int) {
        binding.txtCommentCount.text = if (count == 0)
            getString(R.string.comments)
        else
            getString(R.string.comments_count, count)
    }

    private fun showLoading(loading: Boolean) {
        binding.progressComments.visibility = if (loading) View.VISIBLE else View.GONE
    }
}