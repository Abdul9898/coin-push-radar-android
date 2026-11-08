package com.coinpush.radar.adapter

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.coinpush.radar.R
import com.coinpush.radar.databinding.ItemCoinPushRowBinding
import com.coinpush.radar.engine.BinanceFeedEngine
import com.coinpush.radar.model.CoinPushItem
import java.util.Locale

class CoinPushAdapter(
    private val context: Context,
    private var coins: MutableList<CoinPushItem>,
    private val onSubToggle: (CoinPushItem, Boolean) -> Unit
) : RecyclerView.Adapter<CoinPushAdapter.CoinViewHolder>() {

    private val sharedPrefs = context.getSharedPreferences("coin_push_subs", Context.MODE_PRIVATE)

    fun updateList(newList: List<CoinPushItem>) {
        coins.clear()
        coins.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoinViewHolder {
        val binding = ItemCoinPushRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CoinViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CoinViewHolder, position: Int) {
        holder.bind(coins[position])
    }

    override fun getItemCount(): Int = coins.size

    inner class CoinViewHolder(val binding: ItemCoinPushRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val intervals = arrayOf(
            binding.tvInterval1s,
            binding.tvInterval1m,
            binding.tvInterval5m,
            binding.tvInterval15m,
            binding.tvInterval1h,
            binding.tvInterval4h,
            binding.tvInterval1d
        )

        private val intervalKeys = arrayOf("1s", "1m", "5m", "15m", "1h", "4h", "1d")

        fun bind(item: CoinPushItem) {
            binding.symbolTitle.text = item.baseAsset
            binding.symbolName.text = item.name
            binding.price.text = BinanceFeedEngine.formatPrice(item.price)

            // Price change percentage badge
            val isPositive = item.priceChangePercent >= 0.0
            val prefix = if (isPositive) "+" else ""
            val changeFormatted = "$prefix${String.format(Locale.US, "%.2f", item.priceChangePercent)}%"
            binding.percentUp.text = changeFormatted

            if (isPositive) {
                binding.percentUp.setTextColor(ContextCompat.getColor(context, R.color.up_text_color))
                binding.percentUp.setBackgroundResource(R.drawable.bg_pill_green)
            } else {
                binding.percentUp.setTextColor(ContextCompat.getColor(context, R.color.down_text_color))
                binding.percentUp.setBackgroundResource(R.drawable.bg_pill_red)
            }

            // Latest Alert Badge
            when (item.alertType) {
                "Breaks up!" -> {
                    binding.latestAlert.visibility = View.VISIBLE
                    binding.latestAlert.setImageResource(R.drawable.break_up_mini)
                }
                "Going up!" -> {
                    binding.latestAlert.visibility = View.VISIBLE
                    binding.latestAlert.setImageResource(R.drawable.going_up_mini)
                }
                "Imminent movement!" -> {
                    binding.latestAlert.visibility = View.VISIBLE
                    binding.latestAlert.setImageResource(R.drawable.squeeze_mini)
                }
                "Breaks down!", "Going down!" -> {
                    binding.latestAlert.visibility = View.VISIBLE
                    binding.latestAlert.setImageResource(R.drawable.break_down_mini)
                }
                else -> {
                    binding.latestAlert.visibility = View.GONE
                }
            }

            // Subscription status (default true, unrestricted!)
            val isSub = sharedPrefs.getBoolean("sub_${item.symbol}", item.isSubscribed)
            item.isSubscribed = isSub
            updateSwitchIcon(isSub)

            binding.switchPush.setOnClickListener {
                val newSub = !item.isSubscribed
                item.isSubscribed = newSub
                sharedPrefs.edit().putBoolean("sub_${item.symbol}", newSub).apply()
                updateSwitchIcon(newSub)
                onSubToggle(item, newSub)
                val msg = if (newSub) "Alert enabled for ${item.baseAsset}" else "Alert disabled for ${item.baseAsset}"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }

            // Expandable Chart Accordion
            if (item.isChartExpanded) {
                binding.clWebview.visibility = View.VISIBLE
                setupWebView(binding.webView, item)
                setupIntervalChips(item)
            } else {
                binding.clWebview.visibility = View.GONE
            }

            // Toggle Expand / Collapse when clicking header
            binding.clHeader.setOnClickListener {
                item.isChartExpanded = !item.isChartExpanded
                notifyItemChanged(adapterPosition)
            }

            // Trade on Binance
            binding.btnTradeBinance.setOnClickListener {
                try {
                    val url = "https://www.binance.com/en/trade/${item.baseAsset}_USDT"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Opening Binance trade pair...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun updateSwitchIcon(isSub: Boolean) {
            if (isSub) {
                binding.switchPush.setImageResource(R.drawable.checked)
            } else {
                binding.switchPush.setImageResource(R.drawable.unchecked)
            }
        }

        private fun setupWebView(webView: WebView, item: CoinPushItem) {
            webView.settings.apply {
                javaScriptEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            webView.setBackgroundColor(Color.BLACK)
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }

            val pairLower = item.symbol.lowercase(Locale.ROOT)
            val chartUrl = "https://api.coinpush.app/chart/realtime.html?symbol=$pairLower&interval=${item.selectedInterval}"
            webView.loadUrl(chartUrl)
        }

        private fun setupIntervalChips(item: CoinPushItem) {
            for (i in intervals.indices) {
                val tv = intervals[i]
                val key = intervalKeys[i]

                if (key == item.selectedInterval) {
                    tv.setTextColor(ContextCompat.getColor(context, R.color.gold_accent))
                    tv.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    tv.setTextColor(ContextCompat.getColor(context, R.color.passive_gray))
                    tv.setTypeface(null, android.graphics.Typeface.NORMAL)
                }

                tv.setOnClickListener {
                    item.selectedInterval = key
                    setupIntervalChips(item)
                    val pairLower = item.symbol.lowercase(Locale.ROOT)
                    val chartUrl = "https://api.coinpush.app/chart/realtime.html?symbol=$pairLower&interval=$key"
                    binding.webView.loadUrl(chartUrl)
                }
            }
        }
    }
}
