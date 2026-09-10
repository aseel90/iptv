package com.selyro.tv.player

enum class StreamingProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackBufferMs: Int,
    val rebufferMs: Int
) {
    FAST(
        minBufferMs = 6_000,
        maxBufferMs = 18_000,
        playbackBufferMs = 700,
        rebufferMs = 1_500
    ),
    BALANCED(
        minBufferMs = 12_000,
        maxBufferMs = 35_000,
        playbackBufferMs = 1_200,
        rebufferMs = 2_500
    ),
    STABLE(
        minBufferMs = 25_000,
        maxBufferMs = 60_000,
        playbackBufferMs = 2_500,
        rebufferMs = 5_000
    )
}
