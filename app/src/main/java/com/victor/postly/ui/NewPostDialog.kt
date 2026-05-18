package com.victor.postly.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.location.Address
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.victor.postly.R
import com.victor.postly.auth.UserAuth
import com.victor.postly.dao.PostDao
import com.victor.postly.databinding.DialogNewPostBinding
import com.victor.postly.model.Post
import com.victor.postly.utils.Base64Converter
import com.victor.postly.utils.LocalizacaoHelper

class NewPostDialog : DialogFragment() {

    private var _binding: DialogNewPostBinding? = null
    private val binding get() = _binding!!

    var onPostSaved: (() -> Unit)? = null
    var editPost: Post? = null

    private var selectedBitmap: Bitmap? = null
    private val converter = Base64Converter()
    private val auth = UserAuth()

    private var capturedLat: Double? = null
    private var capturedLng: Double? = null
    private var capturedLocationName: String? = null

    private lateinit var localizacaoHelper: LocalizacaoHelper

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(requireContext().contentResolver, uri)
            )
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
        }
        selectedBitmap = bitmap
        binding.imgPost.setImageBitmap(bitmap)
        binding.imgPost.visibility = View.VISIBLE
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchLocation()
        } else {
            Toast.makeText(context, getString(R.string.location_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogNewPostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        localizacaoHelper = LocalizacaoHelper(requireContext())

        editPost?.let { post ->
            binding.txtDialogTitle.text = getString(R.string.edit_post_dialog_title)
            binding.edtDescription.setText(post.description)

            if (!post.image.isNullOrEmpty()) {
                val bmp = converter.stringToBitmap(post.image)
                binding.imgPost.setImageBitmap(bmp)
                binding.imgPost.visibility = View.VISIBLE
            }

            if (!post.locationName.isNullOrEmpty()) {
                capturedLat = post.latitude
                capturedLng = post.longitude
                capturedLocationName = post.locationName
                binding.chipLocation.text = post.locationName
                binding.chipLocation.visibility = View.VISIBLE
            }

            binding.btnPostar.text = getString(R.string.save)
        }

        binding.btnSelectImage.setOnClickListener { pickImage.launch("image/*") }
        binding.btnLocation.setOnClickListener { requestLocation() }
        binding.chipLocation.setOnCloseIconClickListener { clearLocation() }
        binding.btnPostar.setOnClickListener { savePost() }
    }

    override fun onStart() {
        super.onStart()
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─── Localização ──────────────────────────────────────────────────────────

    private fun requestLocation() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(requireContext(), permission)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocation()
        } else {
            locationPermissionLauncher.launch(permission)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        if (_binding == null) return

        binding.btnLocation.isEnabled = false
        binding.btnLocation.alpha = 0.5f

        localizacaoHelper.obterLocalizacaoAtual(object : LocalizacaoHelper.Callback {

            override fun onLocalizacaoRecebida(
                endereco: Address,
                latitude: Double,
                longitude: Double
            ) {
                if (_binding == null) return

                capturedLat = latitude
                capturedLng = longitude
                capturedLocationName = endereco.locality
                    ?: endereco.subAdminArea
                            ?: endereco.adminArea

                val label = capturedLocationName
                    ?: "%.4f, %.4f".format(latitude, longitude)

                binding.btnLocation.isEnabled = true
                binding.btnLocation.alpha = 1f
                binding.chipLocation.text = label
                binding.chipLocation.visibility = View.VISIBLE
            }

            override fun onErro(mensagem: String) {
                if (_binding == null) return

                binding.btnLocation.isEnabled = true
                binding.btnLocation.alpha = 1f
                Toast.makeText(context, mensagem, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun clearLocation() {
        capturedLat = null
        capturedLng = null
        capturedLocationName = null
        binding.chipLocation.visibility = View.GONE
    }

    // ─── Salvar post ──────────────────────────────────────────────────────────

    private fun savePost() {
        val description = binding.edtDescription.text.toString().trim()
        if (description.isEmpty()) {
            binding.edtDescription.error = getString(R.string.what_are_you_thinking)
            return
        }

        val uid = auth.getCurrentUid()
        if (uid == null) {
            Toast.makeText(context, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        val imageBase64 = selectedBitmap?.let { converter.bitmapToString(it) } ?: editPost?.image

        val post = Post(
            id = editPost?.id ?: "",
            userId = editPost?.userId ?: uid,
            description = description,
            image = imageBase64,
            timestamp = editPost?.timestamp ?: System.currentTimeMillis(),
            latitude = capturedLat,
            longitude = capturedLng,
            locationName = capturedLocationName
        )

        val dao = PostDao()

        if (editPost != null) {
            dao.updatePost(post,
                onSuccess = {
                    if (_binding == null) return@updatePost
                    setLoading(false)
                    Toast.makeText(context, getString(R.string.post_updated), Toast.LENGTH_SHORT).show()
                    onPostSaved?.invoke()
                    dismiss()
                },
                onError = { msg ->
                    if (_binding == null) return@updatePost
                    setLoading(false)
                    Toast.makeText(context, getString(R.string.error_comment, msg), Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            dao.addPost(post,
                onSuccess = {
                    if (_binding == null) return@addPost
                    setLoading(false)
                    Toast.makeText(context, getString(R.string.post_created), Toast.LENGTH_SHORT).show()
                    onPostSaved?.invoke()
                    dismiss()
                },
                onError = { msg ->
                    if (_binding == null) return@addPost
                    setLoading(false)
                    Toast.makeText(context, getString(R.string.error_comment, msg), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnPostar.isEnabled = !isLoading
        binding.btnSelectImage.isEnabled = !isLoading
        binding.btnLocation.isEnabled = !isLoading
        binding.btnPostar.text = when {
            isLoading && editPost != null -> getString(R.string.saving)
            isLoading                     -> getString(R.string.posting)
            editPost != null              -> getString(R.string.save)
            else                          -> getString(R.string.post_button)
        }
    }
}