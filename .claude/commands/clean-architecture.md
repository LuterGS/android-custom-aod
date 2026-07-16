---
description: 클린 아키텍처 원칙에 따라 코드베이스를 분석하고 리팩터링 제안
---

# Android Clean Architecture 분석 및 리팩터링

이 프로젝트의 클린 아키텍처 준수 여부를 분석하고, 위반 사항이 있으면 리팩터링을 제안해주세요.

## 프로젝트 구조

```
app/src/main/java/com/example/customaod/
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

## 분석 항목

### 1. 의존성 규칙 검증
- **Presentation → Domain**: ViewModel이 UseCase만 의존하는지 확인
- **Data → Domain**: Repository 구현체가 Domain의 인터페이스를 구현하는지 확인
- **Domain Layer 독립성**: Domain이 Android Framework나 다른 레이어에 의존하지 않는지 확인

### 2. 레이어별 책임 분리
- **Data Layer**: 데이터 소스 접근, Repository 구현
- **Domain Layer**: 비즈니스 로직(UseCase), 도메인 모델, Repository 인터페이스
- **Presentation Layer**: UI 로직, ViewModel, Composable

### 3. UseCase 패턴
- 각 UseCase가 단일 책임을 가지는지 확인
- UseCase가 Repository 인터페이스에만 의존하는지 확인

### 4. DI (Dependency Injection)
- Hilt 모듈이 올바르게 구성되어 있는지 확인
- 인터페이스와 구현체 바인딩이 적절한지 확인

## 분석 수행

1. 각 레이어의 import 문을 분석하여 의존성 규칙 위반 찾기
2. 잘못된 위치에 있는 클래스 식별
3. 누락된 추상화(인터페이스) 찾기
4. 개선이 필요한 UseCase 패턴 식별

## 출력 형식

### 분석 결과
- ✅ 준수 사항
- ⚠️ 경고 (권장 개선)
- ❌ 위반 사항 (필수 수정)

### 리팩터링 제안
위반 사항이 있으면 구체적인 리팩터링 방법을 제안해주세요.

---

분석을 시작해주세요.
