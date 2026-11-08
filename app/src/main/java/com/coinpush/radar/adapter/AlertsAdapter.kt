package com.coinpush.radar.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.coinpush.radar.R
import com.coinpush.radar.databinding.ItemAlertRowBinding
import com.coinpush.radar.engine.BinanceFeedEngine
import com.coinpush.radar.model.AlertSignalItem

class AlertsAdapter(
    private val context: Context,
    private var alerts: MutableList<AlertSignalItem>
) : RecyclerView.Adapter<AlertsAdapter.AlertViewHolder>() {

    fun updateList(newList: List<AlertSignalItem>) {
        alerts.clear()
        alerts.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemAlertRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(alerts[position])
    }

    override fun getItemCount(): Int = alerts.size

    inner class AlertViewHolder(val binding: ItemAlertRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AlertSignalItem) {
            binding.symbolTitleTxt.text = item.symbol
            binding.alertTypeTxt.text = item.alertType
            binding.fifteenMinTxt.text = item.description
            binding.alertPriceTxt.text = "$${BinanceFeedEngine.formatPrice(item.price)}"

            val timeDiffMinutes = (System.currentTimeMillis() - item.timestamp) / 60000
            binding.alertTimeTxt.text = if (timeDiffMinutes <= 1) "Just now" else "${timeDiffMinutes}m ago"

            if (item.iconRes != 0) {
                binding.alertImg.setImageResource(item.iconRes)
            } else {
                binding.alertImg.setImageResource(R.drawable.going_up)
            }

            // Customize badge color based on alert type
            if (item.alertType.contains("down", ignoreCase = true)) {
                binding.alertTypeTxt.setBackgroundResource(R.drawable.bg_pill_red)
                binding.alertTypeTxt.setTextColor(context.getColor(R.color.down_text_color))
            } else if (item.alertType.contains("break", ignoreCase = true) || item.alertType.contains("going", ignoreCase = true)) {
                binding.alertTypeTxt.setBackgroundResource(R.drawable.bg_pill_green)
                binding.alertTypeTxt.setTextColor(context.getColor(R.color.up_text_color))
            } else {
                binding.alertTypeTxt.setBackgroundResource(R.drawable.bg_pill_gold)
                binding.alertTypeTxt.setTextColor(context.getColor(R.color.title_color))
            }

            binding.root.setOnClickListener {
                try {
                    val base = item.symbol.removeSuffix("USDT")
                    val url = "https://www.binance.com/en/trade/${base}_USDT"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "${item.symbol}: ${item.alertType}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
