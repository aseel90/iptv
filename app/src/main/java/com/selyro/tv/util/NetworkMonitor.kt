package com.selyro.tv.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkMonitor{fun bandwidthKbps(c:Context):Int{val cm=c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;val n=cm.activeNetwork?:return 0;return cm.getNetworkCapabilities(n)?.getLinkDownstreamBandwidthKbps()?:0}}
