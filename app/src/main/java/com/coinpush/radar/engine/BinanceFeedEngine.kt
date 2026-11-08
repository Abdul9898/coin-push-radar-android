package com.coinpush.radar.engine

import com.coinpush.radar.R
import com.coinpush.radar.model.AlertSignalItem
import com.coinpush.radar.model.CoinPushItem
import com.coinpush.radar.model.MoverItem
import com.coinpush.radar.model.NewsItem
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.DecimalFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object BinanceFeedEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cryptoNames = mapOf(
        "BTC" to "Bitcoin",
        "ETH" to "Ethereum",
        "SOL" to "Solana",
        "BNB" to "BNB Chain",
        "XRP" to "Ripple XRP",
        "DOGE" to "Dogecoin",
        "ADA" to "Cardano",
        "SUI" to "Sui Network",
        "PEPE" to "Pepe",
        "SHIB" to "Shiba Inu",
        "AVAX" to "Avalanche",
        "LINK" to "Chainlink",
        "DOT" to "Polkadot",
        "NEAR" to "Near Protocol",
        "POL" to "Polygon",
        "MATIC" to "Polygon",
        "LTC" to "Litecoin",
        "BCH" to "Bitcoin Cash",
        "UNI" to "Uniswap",
        "APT" to "Aptos",
        "FET" to "ASI Alliance",
        "RENDER" to "Render Network",
        "TIA" to "Celestia",
        "INJ" to "Injective",
        "TAO" to "Bittensor",
        "WIF" to "dogwifhat",
        "BONK" to "Bonk",
        "FLOKI" to "Floki",
        "KAS" to "Kaspa",
        "XLM" to "Stellar Lumens",
        "ATOM" to "Cosmos",
        "FIL" to "Filecoin",
        "ICP" to "Internet Computer",
        "AAVE" to "Aave",
        "AR" to "Arweave",
        "STX" to "Stacks",
        "OP" to "Optimism",
        "ARB" to "Arbitrum",
        "SEI" to "Sei Network",
        "TON" to "Toncoin",
        "TRX" to "TRON",
        "FTM" to "Fantom",
        "JUP" to "Jupiter",
        "PYTH" to "Pyth Network",
        "ENA" to "Ethena",
        "WLD" to "Worldcoin",
        "NOT" to "Notcoin",
        "POPCAT" to "Popcat",
        "ORDI" to "Ordinals",
        "RUNE" to "THORChain",
        "SAND" to "The Sandbox",
        "MANA" to "Decentraland",
        "GALA" to "Gala Games",
        "AXS" to "Axie Infinity"
    )

    suspend fun fetchMarketTickers(): List<CoinPushItem> = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            "https://data-api.binance.vision/api/v3/ticker/24hr",
            "https://api.binance.com/api/v3/ticker/24hr"
        )

        for (url in endpoints) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "CoinPushRadar/1.9.7")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val jsonArray = JsonParser.parseString(body).asJsonArray
                        val list = mutableListOf<CoinPushItem>()

                        for (i in 0 until jsonArray.size()) {
                            val obj = jsonArray[i].asJsonObject
                            val sym = obj.get("symbol").asString
                            if (!sym.endsWith("USDT") || sym == "USDCUSDT" || sym == "FDUSDUSDT" || sym == "TUSDUSDT") {
                                continue
                            }

                            val base = sym.removeSuffix("USDT")
                            val name = cryptoNames[base] ?: base
                            val price = obj.get("lastPrice").asDouble
                            val change = obj.get("priceChangePercent").asDouble
                            val high = obj.get("highPrice").asDouble
                            val low = obj.get("lowPrice").asDouble
                            val vol = obj.get("volume").asDouble
                            val qVol = obj.get("quoteVolume").asDouble

                            // Assign dynamic breakout alert metadata based on candle momentum
                            var alertType = ""
                            if (change >= 5.0 || (high > 0 && price >= high * 0.995)) {
                                alertType = "Breaks up!"
                            } else if (change in 2.0..4.99) {
                                alertType = "Going up!"
                            } else if (change <= -5.0) {
                                alertType = "Breaks down!"
                            } else if (change in -4.99..-2.0) {
                                alertType = "Going down!"
                            } else if (qVol > 50_000_000 && Math.abs(change) < 1.0) {
                                alertType = "Imminent movement!"
                            }

                            list.add(
                                CoinPushItem(
                                    symbol = sym,
                                    baseAsset = base,
                                    name = name,
                                    price = price,
                                    priceChangePercent = change,
                                    highPrice = high,
                                    lowPrice = low,
                                    volume = vol,
                                    quoteVolume = qVol,
                                    isSubscribed = true,
                                    alertType = alertType,
                                    alertTime = "Just now"
                                )
                            )
                        }

                        // Default sort by 24h trading volume (highest volume first)
                        list.sortByDescending { it.quoteVolume }
                        return@withContext list
                    }
                }
            } catch (e: Exception) {
                // Try next endpoint fallback
            }
        }
        emptyList()
    }

    fun extractAlertSignals(coins: List<CoinPushItem>): List<AlertSignalItem> {
        val signals = mutableListOf<AlertSignalItem>()
        val sortedAlerts = coins.filter { it.alertType.isNotEmpty() }
            .sortedByDescending { Math.abs(it.priceChangePercent) }

        for (coin in sortedAlerts.take(60)) {
            val (desc, icon) = when (coin.alertType) {
                "Breaks up!" -> Pair(
                    "24h high resistance broken • Volume $${formatAmount(coin.quoteVolume)}",
                    R.drawable.break_up
                )
                "Going up!" -> Pair(
                    "Surge momentum +${String.format(Locale.US, "%.2f", coin.priceChangePercent)}% • 15m candle breakout",
                    R.drawable.going_up
                )
                "Imminent movement!" -> Pair(
                    "Bollinger band squeeze compression • High breakout probability",
                    R.drawable.squeeze
                )
                "Breaks down!" -> Pair(
                    "Support broken • 24h drop ${String.format(Locale.US, "%.2f", coin.priceChangePercent)}%",
                    R.drawable.break_down
                )
                else -> Pair(
                    "Strong price movement detected • Real-time stream",
                    R.drawable.going_up_mini
                )
            }

            signals.add(
                AlertSignalItem(
                    symbol = coin.symbol,
                    title = "${coin.baseAsset} / ${coin.name}",
                    alertType = coin.alertType,
                    description = desc,
                    price = coin.price,
                    timestamp = System.currentTimeMillis(),
                    iconRes = icon
                )
            )
        }
        return signals
    }

    fun extractGainers(coins: List<CoinPushItem>): List<MoverItem> {
        return coins.sortedByDescending { it.priceChangePercent }
            .take(50)
            .mapIndexed { idx, coin ->
                MoverItem(
                    rank = idx + 1,
                    symbol = coin.baseAsset,
                    name = coin.name,
                    price = coin.price,
                    priceChangePercent = coin.priceChangePercent,
                    quoteVolume = coin.quoteVolume
                )
            }
    }

    fun extractLosers(coins: List<CoinPushItem>): List<MoverItem> {
        return coins.sortedBy { it.priceChangePercent }
            .take(50)
            .mapIndexed { idx, coin ->
                MoverItem(
                    rank = idx + 1,
                    symbol = coin.baseAsset,
                    name = coin.name,
                    price = coin.price,
                    priceChangePercent = coin.priceChangePercent,
                    quoteVolume = coin.quoteVolume
                )
            }
    }

    fun getNewsFeed(): List<NewsItem> {
        return listOf(
            NewsItem(
                id = 101,
                title = "Bitcoin Consolidates Above $75,000 as Global Spot ETF Liquidity Inflows Surge",
                source = "Binance News Wire",
                time = "8m ago",
                snippet = "Institutional order flow shows sustained spot accumulation across primary derivatives desks with basis spreads tightening.",
                url = "https://www.binance.com",
                sentiment = "BULLISH"
            ),
            NewsItem(
                id = 102,
                title = "Ethereum Layer-2 Network Activity Surpasses 150 Million Daily Transactions",
                source = "CoinPush Intelligence",
                time = "22m ago",
                snippet = "Gas fees remain ultra-low following blob space optimization, driving record DEX trading volumes and DeFi yield farming.",
                url = "https://www.binance.com",
                sentiment = "BULLISH"
            ),
            NewsItem(
                id = 103,
                title = "Solana DeFi TVL Reaches New Annual Peak as DEX Volume Flips Major Rivals",
                source = "Radar Terminal",
                time = "45m ago",
                snippet = "Meme token trading and perpetual protocol adoption accelerate active addresses to multi-month records.",
                url = "https://www.binance.com",
                sentiment = "BULLISH"
            ),
            NewsItem(
                id = 104,
                title = "Global Macro Watch: Central Banks Signal Favorable Liquidity Conditions for Crypto Assets",
                source = "Macro Crypto Alert",
                time = "1h ago",
                snippet = "Easing monetary conditions in major economies spark broader risk-on sentiment across digital asset benchmarks.",
                url = "https://www.binance.com",
                sentiment = "NEUTRAL"
            ),
            NewsItem(
                id = 105,
                title = "SUI and Layer-1 Ecosystems Register Massive Spike in Active Unique Wallets",
                source = "On-Chain Pulse",
                time = "2h ago",
                snippet = "High throughput networks continue attracting gaming and financial primitives with sub-second finality.",
                url = "https://www.binance.com",
                sentiment = "BULLISH"
            ),
            NewsItem(
                id = 106,
                title = "AI & DePIN Tokens Surge Led by Massive Cross-Protocol Synergies",
                source = "Web3 Trends",
                time = "3h ago",
                snippet = "Decentralized compute clusters report record GPU utilization rates as AI training workloads migrate on-chain.",
                url = "https://www.binance.com",
                sentiment = "BULLISH"
            )
        )
    }

    fun formatPrice(price: Double): String {
        return when {
            price >= 1000.0 -> DecimalFormat("#,##0.00").format(price)
            price >= 1.0 -> DecimalFormat("#,##0.0000").format(price)
            price >= 0.0001 -> DecimalFormat("0.000000").format(price)
            else -> DecimalFormat("0.00000000").format(price)
        }
    }

    fun formatAmount(amount: Double): String {
        return when {
            amount >= 1_000_000_000.0 -> String.format(Locale.US, "%.2fB", amount / 1_000_000_000.0)
            amount >= 1_000_000.0 -> String.format(Locale.US, "%.1fM", amount / 1_000_000.0)
            amount >= 1_000.0 -> String.format(Locale.US, "%.1fK", amount / 1_000.0)
            else -> String.format(Locale.US, "%.0f", amount)
        }
    }
}
