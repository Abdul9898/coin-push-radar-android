package com.coinpush.radar.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
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
import com.coinpush.radar.R
import com.coinpush.radar.databinding.DialogCoinDetailBinding
import com.coinpush.radar.engine.BybitFeedEngine
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Locale

class CoinDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogCoinDetailBinding? = null
    private val binding get() = _binding!!

    private var symbol: String = "BTCUSDT"
    private var baseAsset: String = "BTC"
    private var coinName: String = "Bitcoin"
    private var price: Double = 0.0
    private var change: Double = 0.0
    private var high: Double = 0.0
    private var low: Double = 0.0
    private var turnover: Double = 0.0
    private var selectedInterval: String = "15m"

    companion object {
        fun newInstance(
            symbol: String,
            baseAsset: String,
            name: String,
            price: Double,
            change: Double,
            high: Double,
            low: Double,
            turnover: Double
        ): CoinDetailBottomSheet {
            val sheet = CoinDetailBottomSheet()
            val args = Bundle().apply {
                putString("symbol", symbol)
                putString("base", baseAsset)
                putString("name", name)
                putDouble("price", price)
                putDouble("change", change)
                putDouble("high", high)
                putDouble("low", low)
                putDouble("turnover", turnover)
            }
            sheet.arguments = args
            return sheet
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            symbol = it.getString("symbol", "BTCUSDT")
            baseAsset = it.getString("base", "BTC")
            coinName = it.getString("name", "Bitcoin")
            price = it.getDouble("price", 0.0)
            change = it.getDouble("change", 0.0)
            high = it.getDouble("high", 0.0)
            low = it.getDouble("low", 0.0)
            turnover = it.getDouble("turnover", 0.0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCoinDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvDetailSymbol.text = "$baseAsset / USDT"
        binding.tvDetailName.text = "$coinName • Bybit Linear Perpetual"
        binding.tvDetailPrice.text = "$${BybitFeedEngine.formatPrice(price)}"

        val isPositive = change >= 0.0
        val prefix = if (isPositive) "+" else ""
        binding.tvDetailChange.text = "$prefix${String.format(Locale.US, "%.2f", change)}%"
        if (isPositive) {
            binding.tvDetailChange.setBackgroundResource(R.drawable.bg_pill_green)
            binding.tvDetailChange.setTextColor(ContextCompat.getColor(requireContext(), R.color.up_text_color))
        } else {
            binding.tvDetailChange.setBackgroundResource(R.drawable.bg_pill_red)
            binding.tvDetailChange.setTextColor(ContextCompat.getColor(requireContext(), R.color.down_text_color))
        }

        binding.tvDetailHigh.text = "$${BybitFeedEngine.formatPrice(high)}"
        binding.tvDetailLow.text = "$${BybitFeedEngine.formatPrice(low)}"
        binding.tvDetailTurnover.text = "$${BybitFeedEngine.formatAmount(turnover)}"

        setupWebView()
        setupIntervalChips()
        setupAlertToggle()

        binding.btnDetailClose.setOnClickListener { dismiss() }
    }

    private fun setupWebView() {
        binding.wvDetailChart.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        binding.wvDetailChart.setBackgroundColor(Color.BLACK)
        binding.wvDetailChart.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // Stay inside WebView
            }
        }
        loadChart(selectedInterval)
    }

    private fun loadChart(interval: String) {
        val tvInterval = when (interval) {
            "1s", "1m" -> "1"
            "5m" -> "5"
            "15m" -> "15"
            "1h" -> "60"
            "4h" -> "240"
            "1d" -> "D"
            else -> "15"
        }
        val chartUrl = "https://s.tradingview.com/widgetembed/?symbol=BYBIT%3A${symbol}.P&interval=$tvInterval&theme=dark&style=1&timezone=exchange&hide_top_toolbar=0&withdateranges=1&hide_side_toolbar=1&allow_symbol_change=0"
        binding.wvDetailChart.loadUrl(chartUrl)
    }

    private fun setupIntervalChips() {
        val chips = listOf(
            Pair("1s", binding.tvDialogInt1s),
            Pair("1m", binding.tvDialogInt1m),
            Pair("5m", binding.tvDialogInt5m),
            Pair("15m", binding.tvDialogInt15m),
            Pair("1h", binding.tvDialogInt1h),
            Pair("4h", binding.tvDialogInt4h),
            Pair("1d", binding.tvDialogInt1d)
        )

        for ((key, tv) in chips) {
            tv.setOnClickListener {
                selectedInterval = key
                for ((k, t) in chips) {
                    if (k == selectedInterval) {
                        t.setTextColor(ContextCompat.getColor(requireContext(), R.color.title_color))
                        t.setTypeface(null, android.graphics.Typeface.BOLD)
                    } else {
                        t.setTextColor(ContextCompat.getColor(requireContext(), R.color.passive_gray))
                        t.setTypeface(null, android.graphics.Typeface.NORMAL)
                    }
                }
                loadChart(selectedInterval)
            }
        }
    }

    private fun setupAlertToggle() {
        val prefs = requireContext().getSharedPreferences("coin_push_subs", Context.MODE_PRIVATE)
        var isSub = prefs.getBoolean("sub_$symbol", true)
        updateAlertIcon(isSub)

        binding.ivDetailAlertToggle.setOnClickListener {
            isSub = !isSub
            prefs.edit().putBoolean("sub_$symbol", isSub).apply()
            updateAlertIcon(isSub)
            val msg = if (isSub) "Alerts activated for $baseAsset" else "Alerts deactivated for $baseAsset"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAlertIcon(isSub: Boolean) {
        if (isSub) {
            binding.ivDetailAlertToggle.setImageResource(R.drawable.checked)
            binding.tvDetailAlertLabel.text = "Alerts Active for this coin"
            binding.tvDetailAlertLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.gold_accent))
        } else {
            binding.ivDetailAlertToggle.setImageResource(R.drawable.unchecked)
            binding.tvDetailAlertLabel.text = "Alerts Muted for this coin"
            binding.tvDetailAlertLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.passive_gray))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
