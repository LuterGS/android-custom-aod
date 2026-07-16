package dev.lutergs.sgaod.presentation.aod

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lutergs.sgaod.R
import dev.lutergs.sgaod.domain.model.MediaInfo

/**
 * AOD 화면 하단에 표시되는 미디어 컨트롤러
 *
 * ┌─────────────────────────────────────────┐
 * │  [앨범아트]  제목                        │
 * │             아티스트    [◀][▶/⏸][▶▶]   │
 * └─────────────────────────────────────────┘
 *
 * albumArt는 Domain Model에서 분리되어 별도 파라미터로 전달
 */
@Composable
fun MediaControllerSection(
    mediaInfo: MediaInfo,
    albumArt: Bitmap?,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 미디어 정보가 유효하지 않으면 표시하지 않음
    if (!mediaInfo.isValid) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, AodPalette.outline, RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 앨범 아트
            AlbumArt(
                bitmap = albumArt,
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 곡 정보
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = mediaInfo.title.ifEmpty { "알 수 없는 제목" },
                    color = AodPalette.primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = mediaInfo.artist.ifEmpty { "알 수 없는 아티스트" },
                    color = AodPalette.secondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 재생 컨트롤 버튼
            MediaControlButtons(
                isPlaying = mediaInfo.isPlaying,
                onPlayPauseClick = onPlayPauseClick,
                onSkipNextClick = onSkipNextClick,
                onSkipPreviousClick = onSkipPreviousClick
            )
        }
    }
}

@Composable
private fun AlbumArt(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AodPalette.surfaceTint),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "앨범 아트",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                painter = painterResource(id = R.drawable.ic_music_note),
                contentDescription = "기본 음악 아이콘",
                tint = AodPalette.tertiaryText,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun MediaControlButtons(
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 이전 곡 버튼
        IconButton(
            onClick = onSkipPreviousClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_previous),
                contentDescription = "이전 곡",
                tint = AodPalette.primaryText,
                modifier = Modifier.size(24.dp)
            )
        }

        // 재생/일시정지 버튼
        IconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, AodPalette.outline, CircleShape)
        ) {
            Icon(
                painter = painterResource(
                    id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                ),
                contentDescription = if (isPlaying) "일시정지" else "재생",
                tint = AodPalette.primaryText,
                modifier = Modifier.size(28.dp)
            )
        }

        // 다음 곡 버튼
        IconButton(
            onClick = onSkipNextClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_next),
                contentDescription = "다음 곡",
                tint = AodPalette.primaryText,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
