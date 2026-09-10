package com.selyro.tv.model

data class PlaylistAccount(val name:String,val server:String,val username:String,val password:String)
data class Channel(val id:String,val name:String,val url:String,val logo:String?=null,val group:String="Other",val epgId:String?=null)
data class VodItem(val id:String,val name:String,val streamUrl:String,val poster:String?=null,val category:String="Other",val plot:String?=null,val year:String?=null)
data class SeriesItem(val id:String,val name:String,val poster:String?=null,val category:String="Other",val plot:String?=null)
data class EpgProgram(val channelId:String,val title:String,val start:Long,val end:Long,val description:String?=null)
