package com.samhith.aurio.dsp

import kotlin.time.TimeSource

private val timeSource = TimeSource.Monotonic
private val startMark = timeSource.markNow()

actual fun currentNanoTime(): Long = startMark.elapsedNow().inWholeNanoseconds
