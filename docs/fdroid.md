# F-Droid 배포

SG AOD는 기존 GitHub 배포 서명을 유지하는 재현 가능한 빌드 방식으로
F-Droid 공식 저장소 등록을 준비합니다. 이 문서나 메타데이터 파일의 존재가
F-Droid 심사 완료 또는 등록 완료를 의미하지는 않습니다.

## 배포 자료

- `fastlane/metadata/android/en-US/`: 영어 소개, 아이콘, 버전별 변경 이력
- `fastlane/metadata/android/ko-KR/`: 한국어 소개, 아이콘, 버전별 변경 이력
- `fdroid/metadata/dev.lutergs.sgaod.yml`: fdroiddata에 제출할 빌드 설정
- `fdroid/submission.md`: 등록 MR 설명

현재 앱 UI는 한국어입니다. 영어 소개에도 이 제한을 명시했습니다.
`docs/screenshots/`는 v1 화면이라 사용하지 않습니다. 현재 버전의 실제 화면을
촬영한 뒤 각 언어의 `images/phoneScreenshots/`에 추가할 수 있습니다.
`docs/images/`의 디자인 시안은 실제 스크린샷으로 게시하지 않습니다.
앱 아이콘은 저장소의 `icon/play_store_512.png`를 그대로 사용합니다.

## 서명과 빌드

앱 ID는 `dev.lutergs.sgaod`입니다. 기존 배포 APK에서 확인한 서명 인증서의
SHA-256 지문은 다음과 같습니다. 개인 서명 키는 F-Droid에 제공하지 않습니다.

```text
2756a099ee43d26cd81ad2db79e2d4aa00577fcd2e444519afd3911f105eccdd
```

`Binaries`는 버전별 GitHub APK를 가리키고, `AllowedAPKSigningKeys`는 위
인증서로 제한합니다. F-Droid가 소스로 빌드한 APK와 배포 APK의 일치를
검증한 뒤 개발자 서명의 APK를 배포하는 구성입니다. 검증이 실패하면
원인을 해결해야 하며, 서명 유지 설정을 제거해 우회하지 않습니다.

v2.1.1부터 `dependenciesInfo`를 비활성화하여 APK의 불투명한 의존성 정보
블록을 제외합니다. 기존 v2.1 APK나 태그는 교체하지 않습니다.
앱 소개 자료도 릴리스 태그에 포함되어야 하므로 첫 등록은 v2.1.1을 대상으로 합니다.

## 제출 절차

1. 앱 변경 사항을 병합하고 GitHub의 **Release APK** workflow를 실행합니다.
   기존 GitHub Actions secrets로 서명하며 `v<versionName>` 태그와 APK를 생성합니다.
2. 빌드 설정의 `commit`을 해당 태그의 전체 커밋 SHA로 고정합니다.
   `versionName`, `versionCode`, `CurrentVersion`, `CurrentVersionCode`도 확인합니다.
3. GitLab에서 `fdroid/fdroiddata`를 공개 fork하고 별도 브랜치를 만듭니다.
   `fdroid/metadata/dev.lutergs.sgaod.yml`을 fork의
   `metadata/dev.lutergs.sgaod.yml`로 복사합니다. 앱 소개 파일은 앱 저장소에 둡니다.
4. fdroiddata 디렉터리에서 검사합니다.

   ```bash
   fdroid readmeta
   fdroid rewritemeta dev.lutergs.sgaod
   fdroid lint -f dev.lutergs.sgaod
   fdroid checkupdates --allow-dirty dev.lutergs.sgaod
   fdroid build -v -l dev.lutergs.sgaod
   ```

5. fork의 GitLab CI 결과를 확인하고 **New app: SG AOD** MR을 제출합니다.
   `fdroid/submission.md`의 설명과 실제 검사 결과를 사용합니다.
   공식 빌드 환경의 검증·심사가 끝나야 F-Droid에 게시됩니다.

빌드 도구는 AGP 9.3.0, Gradle 9.5.0, SDK 37, JDK 17입니다.
Gradle daemon JDK는 저장소에 고정된 Amazon Corretto 다운로드 설정을 따릅니다.
F-Droid 소스 스캐너가 차단하는 Foojay resolver 플러그인은 빌드 설정의
`prebuild`에서 제거합니다. daemon JDK는 별도로 기록된 다운로드 URL을 사용하므로
이 플러그인 없이도 프로비저닝할 수 있습니다.
APK의 파일 내용이 같아도 JDK·CPU 아키텍처에 따른 ZIP 압축 차이로
재현성 검증에 실패할 수 있으므로 GitHub 배포와 같은 Linux x86_64 환경에서
검증합니다. F-Droid CI에서 이 도구 구성을 사용할 수 있는지도 확인해야 합니다.

## 준비 과정의 검증

2026-09-22 기준:

- [v2.1.1 릴리스](https://github.com/LuterGS/android-custom-aod/releases/tag/v2.1.1) 발행 완료
- [준비 PR #3](https://github.com/LuterGS/android-custom-aod/pull/3) 병합 및 GitHub CI 통과
- 로컬 Debug/Release 빌드, 단위 테스트 64개, Android Lint 통과
- F-Droid 메타데이터 읽기·정규화·Lint·버전 태그 검사 통과
- fdroiddata의 `tools/check-fastlane.py`에서 두 언어 모두 경고·오류 없이 확인
- F-Droid 소스 스캔 통과 (`prebuild` 적용 후)
- 실제 GitHub 배포 APK의 F-Droid 바이너리 스캔 및 기존 서명 일치 확인
- [GitHub x86_64 검증](https://github.com/LuterGS/android-custom-aod/actions/runs/35691033043)에서
  `fdroid build --stop --latest --scan-binary` 통과: 소스 빌드와 배포 APK의 일치,
  허용된 서명 인증서를 모두 확인

`.github/workflows/fdroid.yml`은 제출용 빌드 설정이 바뀌는 PR에서 위 검증을
자동 실행합니다. Actions의 **Verify F-Droid build**에서 수동 실행할 수도 있습니다.
GitHub에서 F-Droid 도구를 실행한 결과이며, 공식 F-Droid 서버의 심사·승인을
대체하지 않습니다.

## 공식 등록 요청 상태

2026-09-22에 [New app: SG AOD !49751](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/49751)을
제출했습니다. 공개 fork는 [LuterGS/fdroiddata](https://gitlab.com/LuterGS/fdroiddata)이며,
`dev.lutergs.sgaod` 브랜치에는 앱 메타데이터 한 파일만 추가했습니다.

초기에는 새 계정의 본인확인 제한으로 GitLab CI를 실행하지 못했지만,
본인확인 완료 후 정상적으로 실행됐습니다. 첫 실행에서 APK 빌드는 통과했고,
`rewritemeta`가 요구한 URL 줄바꿈을 수정해
[파이프라인 2870182811](https://gitlab.com/LuterGS/fdroiddata/-/pipelines/2870182811)에서
다시 검사합니다. 최신 결과는 해당 파이프라인과 MR에서 확인할 수 있습니다.
F-Droid 측 심사와 병합이 필요하며, 아직 F-Droid 저장소에 게시된 상태는 아닙니다.

GitHub 검증도 공식 CI에서 사용한 fdroidserver 커밋과 `ruamel.yaml 0.18.10`으로
고정했습니다. 메타데이터의 `Binaries:` 뒤 공백과 줄바꿈은 이 포맷터가 생성한
정규 형식이므로 수동으로 한 줄로 합치지 않습니다.

배포 APK SHA-256:

```text
3e0629dfb0939b19fd8a5579832bc95f9fc2d2020cd0783c3ec1dfa04fc7cf19
```

## 이후 업데이트

`versionCode`를 늘리고 `versionName`을 변경한 다음, 두 언어의
`changelogs/<versionCode>.txt`를 작성합니다. 변경 이력은 500자 이내입니다.
동일한 서명 키로 GitHub Release를 발행하면 F-Droid가 정식 버전 태그를
감지하도록 설정되어 있습니다. 베타 태그는 자동 업데이트 대상에서 제외합니다.

## 공식 참고 자료

- [등록 정책](https://f-droid.org/docs/Inclusion_Policy/)
- [제출 가이드](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
- [빌드 메타데이터](https://f-droid.org/docs/Build_Metadata_Reference/)
- [재현 가능한 빌드](https://f-droid.org/docs/Reproducible_Builds/)
- [앱 소개와 이미지](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/)
