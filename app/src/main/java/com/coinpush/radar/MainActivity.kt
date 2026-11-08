package com.coinpush.radar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.coinpush.radar.adapter.AlertsAdapter
import com.coinpush.radar.adapter.CoinPushAdapter
import com.coinpush.radar.adapter.MoversAdapter
import com.coinpush.radar.adapter.NewsAdapter
import com.coinpush.radar.databinding.ActivityMainBinding
import com.coinpush.radar.engine.AlertNotificationManager
import com.coinpush.radar.engine.BybitFeedEngine
import com.coinpush.radar.model.AlertSignalItem
import com.coinpush.radar.model.CoinPushItem
import com.coinpush.radar.model.MoverItem
import com.coinpush.radar.ui.CoinDetailBottomSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Adapters
    private lateinit var coinsAdapter: CoinPushAdapter
    private lateinit var alertsAdapter: AlertsAdapter
    private lateinit var moversAdapter: MoversAdapter
    private lateinit var newsAdapter: NewsAdapter

    // In-memory data collections
    private val allCoinsList = mutableListOf<CoinPushItem>()
    private val displayedCoinsList = mutableListOf<CoinPushItem>()
    private val allAlertsList = mutableListOf<AlertSignalItem>()
    private val gainersList = mutableListOf<MoverItem>()
    private val losersList = mutableListOf<MoverItem>()

    private var isGainersSelected = true
    private var isAllAlertsActive = true
    private var refreshJob: Job? = null
    private var popupDismissJob: Job? = null

    companion object {
        private const val REQ_NOTIF_PERMISSION = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize high-priority notification channel
        AlertNotificationManager.initNotificationChannel(this)
        requestNotificationPermission()

        val prefs = getSharedPreferences("coin_push_radar_settings", Context.MODE_PRIVATE)
        isAllAlertsActive = prefs.getBoolean("all_alerts_active", true)

        setupRecyclerViews()
        setupBottomNav()
        setupTopBar()
        setupMoversSubTabs()
        setupAlertFilterChips()
        setupSettingsControls()
        loadInitialData()
        startLiveRefreshLoop()

        // If launched via notification intent
        intent?.getStringExtra("selected_symbol")?.let { sym ->
            openCoinDetail(sym)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIF_PERMISSION
                )
            }
        }
    }

    private fun setupRecyclerViews() {
        // 1. Coins RecyclerView
        coinsAdapter = CoinPushAdapter(this, displayedCoinsList) { coin, isSub ->
            // Sub toggle callback (unrestricted!)
        }
        binding.allSymbolsRV.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = coinsAdapter
        }

        // 2. Movers RecyclerView (In-app chart bottom sheet, NO external redirect)
        moversAdapter = MoversAdapter(this, mutableListOf()) { mover ->
            val sym = if (mover.symbol.endsWith("USDT")) mover.symbol else "${mover.symbol}USDT"
            openCoinDetail(sym)
        }
        binding.moversRV.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = moversAdapter
        }

        // 3. News RecyclerView
        newsAdapter = NewsAdapter(this, BybitFeedEngine.getNewsFeed().toMutableList())
        binding.newsRV.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = newsAdapter
        }

        // 4. Alerts RecyclerView (In-app chart bottom sheet, NO external redirect)
        alertsAdapter = AlertsAdapter(this, mutableListOf()) { alert ->
            openCoinDetail(alert.symbol)
        }
        binding.alertsRV.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = alertsAdapter
        }

        // SwipeRefresh listeners
        binding.swipeCoins.setOnRefreshListener { refreshMarketData() }
        binding.swipeMovers.setOnRefreshListener { refreshMarketData() }
        binding.swipeNews.setOnRefreshListener {
            newsAdapter.updateList(BybitFeedEngine.getNewsFeed())
            binding.swipeNews.isRefreshing = false
        }
    }

    fun openCoinDetail(symbol: String) {
        val coin = allCoinsList.find { it.symbol.equals(symbol, ignoreCase = true) }
        val sheet = if (coin != null) {
            CoinDetailBottomSheet.newInstance(
                symbol = coin.symbol,
                baseAsset = coin.baseAsset,
                name = coin.name,
                price = coin.price,
                change = coin.priceChangePercent,
                high = coin.highPrice,
                low = coin.lowPrice,
                turnover = coin.quoteVolume
            )
        } else {
            val base = symbol.removeSuffix("USDT")
            CoinDetailBottomSheet.newInstance(
                symbol = symbol,
                baseAsset = base,
                name = base,
                price = 0.0,
                change = 0.0,
                high = 0.0,
                low = 0.0,
                turnover = 0.0
            )
        }
        sheet.show(supportFragmentManager, "CoinDetailSheet")
    }

    private fun showOnScreenAlert(coin: CoinPushItem) {
        runOnUiThread {
            binding.tvPopupTitle.text = "🚀 ${coin.baseAsset} ${coin.alertType}"
            val priceStr = BybitFeedEngine.formatPrice(coin.price)
            val prefix = if (coin.priceChangePercent >= 0) "+" else ""
            val changeStr = "$prefix${String.format(Locale.US, "%.2f", coin.priceChangePercent)}%"
            binding.tvPopupDesc.text = "Price: $$priceStr • 24h: $changeStr • Bybit Linear"

            when (coin.alertType) {
                "Breaks up!" -> binding.ivPopupIcon.setImageResource(R.drawable.break_up)
                "Going up!" -> binding.ivPopupIcon.setImageResource(R.drawable.going_up)
                "Imminent movement!" -> binding.ivPopupIcon.setImageResource(R.drawable.squeeze)
                "Breaks down!" -> binding.ivPopupIcon.setImageResource(R.drawable.break_down)
                else -> binding.ivPopupIcon.setImageResource(R.drawable.going_up)
            }

            binding.btnPopupView.setOnClickListener {
                binding.cardAlertPopup.visibility = View.GONE
                openCoinDetail(coin.symbol)
            }

            binding.ivPopupClose.setOnClickListener {
                binding.cardAlertPopup.visibility = View.GONE
            }

            binding.cardAlertPopup.visibility = View.VISIBLE
            binding.cardAlertPopup.alpha = 0f
            binding.cardAlertPopup.translationY = -40f
            binding.cardAlertPopup.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start()

            // Auto-dismiss after 6 seconds
            popupDismissJob?.cancel()
            popupDismissJob = lifecycleScope.launch {
                delay(6000)
                if (binding.cardAlertPopup.visibility == View.VISIBLE) {
                    binding.cardAlertPopup.animate()
                        .alpha(0f)
                        .translationY(-40f)
                        .setDuration(300)
                        .withEndAction { binding.cardAlertPopup.visibility = View.GONE }
                        .start()
                }
            }
        }
    }

    private fun setupTopBar() {
        updateActivateAllUi()

        // Global "Activate All" master toggle (completely unrestricted!)
        binding.clSubAll.setOnClickListener {
            isAllAlertsActive = !isAllAlertsActive
            val prefs = getSharedPreferences("coin_push_radar_settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("all_alerts_active", isAllAlertsActive).apply()

            val subsPrefs = getSharedPreferences("coin_push_subs", Context.MODE_PRIVATE)
            val editor = subsPrefs.edit()
            for (coin in allCoinsList) {
                coin.isSubscribed = isAllAlertsActive
                editor.putBoolean("sub_${coin.symbol}", isAllAlertsActive)
            }
            editor.apply()

            updateActivateAllUi()
            coinsAdapter.notifyDataSetChanged()

            val statusMsg = if (isAllAlertsActive) {
                "🔓 All 829+ Bybit coin alerts activated! (Unrestricted VIP)"
            } else {
                "All alerts deactivated"
            }
            Toast.makeText(this, statusMsg, Toast.LENGTH_SHORT).show()
        }

        // VIP Badge click info
        binding.tvVipBadge.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Coin Push Radar VIP 🔓")
                .setMessage("VIP status is permanently unlocked:\n\n• Unlimited Bybit Linear Perpetual coins (829+ pairs)\n• Master Activate All toggle active\n• Live 1s / 1m / 15m / 1h candlestick charts\n• On-Screen Popups & Heads-Up Alerts enabled\n• Zero subscription fees forever!")
                .setPositiveButton("OK", null)
                .show()
        }

        // Search Toggle & TextWatcher
        binding.ivSearchToggle.setOnClickListener {
            binding.tvAppTitle.visibility = View.GONE
            binding.tvVipBadge.visibility = View.GONE
            binding.ivSearchToggle.visibility = View.GONE
            binding.edtSearch.visibility = View.VISIBLE
            binding.ivSearchClose.visibility = View.VISIBLE
            binding.edtSearch.requestFocus()
        }

        binding.ivSearchClose.setOnClickListener {
            binding.edtSearch.setText("")
            binding.edtSearch.visibility = View.GONE
            binding.ivSearchClose.visibility = View.GONE
            binding.tvAppTitle.visibility = View.VISIBLE
            binding.tvVipBadge.visibility = View.VISIBLE
            binding.ivSearchToggle.visibility = View.VISIBLE
            filterCoins("")
        }

        binding.edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCoins(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupSettingsControls() {
        // Test Alert Notification button
        binding.btnTestNotification.setOnClickListener {
            requestNotificationPermission()

            val testCoin = allCoinsList.firstOrNull { it.alertType.isNotEmpty() }
                ?: CoinPushItem(
                    symbol = "1000PEPEUSDT",
                    baseAsset = "1000PEPE",
                    name = "Pepe",
                    price = 0.0000213,
                    priceChangePercent = 8.45,
                    alertType = "Going up!"
                )

            val priceStr = BybitFeedEngine.formatPrice(testCoin.price)
            val prefix = if (testCoin.priceChangePercent >= 0) "+" else ""
            val changeStr = "$prefix${String.format(Locale.US, "%.2f", testCoin.priceChangePercent)}%"

            // 1. Post system heads-up notification with sound and vibration
            val posted = AlertNotificationManager.triggerAlertNotification(
                context = this,
                symbol = testCoin.symbol,
                alertType = testCoin.alertType,
                priceFormatted = priceStr,
                changeFormatted = changeStr,
                isTest = true
            )

            // 2. Trigger in-app on-screen pop-up banner
            showOnScreenAlert(testCoin)

            val feedback = if (posted) {
                "🔔 Alert notification popped on screen & status bar!"
            } else {
                "Alert popup shown! (Enable notifications in system settings if status bar icon is hidden)"
            }
            Toast.makeText(this, feedback, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateActivateAllUi() {
        if (isAllAlertsActive) {
            binding.subAll.setImageResource(R.drawable.checked)
            binding.tvActivateAllLabel.text = getString(R.string.activate_all)
            binding.tvActivateAllLabel.setTextColor(ContextCompat.getColor(this, R.color.gold_accent))
        } else {
            binding.subAll.setImageResource(R.drawable.unchecked)
            binding.tvActivateAllLabel.text = "Deactivated"
            binding.tvActivateAllLabel.setTextColor(ContextCompat.getColor(this, R.color.passive_gray))
        }
    }

    private fun filterCoins(query: String) {
        val q = query.trim().uppercase(Locale.ROOT)
        val filtered = if (q.isEmpty()) {
            allCoinsList
        } else {
            allCoinsList.filter {
                it.symbol.contains(q) || it.baseAsset.contains(q) || it.name.uppercase(Locale.ROOT).contains(q)
            }
        }
        displayedCoinsList.clear()
        displayedCoinsList.addAll(filtered)
        coinsAdapter.notifyDataSetChanged()
    }

    private fun setupBottomNav() {
        binding.navView.setOnItemSelectedListener { item ->
            // Hide all tab containers
            binding.containerCoins.visibility = View.GONE
            binding.containerMovers.visibility = View.GONE
            binding.containerNews.visibility = View.GONE
            binding.containerAlerts.visibility = View.GONE
            binding.containerSettings.visibility = View.GONE

            when (item.itemId) {
                R.id.navigation_allSymbols -> {
                    binding.containerCoins.visibility = View.VISIBLE
                    binding.tvAppTitle.text = "COIN PUSH"
                    binding.clSubAll.visibility = View.VISIBLE
                    binding.ivSearchToggle.visibility = View.VISIBLE
                }
                R.id.navigation_gainers -> {
                    binding.containerMovers.visibility = View.VISIBLE
                    binding.tvAppTitle.text = "MOVERS"
                    binding.clSubAll.visibility = View.GONE
                    binding.ivSearchToggle.visibility = View.GONE
                }
                R.id.navigation_news -> {
                    binding.containerNews.visibility = View.VISIBLE
                    binding.tvAppTitle.text = "NEWS"
                    binding.clSubAll.visibility = View.GONE
                    binding.ivSearchToggle.visibility = View.GONE
                }
                R.id.navigation_latestAlerts -> {
                    binding.containerAlerts.visibility = View.VISIBLE
                    binding.tvAppTitle.text = "ALERTS"
                    binding.clSubAll.visibility = View.GONE
                    binding.ivSearchToggle.visibility = View.GONE
                }
                R.id.navigation_users -> {
                    binding.containerSettings.visibility = View.VISIBLE
                    binding.tvAppTitle.text = "SETTINGS"
                    binding.clSubAll.visibility = View.GONE
                    binding.ivSearchToggle.visibility = View.GONE
                }
            }
            true
        }
    }

    private fun setupMoversSubTabs() {
        binding.btnSubtabGainers.setOnClickListener {
            isGainersSelected = true
            binding.btnSubtabGainers.setTextColor(ContextCompat.getColor(this, R.color.up_text_color))
            binding.btnSubtabLosers.setTextColor(ContextCompat.getColor(this, R.color.passive_gray))
            moversAdapter.updateList(gainersList)
        }

        binding.btnSubtabLosers.setOnClickListener {
            isGainersSelected = false
            binding.btnSubtabLosers.setTextColor(ContextCompat.getColor(this, R.color.down_text_color))
            binding.btnSubtabGainers.setTextColor(ContextCompat.getColor(this, R.color.passive_gray))
            moversAdapter.updateList(losersList)
        }
    }

    private fun setupAlertFilterChips() {
        binding.chipAlertAll.setOnClickListener {
            resetAlertChipStyles()
            binding.chipAlertAll.setBackgroundResource(R.drawable.bg_pill_gold)
            binding.chipAlertAll.setTextColor(ContextCompat.getColor(this, R.color.title_color))
            alertsAdapter.updateList(allAlertsList)
        }

        binding.chipAlertGoingUp.setOnClickListener {
            resetAlertChipStyles()
            binding.chipAlertGoingUp.setBackgroundResource(R.drawable.bg_pill_green)
            binding.chipAlertGoingUp.setTextColor(ContextCompat.getColor(this, R.color.up_text_color))
            val filtered = allAlertsList.filter { it.alertType == "Going up!" }
            alertsAdapter.updateList(filtered)
        }

        binding.chipAlertBreakUp.setOnClickListener {
            resetAlertChipStyles()
            binding.chipAlertBreakUp.setBackgroundResource(R.drawable.bg_pill_green)
            binding.chipAlertBreakUp.setTextColor(ContextCompat.getColor(this, R.color.up_text_color))
            val filtered = allAlertsList.filter { it.alertType == "Breaks up!" }
            alertsAdapter.updateList(filtered)
        }

        binding.chipAlertSqueeze.setOnClickListener {
            resetAlertChipStyles()
            binding.chipAlertSqueeze.setBackgroundResource(R.drawable.bg_pill_gold)
            binding.chipAlertSqueeze.setTextColor(ContextCompat.getColor(this, R.color.title_color))
            val filtered = allAlertsList.filter { it.alertType == "Imminent movement!" }
            alertsAdapter.updateList(filtered)
        }
    }

    private fun resetAlertChipStyles() {
        val gray = ContextCompat.getColor(this, R.color.passive_gray)
        binding.chipAlertAll.setBackgroundResource(R.drawable.bg_card_dark)
        binding.chipAlertAll.setTextColor(gray)
        binding.chipAlertGoingUp.setBackgroundResource(R.drawable.bg_card_dark)
        binding.chipAlertGoingUp.setTextColor(gray)
        binding.chipAlertBreakUp.setBackgroundResource(R.drawable.bg_card_dark)
        binding.chipAlertBreakUp.setTextColor(gray)
        binding.chipAlertSqueeze.setBackgroundResource(R.drawable.bg_card_dark)
        binding.chipAlertSqueeze.setTextColor(gray)
    }

    private fun loadInitialData() {
        binding.pbCoins.visibility = View.VISIBLE
        refreshMarketData()
    }

    private fun refreshMarketData() {
        lifecycleScope.launch {
            try {
                val coins = BybitFeedEngine.fetchMarketTickers()
                if (coins.isNotEmpty()) {
                    allCoinsList.clear()
                    allCoinsList.addAll(coins)

                    // Maintain search filter if active
                    val currentQuery = binding.edtSearch.text.toString()
                    filterCoins(currentQuery)

                    // Update Movers
                    gainersList.clear()
                    gainersList.addAll(BybitFeedEngine.extractGainers(coins))
                    losersList.clear()
                    losersList.addAll(BybitFeedEngine.extractLosers(coins))

                    if (isGainersSelected) {
                        moversAdapter.updateList(gainersList)
                    } else {
                        moversAdapter.updateList(losersList)
                    }

                    // Update Alerts
                    val extractedAlerts = BybitFeedEngine.extractAlertSignals(coins)
                    allAlertsList.clear()
                    allAlertsList.addAll(extractedAlerts)
                    alertsAdapter.updateList(allAlertsList)

                    // Check for active alerts on subscribed coins to fire heads-up notifications & popups
                    checkForActiveAlerts(coins)
                }
            } catch (e: Exception) {
                // Ignore transient network errors
            } finally {
                binding.pbCoins.visibility = View.GONE
                binding.swipeCoins.isRefreshing = false
                binding.swipeMovers.isRefreshing = false
            }
        }
    }

    private fun checkForActiveAlerts(coins: List<CoinPushItem>) {
        val subsPrefs = getSharedPreferences("coin_push_subs", Context.MODE_PRIVATE)
        val alertCoins = coins.filter { it.alertType.isNotEmpty() }

        for (coin in alertCoins) {
            val isSubscribed = subsPrefs.getBoolean("sub_${coin.symbol}", isAllAlertsActive)
            if (isSubscribed) {
                val priceStr = BybitFeedEngine.formatPrice(coin.price)
                val prefix = if (coin.priceChangePercent >= 0) "+" else ""
                val changeStr = "$prefix${String.format(Locale.US, "%.2f", coin.priceChangePercent)}%"

                // Trigger system heads-up notification (subject to cooldown)
                val triggered = AlertNotificationManager.triggerAlertNotification(
                    context = this,
                    symbol = coin.symbol,
                    alertType = coin.alertType,
                    priceFormatted = priceStr,
                    changeFormatted = changeStr
                )

                if (triggered) {
                    showOnScreenAlert(coin)
                    break // Show the most significant alert popup at a time
                }
            }
        }
    }

    private fun startLiveRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(6000) // update live ticker prices every 6 seconds
                try {
                    val freshCoins = BybitFeedEngine.fetchMarketTickers()
                    if (freshCoins.isNotEmpty()) {
                        // Update in-place to avoid disruptive scrolling
                        val freshMap = freshCoins.associateBy { it.symbol }
                        for (item in allCoinsList) {
                            freshMap[item.symbol]?.let { fresh ->
                                item.price = fresh.price
                                item.priceChangePercent = fresh.priceChangePercent
                                item.highPrice = fresh.highPrice
                                item.lowPrice = fresh.lowPrice
                                item.quoteVolume = fresh.quoteVolume
                                item.alertType = fresh.alertType
                            }
                        }
                        coinsAdapter.notifyDataSetChanged()
                        checkForActiveAlerts(freshCoins)
                    }
                } catch (e: Exception) {
                    // Ignore transient errors
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshJob?.cancel()
        popupDismissJob?.cancel()
    }
}
