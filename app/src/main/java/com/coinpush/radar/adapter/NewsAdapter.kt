package com.coinpush.radar.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.coinpush.radar.R
import com.coinpush.radar.databinding.ItemNewsRowBinding
import com.coinpush.radar.model.NewsItem

class NewsAdapter(
    private val context: Context,
    private var news: MutableList<NewsItem>
) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    fun updateList(newList: List<NewsItem>) {
        news.clear()
        news.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val binding = ItemNewsRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NewsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        holder.bind(news[position])
    }

    override fun getItemCount(): Int = news.size

    inner class NewsViewHolder(val binding: ItemNewsRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NewsItem) {
            binding.tvNewsSource.text = item.source
            binding.tvNewsTime.text = item.time
            binding.tvNewsTitle.text = item.title
            binding.tvNewsSnippet.text = item.snippet
            binding.tvSentiment.text = item.sentiment

            if (item.sentiment == "BULLISH") {
                binding.tvSentiment.setTextColor(ContextCompat.getColor(context, R.color.up_text_color))
                binding.tvSentiment.setBackgroundResource(R.drawable.bg_pill_green)
            } else {
                binding.tvSentiment.setTextColor(ContextCompat.getColor(context, R.color.gold_accent))
                binding.tvSentiment.setBackgroundResource(R.drawable.bg_pill_gold)
            }

            binding.root.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, item.title, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
