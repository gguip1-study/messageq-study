# API 엔드포인트

## 동기 발급 API

### 비관적 락 방식

```http
POST /api/coupons/issue-sync
Content-Type: application/json

{
  "userId": 1,
  "couponId": 100
}
```

**응답 예시:**
```json
{
  "mode": "SYNC",
  "result": "ISSUED",
  "userId": 1,
  "couponId": 100
}
```

### 원자적 업데이트 방식

```http
POST /api/coupons/issue-sync-atomic
Content-Type: application/json

{
  "userId": 1,
  "couponId": 100
}
```

**응답 예시:**
```json
{
  "mode": "SYNC_ATOMIC_UPDATE",
  "result": "ISSUED",
  "userId": 1,
  "couponId": 100
}
```

### 발급 결과 코드

| 코드 | 설명 |
|------|------|
| `ISSUED` | 발급 성공 |
| `SOLD_OUT` | 재고 소진 |
| `ALREADY_ISSUED` | 이미 발급됨 |
| `COUPON_NOT_FOUND` | 쿠폰 없음 |

---

## 비동기 발급 API (MQ)

### 발급 요청

요청을 RabbitMQ에 발행하고 즉시 응답합니다. 실제 처리는 Consumer가 비동기로 수행합니다.

```http
POST /api/coupons/issue-async
Content-Type: application/json

{
  "userId": 1,
  "couponId": 100
}
```

**응답 예시 (즉시 반환):**
```json
{
  "mode": "MQ_ACCEPTED",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "userId": 1,
  "couponId": 100
}
```

### 요청 상태 조회 (Polling)

비동기 발급 결과를 폴링으로 조회합니다.

```http
GET /api/coupons/requests/{requestId}
```

**응답 예시 (처리 완료):**
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "SUCCESS",
  "reason": null,
  "userId": 1,
  "couponId": 100
}
```

**응답 예시 (실패):**
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "FAILED",
  "reason": "SOLD_OUT",
  "userId": 1,
  "couponId": 100
}
```

---

## 테스트용 API

MQ 적용/미적용 공정 비교를 위한 테스트 전용 API입니다.

### MQ 미적용 - 직접 처리

```http
POST /test/issue-direct
Content-Type: application/json

{
  "userId": 1,
  "couponId": 100
}
```

**응답:**
```json
{
  "mode": "DIRECT",
  "result": "ISSUED",
  "userId": 1,
  "couponId": 100
}
```

### MQ 적용 - Fire and Forget

```http
POST /test/issue-mq
Content-Type: application/json

{
  "userId": 1,
  "couponId": 100
}
```

**응답 (큐에 넣고 바로 반환):**
```json
{
  "mode": "MQ",
  "status": "QUEUED",
  "userId": 1,
  "couponId": 100
}
```

### MQ 적용 - RPC (처리 완료 대기)

```http
POST /test/issue-mq-sync
Content-Type: application/json

{
  "userId": 1,
  "couponId": 100
}
```

**응답 (처리 완료 후 반환):**
```json
{
  "mode": "MQ_RPC",
  "result": "ISSUED",
  "userId": 1,
  "couponId": 100
}
```

---

## API 비교

| API | 경로 | 처리 방식 | 응답 시점 |
|-----|------|----------|----------|
| 동기 (락) | `/api/coupons/issue-sync` | 비관적 락 | 처리 완료 후 |
| 동기 (원자적) | `/api/coupons/issue-sync-atomic` | 원자적 UPDATE | 처리 완료 후 |
| 비동기 (MQ) | `/api/coupons/issue-async` | MQ → Consumer | 큐 발행 후 즉시 |
| 테스트 (직접) | `/test/issue-direct` | 원자적 UPDATE | 처리 완료 후 |
| 테스트 (MQ) | `/test/issue-mq` | MQ → Consumer | 큐 발행 후 즉시 |
| 테스트 (RPC) | `/test/issue-mq-sync` | MQ → Consumer → 응답 | 처리 완료 후 |
