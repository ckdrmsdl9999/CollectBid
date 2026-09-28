# 초기 MVP 검증 기록

검증일: 2026-09-28. 이후 변경 전의 기준선 기록이다.

## 환경과 실행 명령

- Windows, JDK 17.0.12, Maven Wrapper 3.9.16
- Spring Boot 4.1.1, Hibernate 7.4.5.Final
- Docker Desktop 27.4.0, `postgres:17-alpine` 이미지의 PostgreSQL 17.11
- 명령: `.\mvnw.cmd -B -Pintegration verify`
- 결과: **BUILD SUCCESS**, 실패 0, 오류 0, 생략 0

| 테스트 | 개수 | 확인한 내용 |
| --- | ---: | --- |
| AuctionTest | 9 | 가격·기한·자기 입찰·종료·취소·역경매 예산 |
| ProductTest | 4 | 소유권·중복 예약·판매·유찰 후 재출품 |
| CollectbidApplicationTests | 1 | 모듈 공개 계약과 순환 의존성 |
| CollectbidApiIT | 15 | 실제 HTTP·인증·QueryDSL·Flyway·PostgreSQL 트랜잭션 |
| 합계 | **29** | 전체 통과 |

## 경쟁 요청·장애 시나리오

- 같은 가격의 동시 입찰 16개: 성공 1건, 규칙 위반 409 응답 15건, 입찰 행 1개
- 같은 요청 키의 동시 재전송 12개: 모두 동일한 입찰 결과, 입찰 행 1개
- 같은 상품의 동시 출품 8개: 성공 1건, 409 응답 7건
- 같은 일반 경매 동시 종료 8개: 주문 1개, 낙찰 알림 2개
- 같은 역경매 제안의 동시 선택 6개: 주문 1개, 거래 당사자 일치
- 서로 다른 역경매 제안 2개 동시 선택: 성공 1개, 남은 상품은 AVAILABLE 유지
- DB 행 잠금 대기 중 시각을 마감으로 이동: 대기 종료 후 입찰 거절, 입찰 행 0개
- 낙찰 리스너에 의도적으로 예외 발생: 경매 OPEN·상품 LISTED 유지, 주문·알림 행 0개; 재시도 성공

마지막 시나리오의 `Intentional settlement failure for rollback verification` ERROR 로그는
의도한 장애 주입이다. 테스트 실패를 의미하지 않는다.

## 실행 파일 시연

생성된 `target/collectbid-0.0.1-SNAPSHOT.jar`를 테스트 클래스 없이 실행했다.
별도 임시 PostgreSQL과 임의 포트를 사용하고 `scripts/demo.ps1`을 실제로 실행했다.

- 회원 2명 가입·로그인
- 상품 2개 등록
- 일반 경매 입찰 및 동일 요청 재전송 확인
- 역경매 판매 제안·구매자 선택
- 실제 시스템 시각과 활성 스케줄러로 일반 경매 자동 종료
- 주문 2개와 구매자 알림 2개 확인
- 토큰 로그아웃
- 임시 서버·컨테이너 정리

추가로 `docker compose config --quiet`와 PowerShell 시연 스크립트 구문 검사를 통과했다.
기존 개발 DB는 사용하거나 초기화하지 않았다. Git 명령과 원격 저장소 작업은 실행하지 않았다.

## 이 검증으로 확인하지 않은 것

처리량·p95/p99 성능, 장시간 안정성, 실제 결제, 외부 메시지 전달, 서비스 간 분산 정합성,
멀티 인스턴스 배포, GitHub Actions 원격 실행은 검증하지 않았다.
현재 테스트는 MVP 기능과 단일 PostgreSQL 기반 정합성의 기준선이다.
