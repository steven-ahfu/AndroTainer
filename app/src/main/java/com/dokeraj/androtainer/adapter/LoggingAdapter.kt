package com.dokeraj.androtainer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dokeraj.androtainer.databinding.LoggingCardItemBinding
import com.dokeraj.androtainer.models.LogItem

class LoggingAdapter(private val logList: List<LogItem>) : RecyclerView.Adapter<LoggingAdapter.LoggingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LoggingViewHolder {
        val binding = LoggingCardItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LoggingViewHolder(binding)

    }

    override fun onBindViewHolder(holder: LoggingViewHolder, position: Int) {
        val currentItem = logList[position]
        holder.tvLogLine.text = currentItem.logLine
    }

    override fun getItemCount() = logList.size

    class LoggingViewHolder(
        val binding: LoggingCardItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        val tvLogLine: TextView = binding.tvLoggingItem
    }
}