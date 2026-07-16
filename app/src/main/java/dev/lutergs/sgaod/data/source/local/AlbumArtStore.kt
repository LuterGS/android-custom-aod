package dev.lutergs.sgaod.data.source.local

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앨범 아트를 관리하는 Data Layer Store
 * Domain Layer의 순수성을 위해 Android 타입인 Bitmap을 이 클래스에서 관리
 */
@Singleton
class AlbumArtStore @Inject constructor() {

    private val _currentAlbumArt = MutableStateFlow<Bitmap?>(null)
    val currentAlbumArt: StateFlow<Bitmap?> = _currentAlbumArt.asStateFlow()

    fun update(bitmap: Bitmap?) {
        _currentAlbumArt.value = bitmap
    }

    fun clear() {
        _currentAlbumArt.value = null
    }
}
