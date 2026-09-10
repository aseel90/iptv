package com.selyro.tv.epg

import android.util.Xml
import com.selyro.tv.model.EpgProgram
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

object XmlTvParser{
 fun parse(input:InputStream):List<EpgProgram>{val p=Xml.newPullParser();p.setInput(input,null);val out=mutableListOf<EpgProgram>();var event=p.eventType;while(event!=XmlPullParser.END_DOCUMENT){if(event==XmlPullParser.START_TAG&&p.name=="programme"){val channel=p.getAttributeValue(null,"channel")?:"";val start=time(p.getAttributeValue(null,"start"));val stop=time(p.getAttributeValue(null,"stop"));var title="";var desc:String?=null;val depth=p.depth;while(!(event==XmlPullParser.END_TAG&&p.depth==depth&&p.name=="programme")){event=p.next();if(event==XmlPullParser.START_TAG&&p.name=="title")title=p.nextText();if(event==XmlPullParser.START_TAG&&p.name=="desc")desc=p.nextText()};out+=EpgProgram(channel,title,start,stop,desc)};event=p.next()};return out}
 private fun time(v:String?):Long=runCatching{SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US).parse(v?:"")?.time?:0}.getOrDefault(0)
}
