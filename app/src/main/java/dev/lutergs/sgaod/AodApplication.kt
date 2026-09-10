package dev.lutergs.sgaod

import android.app.Application
import android.content.Context
import dev.lutergs.sgaod.data.AodStore

class AodApplication : Application() {
    val store by lazy { AodStore(this) }
}
val Context.aodStore: AodStore get() = (applicationContext as AodApplication).store
