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
- [Samsung Galaxy Z TriFold 사양](https://news.samsung.com/global/introducing-galaxy-z-trifold-the-shape-of-whats-next-in-mobile-innovation): 메인/커버 패널 1–120Hz, 근접·방향 센서.

확인일: 2026-09-10. One UI 8.5는 사용자 제공 환경 정보이며 이 저장소에서 실측한 펌웨어 정보가 아니다.
