package com.coinpush.radar.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.coinpush.radar.R
import com.coinpush.radar.databinding.ItemMoverRowBinding
import com.coinpush.radar.engine.BybitFeedEngine
import com.coinpush.radar.model.MoverItem
import java.util.Locale

class MoversAdapter(
    private val context: Context,
    private var movers: MutableList<MoverItem>,
    private val onMoverClick: (MoverItem) -> Unit
) : RecyclerView.Adapter<MoversAdapter.MoverViewHolder>() {

    fun updateList(newList: List<MoverItem>) {
        movers.clear()
        movers.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoverViewHolder {
        val binding = ItemMoverRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MoverViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MoverViewHolder, position: Int) {
        holder.bind(movers[position])
    }

    override fun getItemCount(): Int = movers.size

    inner class MoverViewHolder(val binding: ItemMoverRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MoverItem) {
            binding.tvRank.text = item.rank.toString()
            binding.tvSymbol.text = "${item.symbol} / ${item.name}"
            binding.tvVolume.text = "Vol $${BybitFeedEngine.formatAmount(item.quoteVolume)}"
            binding.tvPrice.text = "$${BybitFeedEngine.formatPrice(item.price)}"

            val isPositive = item.priceChangePercent >= 0.0
            val prefix = if (isPositive) "+" else ""
            binding.tvChange.text = "$prefix${String.format(Locale.US, "%.2f", item.priceChangePercent)}%"

            if (isPositive) {
                binding.tvChange.setTextColor(ContextCompat.getColor(context, R.color.up_text_color))
                binding.tvChange.setBackgroundResource(R.drawable.bg_pill_green)
            } else {
                binding.tvChange.setTextColor(ContextCompat.getColor(context, R.color.down_text_color))
                binding.tvChange.setBackgroundResource(R.drawable.bg_pill_red)
            }

            // In-app inspection only — zero external redirects!
            binding.root.setOnClickListener {
                onMoverClick(item)
            }
        }
    }
}
