# custom-aod v2

Android 커스텀 AOD. Kotlin + Android 플랫폼 API를 사용한다.

- domain: Android와 무관한 순수 정책·모델. data/presentation/service에 의존하지 않는다.
- data: 메모리 상태·설정, 센서와 미디어 어댑터.
- service/receiver: Android 진입점과 AOD 세션의 자원 수명.
- presentation: 설정 Activity와 이벤트 기반 Surface 렌더러.

전력 관련 변경은 `docs/design.md`와 `docs/validation.md`를 함께 확인한다.
물리 패널 Hz와 앱 콘텐츠 FPS를 혼동하지 않는다. CPU WakeLock·초 단위 반복 루프·잠금 폴링을 추가하지 않는다.
알림 공개 정책은 보수적으로 적용하고 테스트한다. 실제 기기에서 실행하지 않은 검증을 완료라고 표기하지 않는다.

검증: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`.
