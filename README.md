# CollectBid

수집품의 일반 경매와 역경매를 지원하는 **포트폴리오용 백엔드 MVP**입니다.

현재는 **JPA + QueryDSL JPA 기반 모듈러 모놀리스**입니다. 회원 가입부터 상품 등록, 입찰·판매 제안,
낙찰과 주문 생성까지 동작합니다. 이후 성능 측정, 실시간 통신, 이벤트 신뢰성, 선택적 MSA 전환을
단계별로 개선할 수 있도록 설계 결정과 테스트를 함께 관리합니다.

## 구현 범위

| 영역 | 현재 구현 |
| --- | --- |
| 회원·인증 | 가입, 로그인, 2시간 Bearer 토큰, 로그아웃, 본인 프로필 |
| 상품 | 등록·수정·논리 삭제, 카테고리·키워드 검색, 소유권·출품 상태 검사 |
| 일반 경매 | 생성, 입찰, 최소 증가액, 이력, 종료·유찰·낙찰·취소 |
| 역경매 | 구매 요청, 판매자별 제안, 구매자 선택, 상품 상태 재검증 |
| 정합성 | PostgreSQL 행 잠금, 입찰·제안 요청 키, 중복 출품·주문 제약 |
| 검색 | QueryDSL 조건 검색, 가격·카테고리·유형 필터, 정렬·페이징 |
| 주문 | 낙찰 시 자동 생성, 거래 당사자만 조회 |
| 알림 | 최고가 변경·거래 성립 인앱 알림, 본인 목록·읽음 처리 |
| 운영 기반 | Flyway, 헬스 체크, PostgreSQL Docker Compose, 테스트·CI |

현재 주문은 `PENDING_PAYMENT`까지입니다. 실제 결제·배송·환불, 모바일 앱,
WebSocket·외부 푸시, 관심 목록·시세, Redis·Kafka·Elasticsearch는 후속 단계입니다.
실시간 전송이나 MSA가 이미 구현된 프로젝트로 소개하지 않습니다.

## 기술

- Java 17 / Spring Boot 4.1.1 / Maven Wrapper
- Spring MVC / Spring Security / Bean Validation
- Spring Data JPA / QueryDSL JPA 7.7 (OpenFeign 포크)
- PostgreSQL 17 / Flyway
- JUnit / ArchUnit / Testcontainers

MyBatis와 H2는 사용하지 않습니다. 통합 테스트도 실제 PostgreSQL을 사용합니다.

## 실행

JDK 17과 실행 중인 Docker Desktop이 필요합니다. 아래 명령은 저장소 루트에서 실행합니다.

### Windows PowerShell

```powershell
$env:DB_PASSWORD = 'choose-a-local-password'
docker compose up -d --wait
.\mvnw.cmd spring-boot:run
```

### macOS / Linux

```bash
export DB_PASSWORD='choose-a-local-password'
docker compose up -d --wait
bash mvnw spring-boot:run
```

기본 API 주소는 `http://localhost:8080`, 상태 확인은 `GET /actuator/health`입니다.
API 서버만 시작하며 웹 화면은 제공하지 않습니다.

DB 기본값은 `jdbc:postgresql://localhost:5432/collectbid`, 사용자 `collectbid`입니다.
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 변경할 수 있습니다.
Flyway가 빈 DB에 테이블을 만들고 Hibernate는 스키마 일치 여부만 검사합니다.

이미 사용 중인 DB나 5432 포트가 있다면 새 프로젝트용 DB와 포트를 별도로 지정하세요.
이 프로젝트는 기존 테이블을 삭제하거나 덮어쓰는 초기화 스크립트를 실행하지 않습니다.
기존 데이터가 있는 DB에는 마이그레이션 계획을 먼저 확인해야 합니다.

`.env.example`은 Compose용 예제입니다. Compose의 `.env` 설정이 Maven 프로세스에 자동으로
전달되지는 않으므로 앱 실행 터미널에도 환경변수를 설정해야 합니다.
로컬 개발 비밀번호를 소스에 넣지 않도록 `.env`는 제외 처리했습니다.
PostgreSQL 볼륨이 이미 만들어진 뒤 환경변수만 바꿔도 기존 DB 비밀번호가 자동 변경되지는 않습니다.

DB 종료는 `docker compose stop`을 사용합니다. 볼륨과 데이터는 보존됩니다.

## 자동 시연

앱을 실행해 둔 상태에서 새 PowerShell 창을 열어 실행합니다.

```powershell
.\scripts\demo.ps1
```

약 15~35초 동안 다음 흐름을 확인합니다.

1. 새 판매자·구매자 가입과 로그인
2. 일반 경매 생성·입찰·동일 요청 재전송
3. 역경매 생성·제안·구매자 선택
4. 스케줄러의 일반 경매 자동 종료
5. 주문 2건과 알림 확인, 토큰 로그아웃

시연 계정과 데이터는 새로 생성하며 기존 데이터는 건드리지 않습니다.
별도 포트는 `-BaseUrl http://localhost:8081`로 지정합니다.

개별 API 요청은 [API 안내](docs/API.md)를 참고하세요.

## 테스트

빠른 도메인·구조 테스트는 Docker 없이 실행할 수 있습니다.

```powershell
.\mvnw.cmd test
```

HTTP·보안·Flyway·정합성 통합 테스트까지 실행하려면 Docker를 켜고:

```powershell
.\mvnw.cmd -Pintegration verify
```

macOS/Linux에서는 `bash mvnw test`, `bash mvnw -Pintegration verify`를 사용합니다.

통합 테스트는 Testcontainers가 임시 PostgreSQL을 생성하며 별도의 DB 비밀번호 설정이 필요 없습니다.
애플리케이션용 DB를 초기화하지 않습니다. Docker가 없으면 통합 테스트는 실패하며 조용히 건너뛰지 않습니다.

주요 검증 내용:

- 동일 경매의 같은 금액 동시 입찰에서 정확히 한 건만 성공
- 같은 요청 키 동시 재전송에도 입찰 이력은 한 건
- 동일 상품 동시 출품 방지
- 동시 종료·제안 선택에도 주문은 한 건
- 종료 시각 이후 입찰·역경매 선택 거절
- 주문 처리 중 장애 시 경매·상품·주문·알림 전체 롤백
- 역경매 제안 후 바뀌거나 출품된 상품의 선택 거절
- 토큰 만료·로그아웃, 타인 상품·주문·알림 접근 차단
- 모듈 간 내부 구현 참조와 순환 의존성 차단

결과는 `target/surefire-reports`, `target/failsafe-reports`에 생성됩니다.
GitHub Actions 설정도 같은 통합 검증 명령을 사용합니다. 원격 실행 여부는 별도로 확인해야 합니다.
경쟁 요청 테스트는 정확성 검증이며 처리량·지연 시간 벤치마크는 아닙니다.

초기 구현은 **29개 테스트와 패키징된 앱의 자동 시연을 통과**했습니다.
환경·시나리오·검증 범위는 [검증 기록](docs/VERIFICATION.md)에 남겼습니다.

## 코드 구조

```text
src/main/java/com/example/collectbid
├─ global          공통 설정·보안 계약·오류·페이징
├─ member          회원·비밀번호·토큰
├─ product
│  └─ api          상품 모듈 공개 계약
├─ auction         일반 경매·입찰·역경매 제안
│  └─ api          낙찰·최고가 변경 이벤트
├─ order           낙찰 이벤트에 따른 주문
└─ notification    인앱 알림

src/main/resources/db/migration   Flyway 스키마
src/test                         도메인·구조·PostgreSQL 통합 테스트
scripts/demo.ps1                  실행 가능한 API 시연
docs                             API·아키텍처·개선 로드맵
```

배포는 하나로 유지하고 코드 경계는 `api` 계약과 package-private으로 제한했습니다.
경매와 입찰은 같은 트랜잭션에 묶었습니다.
모듈 간 이벤트는 현재 동기 처리이며, 별도 메시지 브로커에 전달하지 않습니다.

## 포트폴리오 개선 순서

**현재 MVP → 부하·쿼리 측정 → 실시간 알림 → Outbox와 재처리 → 알림부터 MSA 분리**

- [아키텍처와 ADR](docs/ARCHITECTURE.md): 선택 이유, 정합성 경계, 현재 한계
- [개선 로드맵](docs/ROADMAP.md): 단계별 검증 질문과 개선 기록 방식
- [API 문서](docs/API.md): 입력·응답·권한·오류·재시도 규칙

외부 공개 배포 전에는 HTTPS, 로그인 시도 제한, 이메일 인증·계정 복구,
개인정보·로그 보관, DB 백업과 장애 관측을 추가로 설계해야 합니다.

This project is developed for portfolio and learning purposes.
