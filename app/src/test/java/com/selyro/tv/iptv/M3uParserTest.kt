package com.selyro.tv.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    @Test
    fun parsesMetadataAndChannels() {
        val input = "\uFEFF#EXTM3U x-tvg-url=\"https://epg.example/guide.xml\"\n" +
            "#EXTINF:-1 tvg-id=\"bbc.one\" tvg-logo=\"https://img/logo.png\" group-title=\"News\" tvg-chno=\"12\",BBC One\n" +
            "https://stream.example/live.m3u8\n" +
            "#EXTINF:-1 group-title='Sports',Sports HD\n" +
            "http://stream.example/2.ts\n"
        val playlist = M3uParser.parsePlaylist(input)
        assertEquals("https://epg.example/guide.xml", playlist.epgUrl)
        assertEquals(2, playlist.channels.size)
        assertEquals("BBC One", playlist.channels[0].name)
        assertEquals("News", playlist.channels[0].group)
        assertEquals(12, playlist.channels[0].number)
        assertEquals("Sports", playlist.channels[1].group)
        assertTrue(playlist.channels[1].id.startsWith("m3u-"))
    }

    @Test
    fun ignoresCommentsAndInvalidLines() {
        val input = "#EXTM3U\n# comment\nnot-a-stream\n#EXTINF:-1,Valid\nhttps://example.com/live.ts\n"
        val channels = M3uParser.parse(input)
        assertEquals(1, channels.size)
        assertEquals("Valid", channels.single().name)
    }
}
