package com.example.freechat

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.IOException

@SuppressLint("SetTextI18n")
class ChatAdapter(private val messages: MutableList<Message>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    // Simple single MediaPlayer instance to prevent overlap
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingPosition: Int = -1

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].sender == Sender.ME) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            val view = inflater.inflate(R.layout.item_chat_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is SentMessageViewHolder) {
            holder.bind(message, position)
        } else if (holder is ReceivedMessageViewHolder) {
            holder.bind(message, position)
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessageContent: TextView = itemView.findViewById(R.id.tvMessageContent)
        private val layoutDataVoice: LinearLayout = itemView.findViewById(R.id.layoutDataVoice)
        private val ivPlayPause: ImageView = itemView.findViewById(R.id.ivPlayPause)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)

        fun bind(message: Message, position: Int) {
            if (message.type == MessageType.TEXT) {
                tvMessageContent.visibility = View.VISIBLE
                layoutDataVoice.visibility = View.GONE
                tvMessageContent.text = message.content
            } else {
                tvMessageContent.visibility = View.GONE
                layoutDataVoice.visibility = View.VISIBLE
                tvDuration.text = "Voice Note"

                ivPlayPause.setImageResource(if (position == currentPlayingPosition) R.drawable.ic_pause else R.drawable.ic_play)

                layoutDataVoice.setOnClickListener {
                    toggleAudio(message.content, position)
                }
            }
        }
    }

    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessageContent: TextView = itemView.findViewById(R.id.tvMessageContent)
        private val layoutDataVoice: LinearLayout = itemView.findViewById(R.id.layoutDataVoice)
        private val ivPlayPause: ImageView = itemView.findViewById(R.id.ivPlayPause)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)

        fun bind(message: Message, position: Int) {
            if (message.type == MessageType.TEXT) {
                tvMessageContent.visibility = View.VISIBLE
                layoutDataVoice.visibility = View.GONE

                // Parse "Username:Message" to "[Username]: Message"
                val rawText = message.content
                val parts = rawText.split(":", limit = 2)
                if (parts.size == 2) {
                    val username = parts[0]
                    val msg = parts[1]
                    tvMessageContent.text = "[$username]: $msg"
                } else {
                    tvMessageContent.text = rawText
                }
            } else {
                tvMessageContent.visibility = View.GONE
                layoutDataVoice.visibility = View.VISIBLE
                tvDuration.text = "Voice Note"

                ivPlayPause.setImageResource(if (position == currentPlayingPosition) R.drawable.ic_pause else R.drawable.ic_play)

                layoutDataVoice.setOnClickListener {
                    toggleAudio(message.content, position)
                }
            }
        }
    }

    private fun toggleAudio(filePath: String, position: Int) {
        if (currentPlayingPosition == position) {
            // Stop playing
            stopAudio()
        } else {
            // Play new audio
            playAudio(filePath, position)
        }
    }

    private fun playAudio(filePath: String, position: Int) {
        stopAudio()
        try {
            mediaPlayer = MediaPlayer()
            mediaPlayer?.setDataSource(filePath)
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            currentPlayingPosition = position
            notifyItemChanged(position)

            mediaPlayer?.setOnCompletionListener {
                stopAudio()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            // In case of error, reset state
            stopAudio()
        }
    }

    private fun stopAudio() {
        if (mediaPlayer != null) {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.stop()
            }
            mediaPlayer!!.release()
            mediaPlayer = null
        }
        if (currentPlayingPosition != -1) {
            val prevPosition = currentPlayingPosition
            currentPlayingPosition = -1
            notifyItemChanged(prevPosition)
        }
    }

    // Clean up resources when adapter is detached or activity destroyed logic
    fun releaseMediaPlayer() {
        stopAudio()
    }
}
