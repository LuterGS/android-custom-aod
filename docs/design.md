# v2 설계

## 범위와 보장 경계

요청된 시계·앱 알림·충전·음악 상태와 절전을 중심으로 앱 소스를 다시 작성한다.
1Hz를 반복 그리기 타이머로 구현하지 않는다. 변경 없을 때는 새 버퍼를 제출하지 않는 것이 더 적은 작업이다.
앱의 콘텐츠 버퍼 제출을 최대 1fps로 제한하고, 디스플레이에는 1Hz를 요청한다.
공개 API의 요청을 패널 동작의 보장으로 표기하지 않는다.

대상 기기는 사용자가 지정한 Galaxy Z TriFold / Android 16 / One UI 8.5.
삼성 발표 사양에서 두 패널의 가변 주사율은 1–120Hz이다. 펌웨어가 서드파티 Activity에서도
1Hz를 적용하는지, 어떤 밝기에서 적용하는지는 사양표만으로 확인할 수 없다.

## 상태와 자원 수명

화면 OFF 방송에서 세션을 시작하고 센서 초기값을 기다린다. 잠금 상태이고 차단 조건이 없으면
기본 디스플레이에 Activity를 띄운다. 새로 표시할 세션마다 ID를 부여해 이전 Activity가
새 세션을 종료하지 않도록 한다. 프레임레이트 변경으로 발생하는 DisplayListener 이벤트를
전원 버튼으로 오인하지 않도록 해당 콜백으로 화면 꺼짐을 판정하지 않는다.

세션 중 근접·뒤집힘·절전·저전력·발열·시간 제한을 단일 정책으로 계산한다.
여러 차단 조건이 있으면 하나가 해제되어도 나머지가 화면 복귀를 막는다.
가림 소등과 Activity 정지 콜백의 도착 순서 차이는 짧은 일회성 확인으로 처리한다.
표시가 중단된 동안 프레임/분 타이머와 미디어 세션 콜백을 유지하지 않는다.

밝기 모드는 `SCREEN_BRIGHTNESS_MODE`의 변경을 ContentObserver로 관찰한다.
자동 밝기가 켜져 있으면 Window 밝기 override를 -1로 해제하여 Android의 조도 센서와 밝기 곡선을
그대로 사용한다. 앱이 조도를 별도로 반복 측정하지 않는다. 자동 모드를 끄면 저장된 수동 밝기로
복귀한다. 암전 상태의 최소 밝기(0)가 항상 우선하며, Activity 정지 시 모드 관찰도 해제한다.

암전 중에는 앱의 터치 이벤트를 소비하여 아래 잠금화면으로 전달하지 않는다.
진행 중인 GestureDetector에는 CANCEL을 보내고 폐기한다. 복귀 후 DOWN부터 새 터치를 허용한다.
이 처리는 앱 제스처에 대한 것이며 OS의 전원 버튼·잠금화면 제스처 설정을 변경하지 않는다.

잠금 해제는 USER_PRESENT와 Activity 진입/포커스 검사를 사용한다. 250ms 잠금 폴링은 없다.
배터리 상태는 sticky 시스템 방송, 음악은 MediaSession 콜백, 알림은 NotificationListener 콜백이다.
앱이 비활성화되어도 시스템이 바인드한 알림 리스너 자체는 있을 수 있으나 알림 목록은 비운다.
서비스 종료 시 센서·방송·전화·발열·알람·Handler·근접 WakeLock을 해제한다.

검정 화면과 화면 소등을 구분한다. 근접 WakeLock은 지원되는 기기에서 OS의 근접 소등 경로를 사용한다.
이 잠금은 CPU 유지용 PARTIAL_WAKE_LOCK이 아니다. 뒤집힘이나 시간 제한으로 검정 표시 시에는
화면 유지 플래그를 해제하여 시스템이 정상적으로 잠들게 한다. 디바이스 관리자·접근성 권한을
추가 요구하거나 비공개 goToSleep/Doze API를 호출하지 않는다.

## 화면과 개인정보

Surface의 내용은 시각적으로 정적이다. 진행률, 애니메이션, 앨범 사진, 벽지, 컬러 이미지가 없다.
1분 단위로 7×7 물리 픽셀 범위에서 위치를 이동한다. 표시는 화면 크기를 기준으로 축소하고
넓은 내부 화면에서도 가로 콘텐츠 폭을 제한한다. Surface가 바뀌면 1Hz 힌트를 다시 설정한다.
픽셀 이동은 스케일 변환 전에 적용하여 밀도와 관계없이 각 축 ±3px를 유지한다. 동일 분 안에서는
위치가 같고 49분 동안 49개 위치를 순환한다. 암전 중에는 이동용 타이머도 중단한다.
번인을 완전히 예방하는 보장은 아니며, 낮은 발광량과 표시 시간 제한을 함께 사용한다.

### AOD 시각 구성

Apple의 시계 중심 잠금화면과 Samsung의 간결한 AOD 알림 표현을 참고한다.
검정 바탕에 큰 저채도 시계, 작은 현지화 날짜, 배터리 윤곽 아이콘을 배치한다.
음악은 얇은 구분선 아래에 제목·아티스트와 정적인 재생/일시정지 기호로 표시한다.
재생 중·충전 중에만 작은 영역에 차분한 녹색을 사용한다.

알림 내용이 숨겨진 상태에서는 앱별로 묶어 단색 아이콘·이름·개수만 최대 4행 표시한다.
공개 가능한 내용이 있으면 최신 3개 알림의 앱 이름·제목·본문을 서로 다른 밝기와 크기로 표시한다.
나머지 개수는 아래에 요약한다. 긴 글은 한 줄 말줄임으로 처리하고, 높이가 부족한 화면에서는
전체 구성을 축소하여 알림이나 음악이 화면 밖으로 밀리지 않도록 한다.
아이콘은 알림에 포함된 작은 로컬 리소스/비트맵만 사용하고, 원격/URI 아이콘은 읽지 않는다.
표시용 아이콘 캐시는 최대 12개이며 숨김/종료 때 해제한다.

설정의 **AOD 미리보기**는 실제 `AodRenderer`를 예시 데이터로 그린다.
내용 표시·음악 표시를 전환할 수 있고 AOD 활성화나 알림 권한 없이 사용할 수 있다.
[구성 시안](images/aod-design.png)은 레이아웃 설명용 이미지이며 실기기 스크린샷이 아니다.
폰트와 앱 아이콘의 최종 모양은 Android 및 알림 제공 앱에 따라 달라진다.

### 앱 이름 조회

런처 앱의 이름을 읽을 수 있도록 `queries`에 MAIN/LAUNCHER를 선언한다.
알림 발신 패키지의 `ApplicationInfo`로 현지화된 정식 레이블을 조회한다.
조회할 수 없으면 알림의 선택적 `android.appInfo` 메타데이터에서 발신 패키지·UID가 일치하는
정보를 사용해 다시 조회한다. 이 메타데이터는 AOSP 구현 세부사항이므로 존재를 가정하지 않는다.
두 경로 모두 실패하거나 레이블이 패키지명 자체이면 중립적인 “앱 알림”을 표시한다.
제외 앱 목록에도 같은 이름 조회를 적용하며 패키지명을 화면 표시용 대체값으로 사용하지 않는다.

알림 내용은 opt-in이다. 잠금화면 전역 설정을 읽지 못하면 내용을 노출하지 않는다.
랭킹 → 채널 → 알림 순서의 공개 범위로 secret 알림은 제외한다.
메시지 원문은 공개가 허용될 때만 표시 저장소에 복사한다. 알림 그룹 요약은 자식이 있을 때 제외한다.

## 참고한 공식 자료

- [Android frame rate API](https://developer.android.com/media/optimize/performance/frame-rate): 요청은 시스템에 대한 힌트이며 선택은 플랫폼이 한다.
- [Surface](https://developer.android.com/reference/android/view/Surface): setFrameRate와 seamless 전환.
- [PowerManager](https://developer.android.com/reference/android/os/PowerManager): PROXIMITY_SCREEN_OFF_WAKE_LOCK와 지원 여부 검사.
- [AOSP SystemUI Doze](https://android.googlesource.com/platform/frameworks/base/+/main/packages/SystemUI/docs/device-entry/doze.md): 시스템 AOD의 Doze와 일반 Activity의 차이.
- [Android sensor overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview): 필요할 때만 센서 등록, foreground service 범위.
- [Apple Always-On 설명](https://support.apple.com/en-ie/guide/iphone/iph7117338a8/ios): 가려짐·뒤집힘·절전·취침 시 화면을 끄는 정책 참고.
- [Samsung 기본 AOD 꾸미기](https://www.samsung.com/ae/support/mobile-devices/how-to-customize-your-galaxy-phone-always-on-display/): 시계·날짜·알림의 간결한 구성 참고.
- [Android 패키지 조회 범위 선언](https://developer.android.com/training/package-visibility/declaring): 런처 앱 레이블 조회 범위.
- [Window 밝기 override](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness): 자동 모드에서는 시스템 밝기에 위임.
- [시스템 자동 밝기 모드](https://developer.android.com/reference/android/provider/Settings.System#SCREEN_BRIGHTNESS_MODE): 설정 변경 감시.
- [AOSP Notification](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/Notification.java): 선택적인 발신 앱 정보 메타데이터.
- [Samsung Galaxy Z TriFold 사양](https://news.samsung.com/global/introducing-galaxy-z-trifold-the-shape-of-whats-next-in-mobile-innovation): 메인/커버 패널 1–120Hz, 근접·방향 센서.

확인일: 2026-09-10. One UI 8.5는 사용자 제공 환경 정보이며 이 저장소에서 실측한 펌웨어 정보가 아니다.
