# Add project specific ProGuard rules here.
#
# Hilt / Dagger / Compose / DataStore 는 consumer rules 를 자체 제공하므로
# 여기에는 프로젝트 고유 규칙만 둔다.

# 디버깅 가능한 스택트레이스 유지
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# NotificationListenerService 등 매니페스트 등록 컴포넌트는 AGP 가 자동 keep

# skydoves colorpicker-compose (consumer rules 미제공 대비)
-keep class com.github.skydoves.colorpicker.compose.** { *; }

# Kotlin 코루틴 내부 필드 경고 억제
-dontwarn kotlinx.coroutines.**
