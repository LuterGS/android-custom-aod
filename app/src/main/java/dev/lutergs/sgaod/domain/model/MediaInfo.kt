package dev.lutergs.sgaod.domain.model

/**
 * 현재 재생 중인 미디어 정보를 나타내는 모델
 *
 * Clean Architecture 준수를 위해 Android Framework 타입을 포함하지 않음
 * albumArt(Bitmap)는 Data Layer의 AlbumArtStore에서 조회
 */
data class MediaInfo(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val packageName: String,
    val duration: Long,
    val position: Long
) {
    companion object {
        fun empty(): MediaInfo = MediaInfo(
            title = "",
            artist = "",
            isPlaying = false,
            packageName = "",
            duration = 0L,
            position = 0L
        )
    }

    /**
     * 미디어 정보가 유효한지 확인 (재생 중인 미디어가 있는지)
     */
    val isValid: Boolean
        get() = title.isNotEmpty() || artist.isNotEmpty()
}
