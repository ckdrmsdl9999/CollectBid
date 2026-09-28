# REST API

기본 주소: `http://localhost:8080`. 요청·응답은 JSON이며 시각은 UTC ISO-8601이다.
인증이 필요한 요청에 `Authorization: Bearer <accessToken>`을 보낸다. 브라우저 쿠키 인증은 없다.
금액은 원 단위 정수이며 1원부터 1조 원까지다. 페이지는 0부터, size는 1~100(기본 20)이다.

## 엔드포인트

| Method | Path | 인증 | 기능 |
| --- | --- | --- | --- |
| POST | `/api/members` | 공개 | 회원 가입, 201 |
| POST | `/api/auth/login` | 공개 | 로그인·토큰 발급 |
| POST | `/api/auth/logout` | 필요 | 현재 토큰 폐기, 204 |
| GET | `/api/members/me` | 필요 | 본인 프로필 |
| GET | `/api/categories` | 공개 | 카테고리 목록 |
| POST | `/api/products` | 필요 | 상품 등록, 201 |
| GET | `/api/products` | 공개 | 상품 검색 |
| GET | `/api/products/{id}` | 공개 | 상품 조회 |
| PUT | `/api/products/{id}` | 소유자 | 상품 전체 수정 |
| DELETE | `/api/products/{id}` | 소유자 | 미출품 상품 논리 삭제, 204 |
| POST | `/api/auctions/forward` | 상품 소유자 | 일반 경매 생성, 201 |
| POST | `/api/auctions/reverse` | 필요 | 역경매 구매 요청 생성, 201 |
| GET | `/api/auctions` | 공개 | 조건 검색 |
| GET | `/api/auctions/{id}` | 공개 | 경매 상세 |
| POST | `/api/auctions/{id}/bids` | 필요 | 일반 경매 입찰, 요청 키 필수 |
| GET | `/api/auctions/{id}/bids` | 공개 | 일반 경매 입찰 이력 |
| POST | `/api/auctions/{id}/offers` | 필요 | 역경매 판매 제안, 요청 키 필수 |
| GET | `/api/auctions/{id}/offers` | 필요 | 요청자는 전체, 판매자는 본인 제안만 |
| POST | `/api/auctions/{id}/offers/{offerId}/accept` | 역경매 요청자 | 판매 제안 선택·주문 생성 |
| POST | `/api/auctions/{id}/close` | 경매 생성자 | 기한 이후 종료, 스케줄러와 같은 처리 |
| POST | `/api/auctions/{id}/cancel` | 경매 생성자 | 참여자가 없는 경매 취소 |
| GET | `/api/orders` | 필요 | 본인의 구매·판매 주문 목록 |
| GET | `/api/orders/{id}` | 거래 당사자 | 주문 상세 |
| GET | `/api/notifications` | 필요 | 본인 알림 목록 |
| PATCH | `/api/notifications/{id}/read` | 수신자 | 알림 읽음 처리 |
| GET | `/actuator/health` | 공개 | 서버·DB 상태 |

POST 생성 응답 중 입찰·제안·선택·종료는 재호출에 동일한 응답을 주도록 200을 사용한다.
이력·제안·주문·알림 목록에도 `page`, `size`를 지정할 수 있다.

## 회원 가입과 로그인

```json
{
  "email": "seller@example.com",
  "password": "Example-password-123!",
  "nickname": "판매자"
}
```

가입은 위 세 필드, 로그인은 이메일·비밀번호만 보낸다.
닉네임은 2~40자, 비밀번호는 10~64자이면서 UTF-8 72바이트 이하이다.
로그인 응답의 `accessToken`은 이때만 제공된다. 로그아웃 또는 2시간 만료 후 다시 로그인한다.

```json
{
  "accessToken": "발급된-토큰",
  "tokenType": "Bearer",
  "expiresAt": "2026-09-28T12:00:00Z"
}
```

## 상품 등록·수정

```json
{
  "title": "포켓몬 카드 미개봉",
  "description": "보관 상태와 상품 설명",
  "category": "TCG",
  "condition": "SEALED",
  "imageUrl": "https://example.com/card.jpg"
}
```

`imageUrl`은 생략 가능하다. 파일 업로드 기능은 아직 없다.
카테고리: `TCG`, `PHOTOCARD`, `LEGO`, `FIGURE`, `MODEL_KIT`, `SPORTS`, `OTHER`.
상품 상태: `SEALED`, `LIKE_NEW`, `GOOD`, `FAIR`.
상품 검색 조건은 `keyword`, `category`, `sellerId`, `page`, `size`다. 삭제 상품은 제외한다.

## 일반 경매

`endsAt`은 **실행 시점 이후부터 30일 이내**로 지정한다. 아래 예제의 시각은 실행할 때 바꿔야 한다.

```json
{
  "productId": 1,
  "startingPrice": 10000,
  "minIncrement": 1000,
  "endsAt": "2026-09-29T12:00:00Z"
}
```

즉시 OPEN 상태로 시작한다. 첫 입찰 전에는 `currentPrice`, `leadingBidderId`가 null이다.
입찰 본문은 `{"amount": 10000}`이다. 다음 헤더가 필수다.

```http
Authorization: Bearer <구매자 토큰>
Idempotency-Key: 5ca6f54e-2879-4eed-8573-04d71f020390
```

네트워크 오류로 응답을 못 받으면 **같은 키 + 같은 금액**으로 재시도한다.
금액을 바꾸는 새 입찰에는 새 UUID를 사용한다. 키는 경매·입찰자 단위로 관리한다.
성공했던 입찰은 경매 종료 후에도 같은 키로 재조회할 수 있다.
자기 입찰·낮은 금액·기한 이후 입찰은 409로 거절한다.

## 역경매

구매 요청:

```json
{
  "title": "미개봉 카드 구매합니다",
  "description": "배송비 포함, 상태 사진 제공 가능 상품",
  "category": "TCG",
  "budget": 30000,
  "endsAt": "2026-09-29T12:00:00Z"
}
```

판매 제안:

```json
{
  "productId": 2,
  "amount": 25000,
  "note": "미개봉, 배송비 포함"
}
```

제안에도 `Idempotency-Key`를 보낸다. 판매자는 본인 상품만 제안하며 카테고리가 같아야 한다.
MVP는 판매자당 하나의 제안만 허용한다. 제안 단계에는 상품을 예약하지 않는다.
구매자는 기한 내에 accept 엔드포인트를 호출한다. 본문은 없다.
선택 시 상품의 판매 가능 상태와 제안 당시 정보를 다시 검증한다.
역경매 응답에서는 `startingPrice`가 구매 예산, `leadingBidderId`가 선택된 판매자 ID다.
현재 공통 경매 DTO를 사용하므로 이 필드 의미를 구분해야 한다.

## 검색

```http
GET /api/auctions?keyword=카드&category=TCG&kind=FORWARD&openOnly=true&minPrice=10000&maxPrice=50000&sort=ENDING_SOON&page=0&size=20
```

- `kind`: `FORWARD`, `REVERSE` 또는 생략
- `sort`: `NEWEST`(기본), `ENDING_SOON`, `PRICE_ASC`, `PRICE_DESC`
- 가격: 입찰이 있으면 현재가, 없으면 시작가. 미선택 역경매에서는 예산이다.
- `openOnly=true`가 기본이며 종료 시각이 지난 OPEN 행도 제외한다.
- 종료·취소된 경매까지 보려면 `openOnly=false`를 사용한다.
- `ENDING_SOON`은 현재 진행 중 필터와 함께 사용하는 것을 권장한다.

페이지 응답:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

## 오류

업무 오류는 `application/problem+json`과 안정적인 `code`로 응답한다.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "최소 입찰 금액은 11000원입니다.",
  "code": "BID_TOO_LOW"
}
```

| Status | 주요 코드 | 의미 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST`, `VALIDATION_FAILED` | 본문·범위·헤더 형식 오류 |
| 401 | `UNAUTHORIZED`, `INVALID_CREDENTIALS` | 인증 필요·로그인 실패 |
| 403 | `FORBIDDEN` | 소유권·거래 당사자 검사 실패 |
| 404 | `NOT_FOUND` | 대상 없음 또는 타인의 알림 |
| 409 | `BID_TOO_LOW`, `AUCTION_CLOSED`, `SELF_PARTICIPATION` | 경매 규칙 위반 |
| 409 | `IDEMPOTENCY_KEY_REUSED`, `OFFER_EXISTS` | 중복 요청 충돌 |
| 409 | `PRODUCT_UNAVAILABLE`, `OFFER_STALE` | 상품 상태 변경 |
| 409 | `CONCURRENT_REQUEST`, `DATA_CONFLICT` | 잠금 대기·DB 제약 충돌 |
| 500 | `INTERNAL_ERROR` | 예기치 않은 오류, 저장 작업은 롤백 |

404/405/415 같은 프레임워크 오류는 Spring의 기본 ProblemDetail 형태를 사용할 수 있다.
생성 API 전체에 공통 멱등성 키를 적용한 것은 아니다. 현재 보장은 입찰·제안과 반복 선택·종료에 해당한다.
