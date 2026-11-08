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

object BybitFeedEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cryptoNames = mapOf(
        "BTC" to "Bitcoin",
        "ETH" to "Ethereum",
        "SOL" to "Solana",
        "1000PEPE" to "Pepe",
        "1000BONK" to "Bonk",
        "WIF" to "dogwifhat",
        "SUI" to "Sui Network",
        "DOGE" to "Dogecoin",
        "1000000BABYDOGE" to "Baby Doge Coin",
        "1000FLOKI" to "Floki",
        "1000SHIB" to "Shiba Inu",
        "POPCAT" to "Popcat",
        "GOAT" to "Goatseus Maximus",
        "ACT" to "Act I : The AI Prophecy",
        "PNUT" to "Peanut the Squirrel",
        "MOODENG" to "Moo Deng",
        "HYPE" to "Hyperliquid",
        "XRP" to "Ripple XRP",
        "ADA" to "Cardano",
        "AVAX" to "Avalanche",
        "LINK" to "Chainlink",
        "DOT" to "Polkadot",
        "NEAR" to "Near Protocol",
        "APT" to "Aptos",
        "FET" to "ASI Alliance",
        "RENDER" to "Render Network",
        "TIA" to "Celestia",
        "INJ" to "Injective",
        "TAO" to "Bittensor",
        "SEI" to "Sei Network",
        "TON" to "Toncoin",
        "TRX" to "TRON",
        "JUP" to "Jupiter",
        "PYTH" to "Pyth Network",
        "ENA" to "Ethena",
        "WLD" to "Worldcoin",
        "NOT" to "Notcoin",
        "ORDI" to "Ordinals",
        "RUNE" to "THORChain",
        "LTC" to "Litecoin",
        "BCH" to "Bitcoin Cash",
        "UNI" to "Uniswap",
        "AAVE" to "Aave",
        "BNB" to "BNB Chain",
        "KAS" to "Kaspa",
        "XLM" to "Stellar Lumens",
        "ATOM" to "Cosmos",
        "FIL" to "Filecoin",
        "ICP" to "Internet Computer",
        "AR" to "Arweave",
        "STX" to "Stacks",
        "OP" to "Optimism",
        "ARB" to "Arbitrum",
        "FTM" to "Fantom",
        "SAND" to "The Sandbox",
        "MANA" to "Decentraland",
        "GALA" to "Gala Games"
    )

    suspend fun fetchMarketTickers(): List<CoinPushItem> = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            "https://api.bybit.com/v5/market/tickers?category=linear",
            "https://api.bytick.com/v5/market/tickers?category=linear",
            "https://api.bybit.nl/v5/market/tickers?category=linear"
        )

        for (url in endpoints) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "CoinPushRadar/1.9.7 (Linux; Android)")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val rootObj = JsonParser.parseString(body).asJsonObject
                        val resultObj = rootObj.getAsJsonObject("result") ?: return@use
                        val jsonArray = resultObj.getAsJsonArray("list") ?: return@use
                        val list = mutableListOf<CoinPushItem>()

                        for (i in 0 until jsonArray.size()) {
                            val obj = jsonArray[i].asJsonObject
                            val sym = obj.get("symbol")?.asString ?: continue

                            // Filter only perpetual USDT pairs (exclude quarterly expiry e.g. -25DEC26)
                            if (!sym.endsWith("USDT") || sym.contains("-")) {
                                continue
                            }

                            val base = sym.removeSuffix("USDT")
                            val name = cryptoNames[base] ?: base
                            val price = obj.get("lastPrice")?.asString?.toDoubleOrNull() ?: 0.0
                            if (price <= 0.0) continue

                            val pcnt24hRaw = obj.get("price24hPcnt")?.asString?.toDoubleOrNull() ?: 0.0
                            val change24h = pcnt24hRaw * 100.0

                            val prev1h = obj.get("prevPrice1h")?.asString?.toDoubleOrNull() ?: price
                            val surge1h = if (prev1h > 0.0) ((price - prev1h) / prev1h * 100.0) else 0.0

                            val high = obj.get("highPrice24h")?.asString?.toDoubleOrNull() ?: 0.0
                            val low = obj.get("lowPrice24h")?.asString?.toDoubleOrNull() ?: 0.0
                            val vol = obj.get("volume24h")?.asString?.toDoubleOrNull() ?: 0.0
                            val turnover = obj.get("turnover24h")?.asString?.toDoubleOrNull() ?: 0.0

                            // Accurate Bybit breakout and momentum alert detection
                            var alertType = ""
                            if ((high > 0 && price >= high * 0.996) || surge1h >= 4.0 || change24h >= 12.0) {
                                alertType = "Breaks up!"
                            } else if (surge1h in 1.8..3.99 || change24h in 3.5..11.99) {
                                alertType = "Going up!"
                            } else if (turnover >= 15_000_000 && Math.abs(surge1h) < 0.35 && Math.abs(change24h) < 1.2) {
                                alertType = "Imminent movement!"
                            } else if ((low > 0 && price <= low * 1.004) || surge1h <= -4.0 || change24h <= -12.0) {
                                alertType = "Breaks down!"
                            } else if (surge1h in -3.99..-1.8 || change24h in -11.99..-3.5) {
                                alertType = "Going down!"
                            }

                            list.add(
                                CoinPushItem(
                                    symbol = sym,
                                    baseAsset = base,
                                    name = name,
                                    price = price,
                                    priceChangePercent = change24h,
                                    highPrice = high,
                                    lowPrice = low,
                                    volume = vol,
                                    quoteVolume = turnover,
                                    isSubscribed = true,
                                    alertType = alertType,
                                    alertTime = "Just now"
                                )
                            )
                        }

                        // Default sort by 24h turnover (highest perpetual liquidity first)
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
                    "24h high resistance broken • Bybit Turnover $${formatAmount(coin.quoteVolume)}",
                    R.drawable.break_up
                )
                "Going up!" -> Pair(
                    "Strong momentum +${String.format(Locale.US, "%.2f", coin.priceChangePercent)}% • Bybit Linear Perpetual",
                    R.drawable.going_up
                )
                "Imminent movement!" -> Pair(
                    "Volatility squeeze compression • Massive breakout imminent",
                    R.drawable.squeeze
                )
                "Breaks down!" -> Pair(
                    "Key support level broken • 24h drop ${String.format(Locale.US, "%.2f", coin.priceChangePercent)}%",
                    R.drawable.break_down
                )
                else -> Pair(
                    "Active Bybit Perpetual surge detected",
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
                id = 201,
                title = "Bybit Linear Futures Volume Explodes Across Meme & AI Sector Tokens",
                source = "Bybit Market Pulse",
                time = "5m ago",
                snippet = "High open interest in 1000PEPE, WIF, and SUI signals sustained perpetual liquidity and speculative retail volume.",
                url = "https://www.bybit.com",
                sentiment = "BULLISH"
            ),
            NewsItem(
                id = 202,
                title = "Bitcoin Consolidates Highs as Institutional Derivatives Positioning Hits Multi-Month Peak",
                source = "CoinPush Intelligence",
                time = "18m ago",
                snippet = "Perpetual funding rates remain healthy with minimal long liquidations, indicating steady spot-driven accumulation.",
                url = "https://www.bybit.com",
                sentiment = "BULLISH"
            ),
            NewsItem(
                id = 203,
                title = "Solana Ecosystem Tokens Surge as On-Chain Active Wallets Cross 250M",
                source = "Radar Terminal",
                time = "34m ago",
                snippet = "DeFi protocols and perpetual DEXs report record weekly fee revenues, driving token velocities higher.",
                url = "https://www.bybit.com",
                sentiment = "BULLISH"
            ),
            NewsItem(
                id = 204,
                title = "Sui & Layer-1 Chains Capture Record Daily Share of Cross-Chain Bridge Inflows",
                source = "On-Chain Radar",
                time = "1h ago",
                snippet = "Sub-second finality and high TPS continue to attract major game-fi and institutional liquidity providers.",
                url = "https://www.bybit.com",
                sentiment = "BULLISH"
            ),
            NewsItem(
                id = 205,
                title = "Global Macro Digest: Global Central Banks Ease Liquidity Conditions",
                source = "Macro Wire",
                time = "2h ago",
                snippet = "Broad risk appetite returns to digital assets as rate cut expectations solidify across primary currency benchmarks.",
                url = "https://www.bybit.com",
                sentiment = "NEUTRAL"
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
