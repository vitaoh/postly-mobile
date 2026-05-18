package com.victor.postly.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

class Base64Converter {

    companion object {
        // Firestore tem limite de 1MB por documento.
        // Imagens grandes geram Base64 que estoura esse limite.
        // 800px garante qualidade visual adequada mantendo o arquivo pequeno.
        private const val MAX_DIMENSION = 800
    }

    fun bitmapToString(bitmap: Bitmap): String {
        // BUG 4 CORRIGIDO: redimensiona antes de codificar para não ultrapassar
        // o limite de 1MB de documento do Firestore
        val scaled = scaleBitmap(bitmap)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        if (scaled != bitmap) scaled.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
    }

    fun stringToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) return bitmap

        val ratio = minOf(
            MAX_DIMENSION.toFloat() / width,
            MAX_DIMENSION.toFloat() / height
        )
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}