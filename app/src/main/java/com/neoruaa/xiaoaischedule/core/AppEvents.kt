package com.neoruaa.xiaoaischedule.core

import kotlinx.coroutines.flow.MutableSharedFlow

object AppEvents {
    val importFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
