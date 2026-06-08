package com.victor.postly.adapter

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.victor.postly.R
import com.victor.postly.databinding.ItemMessageBinding
import com.victor.postly.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val currentUid: String
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val messages: MutableList<ChatMessage> = mutableListOf()

    // Controla qual MediaPlayer está ativo para não sobrepor áudios
    private var activePlayer: MediaPlayer? = null
    private var activePlayBtnReset: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())

    class MessageViewHolder(val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val isOwn = message.senderId == currentUid
        val ownColor = ContextCompat.getColor(holder.binding.root.context, R.color.primary)
        val ownTextColor = ContextCompat.getColor(holder.binding.root.context, R.color.secundary)
        val otherColor = MaterialColors.getColor(
            holder.binding.root,
            com.google.android.material.R.attr.colorSurfaceVariant
        )
        val otherTextColor = MaterialColors.getColor(
            holder.binding.root,
            com.google.android.material.R.attr.colorOnSurface
        )

        with(holder.binding) {
            // Separador de data
            val showDate = position == 0 || !isSameDay(
                message.timestamp,
                messages[position - 1].timestamp
            )
            txtDateSeparator.visibility = if (showDate) View.VISIBLE else View.GONE
            if (showDate) {
                txtDateSeparator.text =
                    formatDateSeparator(holder.binding.root.context, message.timestamp)
            }

            // Alinhamento da bolha
            messageRow.gravity = if (isOwn) Gravity.END else Gravity.START
            messageBubble.setCardBackgroundColor(if (isOwn) ownColor else otherColor)
            txtMessageTime.text = formatTime(message.timestamp)
            txtMessageTime.setTextColor(if (isOwn) ownTextColor else otherTextColor)

            // Resetar visibilidades
            txtMessageText.visibility = View.GONE
            imgMessagePhoto.visibility = View.GONE
            layoutAudioPlayer.visibility = View.GONE

            when (message.type) {
                ChatMessage.TYPE_IMAGE -> {
                    imgMessagePhoto.visibility = View.VISIBLE
                    bindPhoto(holder, message)
                }
                ChatMessage.TYPE_AUDIO -> {
                    layoutAudioPlayer.visibility = View.VISIBLE
                    txtAudioDuration.setTextColor(if (isOwn) ownTextColor else otherTextColor)
                    bindAudio(holder, message, isOwn, ownTextColor, otherTextColor)
                }
                else -> {
                    txtMessageText.visibility = View.VISIBLE
                    txtMessageText.text = message.text
                    txtMessageText.setTextColor(if (isOwn) ownTextColor else otherTextColor)
                }
            }
        }
    }

    private fun bindPhoto(holder: MessageViewHolder, message: ChatMessage) {
        try {
            val bytes = Base64.decode(message.mediaBase64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            holder.binding.imgMessagePhoto.setImageBitmap(bmp)
        } catch (_: Exception) {
            holder.binding.imgMessagePhoto.setImageDrawable(null)
        }
    }

    private fun bindAudio(
        holder: MessageViewHolder,
        message: ChatMessage,
        isOwn: Boolean,
        ownTextColor: Int,
        otherTextColor: Int
    ) {
        val binding = holder.binding
        binding.seekBarAudio.progress = 0
        binding.txtAudioDuration.text = "0:00"
        binding.btnPlayAudio.setImageResource(android.R.drawable.ic_media_play)

        binding.btnPlayAudio.setOnClickListener {
            toggleAudioPlayback(binding, message, isOwn, ownTextColor, otherTextColor)
        }
    }

    private fun toggleAudioPlayback(
        binding: ItemMessageBinding,
        message: ChatMessage,
        isOwn: Boolean,
        ownTextColor: Int,
        otherTextColor: Int
    ) {
        // Se já está tocando este áudio, pausar
        val currentPlayer = activePlayer
        if (currentPlayer != null && currentPlayer.isPlaying) {
            currentPlayer.pause()
            binding.btnPlayAudio.setImageResource(android.R.drawable.ic_media_play)
            return
        }

        // Parar qualquer áudio anterior
        stopActivePlayer()

        try {
            val bytes = Base64.decode(message.mediaBase64, Base64.DEFAULT)
            val tmpFile = java.io.File.createTempFile("audio_", ".aac", binding.root.context.cacheDir)
            tmpFile.writeBytes(bytes)

            val mp = MediaPlayer().apply {
                setDataSource(tmpFile.absolutePath)
                prepare()
            }

            activePlayer = mp
            activePlayBtnReset = {
                binding.btnPlayAudio.setImageResource(android.R.drawable.ic_media_play)
                binding.seekBarAudio.progress = 0
                binding.txtAudioDuration.text = "0:00"
            }

            binding.btnPlayAudio.setImageResource(android.R.drawable.ic_media_pause)
            binding.seekBarAudio.max = mp.duration
            binding.txtAudioDuration.text = formatAudioTime(mp.duration)

            mp.start()

            // Atualizar seekbar em tempo real
            val updateRunnable = object : Runnable {
                override fun run() {
                    if (mp.isPlaying) {
                        binding.seekBarAudio.progress = mp.currentPosition
                        binding.txtAudioDuration.text =
                            "${formatAudioTime(mp.currentPosition)} / ${formatAudioTime(mp.duration)}"
                        handler.postDelayed(this, 200)
                    }
                }
            }
            handler.post(updateRunnable)

            // Seekbar manual
            binding.seekBarAudio.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) mp.seekTo(progress)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })

            mp.setOnCompletionListener {
                handler.removeCallbacks(updateRunnable)
                binding.btnPlayAudio.setImageResource(android.R.drawable.ic_media_play)
                binding.seekBarAudio.progress = 0
                binding.txtAudioDuration.text = formatAudioTime(mp.duration)
                activePlayer = null
                tmpFile.delete()
            }
        } catch (_: Exception) { }
    }

    private fun stopActivePlayer() {
        try {
            activePlayer?.stop()
            activePlayer?.release()
        } catch (_: Exception) {}
        activePlayer = null
        activePlayBtnReset?.invoke()
        activePlayBtnReset = null
    }

    override fun getItemCount(): Int = messages.size

    fun setMessages(newMessages: List<ChatMessage>) {
        stopActivePlayer()
        messages.clear()
        messages.addAll(newMessages.distinctBy { it.id })
        notifyDataSetChanged()
    }

    fun addMessage(message: ChatMessage) {
        if (messages.any { it.id == message.id }) return
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    private fun formatAudioTime(ms: Int): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    private fun formatDateSeparator(context: Context, timestamp: Long): String {
        val today = Calendar.getInstance()
        val date = Calendar.getInstance().apply { timeInMillis = timestamp }
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(date, today) -> context.getString(R.string.message_date_today)
            isSameDay(date, yesterday) -> context.getString(R.string.message_date_yesterday)
            else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    private fun isSameDay(firstTimestamp: Long, secondTimestamp: Long): Boolean {
        val first = Calendar.getInstance().apply { timeInMillis = firstTimestamp }
        val second = Calendar.getInstance().apply { timeInMillis = secondTimestamp }
        return isSameDay(first, second)
    }

    private fun isSameDay(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }
}
