package com.voiceassistant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.voiceassistant.model.Message

class MessageAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1

        private val DIFF = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(a: Message, b: Message) = a === b
            override fun areContentsTheSame(a: Message, b: Message) = a == b
        }
    }

    override fun getItemViewType(position: Int) =
        if (getItem(position).isUser) TYPE_USER else TYPE_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserVH(inflater.inflate(R.layout.item_message_user, parent, false))
        } else {
            AssistantVH(inflater.inflate(R.layout.item_message_assistant, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        when (holder) {
            is UserVH      -> holder.tvText.text = msg.text
            is AssistantVH -> holder.tvText.text = msg.text
        }
    }

    inner class UserVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvText: TextView = view.findViewById(R.id.tvText)
    }

    inner class AssistantVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvText: TextView = view.findViewById(R.id.tvText)
    }
}
