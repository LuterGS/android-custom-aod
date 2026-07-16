## custom-aod

This is Android application's repository.

This APP is custom AOD app.

## Architecture

이 프로젝트는 Android Clean Architecture를 따릅니다.

```
app/src/main/java/dev/lutergs/sgaod/
├── data/                    # Data Layer
│   ├── repository/          # Repository 구현체
│   └── source/local/        # DataStore 등 로컬 데이터 소스
├── domain/                  # Domain Layer
│   ├── model/               # 도메인 모델
│   ├── repository/          # Repository 인터페이스
│   └── usecase/             # UseCase (비즈니스 로직)
├── presentation/            # Presentation Layer
│   ├── aod/                 # AOD 화면 (ViewModel, Composable)
│   ├── main/                # 메인/설정 화면
│   └── theme/               # Compose 테마
├── service/                 # Android Service
└── util/                    # 유틸리티
```

### 의존성 방향
- Presentation → Domain ← Data
- Domain Layer는 다른 레이어에 의존하지 않음

### 안드로이드 및 언어, 의존성 버전
- 현재 모두 최신 (Android 16) 기준으로 맞추어져 있고, 작업 시에는 웹 검색을 통해 무조건 최신 아키텍쳐에 호환되도록 코드를 작성할 것.

### 작업 절차
- Plan / Execute 시에 가용한 모든 sub-agent 를 활용해서 체계적인 계획과 실행을 하도록 하고, 적극적으로 웹 검색을 통해 레퍼런스를 모을 것.
