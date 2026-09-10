package com.selyro.tv.data

import android.content.Context
import com.selyro.tv.model.PlaylistAccount

class AccountStore(context: Context) {
 private val p=context.getSharedPreferences("selyro",Context.MODE_PRIVATE)
 fun save(a:PlaylistAccount)=p.edit().putString("name",a.name).putString("server",a.server.trimEnd('/')).putString("user",a.username).putString("pass",a.password).apply()
 fun load():PlaylistAccount? { val s=p.getString("server",null)?:return null; return PlaylistAccount(p.getString("name","My IPTV")!!,s,p.getString("user","")!!,p.getString("pass","")!!) }
 fun clear()=p.edit().clear().apply()
 fun favorite(id:String,on:Boolean){ val x=p.getStringSet("favorites",emptySet())!!.toMutableSet(); if(on)x.add(id) else x.remove(id); p.edit().putStringSet("favorites",x).apply() }
 fun favorites():Set<String> = p.getStringSet("favorites",emptySet())?:emptySet()
 fun recent(id:String){ val l=(p.getString("recent","")?:"").split('|').filter{it.isNotBlank()&&it!=id}.toMutableList(); l.add(0,id); p.edit().putString("recent",l.take(30).joinToString("|")).apply() }
 fun recents():List<String>=(p.getString("recent","")?:"").split('|').filter{it.isNotBlank()}
}
