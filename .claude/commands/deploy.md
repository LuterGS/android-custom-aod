---
allowed-tools:
  - Bash
---

# Android 앱 빌드 및 설치

다음 단계를 순서대로 실행하세요:

## 1. 앱 빌드
```bash
cd /Users/luther.lee/Documents/selfDevelop/custom-aod && ./gradlew assembleDebug
```

## 2. ADB로 설치
```bash
ADB="/Users/luther.lee/Library/Android/sdk/platform-tools/adb"
$ADB install -r /Users/luther.lee/Documents/selfDevelop/custom-aod/app/build/outputs/apk/debug/app-debug.apk
```

앱 설치를 할 때, ADB 디바이스가 여러 대일 경우, 실물 장비를 우선으로 설치하세요.
빌드 또는 설치 중 오류가 발생하면 사용자에게 알려주세요.
성공하면 "✅ 앱 설치 완료"라고 알려주세요.
