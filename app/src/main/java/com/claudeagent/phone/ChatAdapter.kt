package com.claudeagent.phone

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Renders chat messages with five visual styles:
 * - USER:      right-aligned accent bubble (what the user said / typed)
 * - ASSISTANT: left-aligned surface bubble (model reasoning + summaries)
 * - ACTION:    centered monospace chip (tool calls like "tap(540, 1200)")
 * - STATUS:    centered italic text (run lifecycle: stopped / error / etc.)
 * - TYPING:    left-aligned italic bubble whose text cycles "Thinking." →
 *              "Thinking.." → "Thinking…" while the agent is running.
 *              MainActivity injects a single synthetic typing row at the end
 *              of the list whenever the agent is mid-run.
 */
class ChatAdapter : ListAdapter<ChatStore.ChatMessage, ChatAdapter.VH>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position).role) {
        "user" -> TYPE_USER
        "assistant" -> TYPE_ASSISTANT
        "action" -> TYPE_ACTION
        "typing" -> TYPE_TYPING
        else -> TYPE_STATUS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = when (viewType) {
            TYPE_USER -> R.layout.item_message_user
            TYPE_ASSISTANT -> R.layout.item_message_assistant
            TYPE_ACTION -> R.layout.item_message_action
            TYPE_TYPING -> R.layout.item_message_typing
            else -> R.layout.item_message_status
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        if (item.role == "typing") {
            holder.startTypingAnimation()
        } else {
            holder.stopTypingAnimation()
            holder.text.text = item.text
        }
    }

    override fun onViewRecycled(holder: VH) {
        holder.stopTypingAnimation()
        super.onViewRecycled(holder)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.messageText)
        private val handler = Handler(Looper.getMainLooper())
        private var tick = 0
        private val typingRunnable = object : Runnable {
            override fun run() {
                text.text = TYPING_FRAMES[tick % TYPING_FRAMES.size]
                tick++
                handler.postDelayed(this, 350L)
            }
        }

        fun startTypingAnimation() {
            stopTypingAnimation()
            tick = 0
            handler.post(typingRunnable)
        }

        fun stopTypingAnimation() {
            handler.removeCallbacks(typingRunnable)
        }
    }

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
        private const val TYPE_ACTION = 2
        private const val TYPE_STATUS = 3
        private const val TYPE_TYPING = 4

        private val TYPING_FRAMES = arrayOf("Thinking.", "Thinking..", "Thinking…")

        private val DIFF = object : DiffUtil.ItemCallback<ChatStore.ChatMessage>() {
            override fun areItemsTheSame(
                oldItem: ChatStore.ChatMessage,
                newItem: ChatStore.ChatMessage,
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: ChatStore.ChatMessage,
                newItem: ChatStore.ChatMessage,
            ): Boolean = oldItem == newItem
        }
    }
}
