package com.selyro.tv.iptv

import com.selyro.tv.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class XtreamClient(private val a:PlaylistAccount){
 private val base=a.server.trimEnd('/')
 private suspend fun get(action:String=""):String=withContext(Dispatchers.IO){
  val suffix=if(action.isBlank())"" else "&action=$action"
  val u=URL("$base/player_api.php?username=${enc(a.username)}&password=${enc(a.password)}$suffix")
  val c=u.openConnection() as HttpURLConnection;c.connectTimeout=10000;c.readTimeout=20000;c.setRequestProperty("User-Agent","SelyroTV/1.0");c.inputStream.bufferedReader().use{it.readText()}
 }
 suspend fun authenticate():Boolean=runCatching{JSONObject(get()).optJSONObject("user_info")?.optInt("auth")==1}.getOrDefault(false)
 suspend fun live():List<Channel>{val arr=JSONArray(get("get_live_streams"));return (0 until arr.length()).map{val o=arr.getJSONObject(it);val id=o.optString("stream_id");Channel(id,o.optString("name","Channel"),"$base/live/${a.username}/${a.password}/$id.ts",o.optString("stream_icon").ifBlank{null},o.optString("category_id","Other"),o.optString("epg_channel_id").ifBlank{null})}}
 suspend fun movies():List<VodItem>{val arr=JSONArray(get("get_vod_streams"));return (0 until arr.length()).map{val o=arr.getJSONObject(it);val id=o.optString("stream_id");val ext=o.optString("container_extension","mp4");VodItem(id,o.optString("name","Movie"),"$base/movie/${a.username}/${a.password}/$id.$ext",o.optString("stream_icon").ifBlank{null},o.optString("category_id","Other"),o.optString("plot").ifBlank{null},o.optString("year").ifBlank{null})}}
 suspend fun series():List<SeriesItem>{val arr=JSONArray(get("get_series"));return (0 until arr.length()).map{val o=arr.getJSONObject(it);SeriesItem(o.optString("series_id"),o.optString("name","Series"),o.optString("cover").ifBlank{null},o.optString("category_id","Other"),o.optString("plot").ifBlank{null})}}
 private fun enc(s:String)=java.net.URLEncoder.encode(s,"UTF-8")
}
