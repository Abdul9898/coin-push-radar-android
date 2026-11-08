package com.coinpush.radar

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.coinpush.radar.adapter.AlertsAdapter
import com.coinpush.radar.adapter.CoinPushAdapter
import com.coinpush.radar.adapter.MoversAdapter
import com.coinpush.radar.adapter.NewsAdapter
import com.coinpush.radar.databinding.ActivityMainBinding
import com.coinpush.radar.engine.BinanceFeedEngine
import com.coinpush.radar.model.AlertSignalItem
import com.coinpush.radar.model.CoinPushItem
import com.coinpush.radar.model.MoverItem
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("coin_push_radar_settings", Context.MODE_PRIVATE)
        isAllAlertsActive = prefs.getBoolean("all_alerts_active", true)

        setupRecyclerViews()
        setupBottomNav()
        setupTopBar()
        setupMoversSubTabs()
        setupAlertFilterChips()
        loadInitialData()
        startLiveRefreshLoop()
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

        // 2. Movers RecyclerView
        moversAdapter = MoversAdapter(this, mutableListOf())
        binding.moversRV.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = moversAdapter
        }

        // 3. News RecyclerView
        newsAdapter = NewsAdapter(this, BinanceFeedEngine.getNewsFeed().toMutableList())
        binding.newsRV.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = newsAdapter
        }

        // 4. Alerts RecyclerView
        alertsAdapter = AlertsAdapter(this, mutableListOf())
        binding.alertsRV.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = alertsAdapter
        }

        // SwipeRefresh listeners
        binding.swipeCoins.setOnRefreshListener { refreshMarketData() }
        binding.swipeMovers.setOnRefreshListener { refreshMarketData() }
        binding.swipeNews.setOnRefreshListener {
            newsAdapter.updateList(BinanceFeedEngine.getNewsFeed())
            binding.swipeNews.isRefreshing = false
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
                "🔓 All 770+ coin alerts activated! (Unrestricted VIP)"
            } else {
                "All alerts deactivated"
            }
            Toast.makeText(this, statusMsg, Toast.LENGTH_SHORT).show()
        }

        // VIP Badge click info
        binding.tvVipBadge.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Coin Push Radar VIP 🔓")
                .setMessage("VIP status is permanently unlocked:\n\n• Unlimited coin alerts (3-coin limit removed)\n• Master Activate All toggle active\n• Live 1s / 1m / 15m / 1h candlestick charts\n• Zero subscription fees forever!")
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
                val coins = BinanceFeedEngine.fetchMarketTickers()
                if (coins.isNotEmpty()) {
                    allCoinsList.clear()
                    allCoinsList.addAll(coins)

                    // Maintain search filter if active
                    val currentQuery = binding.edtSearch.text.toString()
                    filterCoins(currentQuery)

                    // Update Movers
                    gainersList.clear()
                    gainersList.addAll(BinanceFeedEngine.extractGainers(coins))
                    losersList.clear()
                    losersList.addAll(BinanceFeedEngine.extractLosers(coins))

                    if (isGainersSelected) {
                        moversAdapter.updateList(gainersList)
                    } else {
                        moversAdapter.updateList(losersList)
                    }

                    // Update Alerts
                    val extractedAlerts = BinanceFeedEngine.extractAlertSignals(coins)
                    allAlertsList.clear()
                    allAlertsList.addAll(extractedAlerts)
                    alertsAdapter.updateList(allAlertsList)
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

    private fun startLiveRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(6000) // update live ticker prices every 6 seconds
                try {
                    val freshCoins = BinanceFeedEngine.fetchMarketTickers()
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
    }
}
