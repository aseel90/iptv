package com.selyro.tv.player

enum class StreamingProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackBufferMs: Int,
    val rebufferMs: Int
) {
    FAST(5_000, 15_000, 650, 1_200),
    BALANCED(10_000, 30_000, 1_000, 2_000),
    STABLE(20_000, 50_000, 2_000, 4_000)
}
