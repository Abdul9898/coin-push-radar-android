package com.coinpush.radar.model

data class CoinPushItem(
    val symbol: String,
    val baseAsset: String,
    val name: String,
    var price: Double,
    var priceChangePercent: Double,
    var highPrice: Double = 0.0,
    var lowPrice: Double = 0.0,
    var volume: Double = 0.0,
    var quoteVolume: Double = 0.0,
    var isSubscribed: Boolean = true,
    var alertType: String = "",
    var alertTime: String = "",
    var isChartExpanded: Boolean = false,
    var selectedInterval: String = "15m"
)

data class AlertSignalItem(
    val symbol: String,
    val title: String,
    val alertType: String,
    val description: String,
    val price: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val iconRes: Int = 0
)

data class MoverItem(
    val rank: Int,
    val symbol: String,
    val name: String,
    val price: Double,
    val priceChangePercent: Double,
    val quoteVolume: Double
)

data class NewsItem(
    val id: Long,
    val title: String,
    val source: String,
    val time: String,
    val snippet: String,
    val url: String,
    val sentiment: String = "BULLISH"
)
