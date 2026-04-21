package com.claudeagent.phone

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class SessionsAdapter(
    private val onClick: (ChatStore.Session) -> Unit,
) : ListAdapter<ChatStore.Session, SessionsAdapter.VH>(DIFF) {

    var activeId: String? = null
        set(value) {
            val old = field
            field = value
            // Naïve refresh: only two rows can change state, so let DiffUtil
            // find them on next submitList. For explicit immediate feedback
            // we could look up indices and call notifyItemChanged on both.
            if (old != value) notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = getItem(position)
        holder.title.text = s.title.ifBlank { "New chat" }
        holder.meta.text = DateUtils.getRelativeTimeSpanString(
            s.updatedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        )
        holder.itemView.isActivated = (s.id == activeId)
        holder.itemView.setOnClickListener { onClick(s) }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.sessionTitle)
        val meta: TextView = itemView.findViewById(R.id.sessionMeta)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatStore.Session>() {
            override fun areItemsTheSame(
                oldItem: ChatStore.Session,
                newItem: ChatStore.Session,
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: ChatStore.Session,
                newItem: ChatStore.Session,
            ): Boolean = oldItem == newItem
        }
    }
}
