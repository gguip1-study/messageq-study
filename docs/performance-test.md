# MQ 적용/미적용 성능 비교 테스트

## 테스트 목적

MQ(Message Queue) 적용 시 얻는 이점과 트레이드오프를 실제 부하 테스트로 검증합니다.

---

## 테스트 환경

### 하드웨어/소프트웨어

| 항목 | 설정 |
|------|------|
| OS | macOS (Darwin 25.2.0) |
| Java | 21 |
| Spring Boot | 3.5.9 |
| MySQL | 8.4 (Docker) |
| RabbitMQ | 3.x Management (Docker) |

### 리소스 제한 설정

```yaml
# application.yml - 리소스 부족 상황 시뮬레이션
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3316/lab
    hikari:
      maximum-pool-size: 5          # DB 커넥션 5개
      connection-timeout: 500       # 대기 타임아웃 0.5초

  rabbitmq:
    host: 127.0.0.1
    port: 5682
    listener:
      simple:
        concurrency: 5              # Consumer 스레드 5개
        max-concurrency: 5
```

**핵심 설정:**
- **HikariCP**: 커넥션 5개, 500ms 타임아웃 (리소스 부족 시뮬레이션)
- **RabbitMQ Consumer**: 5개 스레드 (DB 커넥션 풀과 동일)

### 부하 테스트 설정

| 항목 | 값 |
|------|-----|
| 테스트 도구 | hey (HTTP 부하 테스트 도구) |
| 총 요청 수 | 5,000건 |
| 동시 접속 수 | 500개 |
| HTTP 메서드 | POST |
| Content-Type | application/json |

---

## 테스트 1: 기본 MQ 적용/미적용 비교

### 테스트한 API 3가지

| API | 구조 | 설명 |
|-----|------|------|
| `/test/issue-direct` | API → DB → 응답 | MQ 미적용 |
| `/api/coupons/issue-async` | API → **DB INSERT** → MQ → 응답 | 기존 MQ (DB 사용) |
| `/test/issue-mq` | API → MQ → 응답 | 테스트용 MQ (DB 미사용) |

### 흐름 비교

```
[MQ 미적용]
API → issueSyncAtomic() → 응답

[기존 MQ - DB INSERT 있음]
API → DB INSERT (coupon_issue_request) → MQ 발행 → 응답
                                              ↓
                                    Consumer → issueSyncAtomic()

[테스트용 MQ - DB INSERT 없음]
API → MQ 발행 → 응답
         ↓
Consumer → issueSyncAtomic()
```

### 테스트 명령어

```bash
# DB 초기화
mysql -h 127.0.0.1 -P 3316 -u app -papppw lab -e "
  TRUNCATE TABLE coupon_issue;
  UPDATE coupon SET issued_quantity = 0 WHERE id = 100;
"

# MQ 미적용
hey -n 5000 -c 500 -m POST \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"couponId":100}' \
  http://localhost:8090/test/issue-direct

# 기존 MQ (DB 사용)
hey -n 5000 -c 500 -m POST \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"couponId":100}' \
  http://localhost:8090/api/coupons/issue-async

# 테스트용 MQ (DB 미사용)
hey -n 5000 -c 500 -m POST \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"couponId":100}' \
  http://localhost:8090/test/issue-mq
```

### 결과

| API | 성공 | 실패 (500) | RPS | 평균 응답 |
|-----|------|------------|-----|-----------|
| MQ 미적용 | 4,985 | **15** | 949 | 488ms |
| 기존 MQ (DB 사용) | 4,712 | **288** | 934 | 503ms |
| 테스트용 MQ (DB 미사용) | **5,000** | **0** | 5,582 | 78ms |

### 분석

#### 1. 기존 MQ API는 MQ 이점이 없다

```
MQ 미적용:         15개 실패
기존 MQ (DB 사용): 288개 실패  ← 오히려 더 나쁨!
```

**이유:** 기존 MQ API도 `coupon_issue_request` INSERT를 하므로 DB 커넥션을 사용합니다.

#### 2. MQ 이점을 얻으려면 API에서 DB를 사용하면 안 된다

```
테스트용 MQ (DB 미사용): 0개 실패  ← MQ 이점 확인
```

API가 DB를 사용하지 않고 MQ 발행만 하면 커넥션 경합이 없습니다.

#### 3. RPS/응답 시간 비교는 의미 없다

| 지표 | MQ 미적용 | 테스트용 MQ |
|------|----------|-------------|
| RPS | 949 | 5,582 |
| 평균 응답 | 488ms | 78ms |

**이 숫자는 함정입니다.**

```
MQ 미적용: API 응답 = 처리 완료 (쿠폰 발급 끝)
MQ 적용:   API 응답 = 큐에 넣음 (처리 완료 아님!)
```

RPS, 응답 시간을 비교하는 것은 **사과와 오렌지를 비교하는 것**입니다.

---

## 테스트 2: 공정한 비교 (RPC 패턴)

### 문제 인식

테스트 1에서 MQ 적용 시 RPS가 6배 높고 응답이 7배 빠른 것은 공정한 비교가 아닙니다.

### 공정한 비교를 위한 조건

**둘 다 "처리 완료 후 응답"으로 통일해야 합니다.**

| API | 응답 시점 | 비교 가능 |
|-----|----------|-----------|
| `/test/issue-direct` | 처리 완료 후 | O |
| `/test/issue-mq` | 큐에 넣고 바로 | **X (불공정)** |
| `/test/issue-mq-sync` | **처리 완료 후** | **O (공정)** |

### RPC 패턴이란?

```
[일반 MQ - Fire and Forget]
Client → API → MQ → 즉시 응답
                ↓
           Consumer (나중에 처리)

[RPC 패턴 - 응답 대기]
Client → API → MQ ─────────────────┐
                ↓                   │
           Consumer → 처리 → 응답 ──┘
                              ↓
         API ← 응답 받음 ← ───┘
                ↓
         Client ← 최종 응답
```

### 코드

**Producer (API):**
```java
@PostMapping("/issue-mq-sync")
public ResponseEntity<?> issueMqSync(@RequestBody IssueReq req) {
    // convertSendAndReceive: 응답 올 때까지 블로킹
    var response = (RpcCouponResponse) rabbitTemplate.convertSendAndReceive(
        RabbitConfig.EXCHANGE,
        RabbitConfig.RPC_ROUTING_KEY,
        new TestCouponMessage(req.userId(), req.couponId())
    );

    return ResponseEntity.ok(Map.of("result", response.result()));
}
```

**Consumer:**
```java
@RabbitListener(queues = RabbitConfig.RPC_QUEUE)
public RpcCouponResponse onMessage(TestCouponMessage msg) {
    // 반환값이 자동으로 replyTo 큐로 전송됨
    var result = couponIssueService.issueSyncAtomic(msg.userId(), msg.couponId());
    return new RpcCouponResponse(result.name());
}
```

### 테스트 명령어

```bash
# MQ 미적용 테스트
hey -n 5000 -c 500 -m POST \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"couponId":100}' \
  http://localhost:8090/test/issue-direct

# MQ 적용 RPC 테스트
hey -n 5000 -c 500 -m POST \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"couponId":100}' \
  http://localhost:8090/test/issue-mq-sync
```

### 결과 (Consumer 1개)

| 지표 | MQ 미적용 | MQ 적용 (RPC) |
|------|----------|---------------|
| **성공** | 4,454 | **5,000** |
| **실패 (500)** | **546** | **0** |
| RPS | **606** | 292 |
| 평균 응답 시간 | **791ms** | 1,618ms |
| 총 소요 시간 | **8.25초** | 17.10초 |

---

## 테스트 3: 동시성 수준 맞춘 최종 테스트 (Consumer 5개)

### 문제 인식

테스트 2에서 Consumer는 1개뿐이었습니다. 반면 MQ 미적용은 톰캣 스레드(기본 200개)가 병렬 처리합니다.

```
MQ 미적용: API 스레드 200개 → DB 커넥션 5개 경합
MQ 적용:   Consumer 1개 → DB 커넥션 1개만 사용
```

**동시성 수준이 다르면 공정한 비교가 아닙니다.**

### Consumer 동시성 설정

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        concurrency: 5        # Consumer 스레드 5개
        max-concurrency: 5
```

### 결과 (Consumer 5개)

| 지표 | MQ 미적용 | MQ 적용 (RPC) | 차이 |
|------|----------|---------------|------|
| **성공** | 4,895 | **5,000** | +105 |
| **실패 (500)** | **105** | **0** | -105 |
| **성공률** | 97.9% | **100%** | +2.1% |
| RPS | **934** | 258 | -72% |
| 평균 응답 시간 | **501ms** | 1,868ms | +273% |
| P99 응답 시간 | 1,052ms | 5,927ms | +463% |
| 총 소요 시간 | **5.35초** | 19.42초| +263% |

### Consumer 1개 vs 5개 비교

| 지표 | Consumer 1개 | Consumer 5개 | 변화 |
|------|-------------|--------------|------|
| MQ 적용 RPS | 292 | 258 | -12% |
| MQ 적용 평균 응답 | 1,618ms | 1,868ms | +15% |
| MQ 적용 총 시간 | 17.10초 | 19.42초 | +14% |

**의외의 결과:** Consumer 수를 늘렸는데 오히려 느려졌습니다.

이는 DB 커넥션 경합 때문입니다:
- Consumer 1개: 커넥션 1개만 사용, 경합 없음
- Consumer 5개: 커넥션 5개 경합, 대기 시간 발생

---

## 결과 분석

### 1. 요청 손실

```
MQ 미적용: 105개 실패 (2.1%)
MQ 적용:   0개 실패 (0%)
```

**MQ는 피크 트래픽에서도 요청을 버리지 않습니다.**

MQ 미적용 시 실패 원인:
- 500개 동시 요청 → 5개 DB 커넥션 경합
- 500ms 타임아웃 초과 → `HikariPool Connection is not available` 에러

MQ 적용 시 성공 이유:
- API는 MQ에 메시지만 발행 (DB 사용 안 함)
- Consumer가 자기 속도대로 처리
- MQ가 버퍼 역할 → 요청 손실 없음

### 2. 성능 트레이드오프

```
                MQ 미적용    MQ 적용
RPS:            934         258      (72% 감소)
평균 응답:       501ms       1,868ms  (273% 증가)
```

**MQ를 거치면 오히려 느려집니다.**

오버헤드 원인:
1. MQ 메시지 직렬화/역직렬화
2. 네트워크 왕복 (API → RabbitMQ → Consumer → RabbitMQ → API)
3. 큐 대기 시간

### 3. 응답 시간 분포

```
MQ 미적용:
  10%: 8ms     50%: 265ms    90%: 943ms    99%: 1,052ms

MQ 적용:
  10%: 240ms   50%: 1,565ms  90%: 4,080ms  99%: 5,927ms
```

MQ 적용 시 응답 시간이 전반적으로 높고 분산도 큽니다.

---

## 최종 결론

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  MQ의 이점은 "성능 향상"이 아니라 "요청 손실 방지"다.       │
│                                                             │
│  ┌───────────────┬────────────┬─────────────┐              │
│  │               │ MQ 미적용  │ MQ 적용     │              │
│  ├───────────────┼────────────┼─────────────┤              │
│  │ 요청 손실     │ 105개      │ 0개 ✓       │              │
│  │ RPS          │ 934 ✓      │ 258         │              │
│  │ 평균 응답    │ 501ms ✓    │ 1,868ms     │              │
│  └───────────────┴────────────┴─────────────┘              │
│                                                             │
│  성능을 희생하고 안정성을 얻는다.                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## MQ 사용 판단 기준

### MQ를 쓰면 안 되는 경우

- 즉시 결과 확인 필수 (결제, 인증)
- 낮은 지연 시간이 중요
- 트래픽이 예측 가능하고 리소스 충분

### MQ를 써야 하는 경우

- 피크 트래픽 대응 (선착순 이벤트)
- 요청 손실이 허용 안 됨
- 느린 응답을 허용할 수 있음
- 시스템 간 결합도를 낮추고 싶음

### 결정 테이블

| 상황 | MQ 미적용 | MQ 적용 |
|------|----------|---------|
| 결제/인증 (즉시 결과 필요) | ✓ | |
| 낮은 지연 시간 필수 | ✓ | |
| 트래픽 예측 가능 | ✓ | |
| 선착순 이벤트 (피크 트래픽) | | ✓ |
| 요청 손실 불가 | | ✓ |
| 느린 응답 허용 | | ✓ |
| 시스템 결합도 낮추기 | | ✓ |

---

## 트레이드오프 요약

| 항목 | MQ 미적용 | MQ 적용 |
|------|----------|---------|
| 요청 손실 | 발생 가능 | **방지** |
| 결과 확인 | **즉시** | 나중에 (폴링/콜백) |
| 성능 (RPS) | **높음** | 낮음 |
| 응답 시간 | **빠름** | 느림 |
| 구현 복잡도 | **단순** | 복잡 |
| 장애 포인트 | DB만 | DB + MQ |
