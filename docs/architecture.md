# 아키텍처

## 도메인 모델

### Coupon (쿠폰)

```java
@Entity
public class Coupon {
    @Id
    private Long id;
    private String name;
    private int totalQuantity;    // 총 발급 가능 수량
    private int issuedQuantity;   // 현재 발급된 수량
    private LocalDateTime createdAt;

    public boolean soldOut() {
        return issuedQuantity >= totalQuantity;
    }
}
```

### CouponIssue (발급 내역)

```java
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"coupon_id", "user_id"}))
public class CouponIssue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long couponId;
    private Long userId;
}
```

- `couponId` + `userId` 복합 유니크 키로 **중복 발급 방지**

### CouponIssueRequest (비동기 요청 추적)

```java
@Entity
public class CouponIssueRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String requestId;     // UUID
    private Long couponId;
    private Long userId;
    private String status;        // PENDING → SUCCESS / FAILED
    private String reason;        // 실패 사유
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

---

## RabbitMQ 설정

### Exchange & Queue

| 항목 | 값 | 용도 |
|------|-----|------|
| Exchange | `coupon.exchange` | Direct Exchange |
| Queue | `coupon.issue.queue` | 비동기 발급 요청 |
| Routing Key | `coupon.issue` | 기본 발급 |
| RPC Queue | `coupon.issue.rpc.queue` | RPC 패턴 테스트 |
| RPC Routing Key | `coupon.issue.rpc` | RPC 패턴 테스트 |

### 설정 코드

```java
@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "coupon.exchange";
    public static final String QUEUE = "coupon.issue.queue";
    public static final String ROUTING_KEY = "coupon.issue";

    @Bean
    DirectExchange couponExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    Queue couponIssueQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    Binding couponIssueBinding(Queue couponIssueQueue, DirectExchange couponExchange) {
        return BindingBuilder.bind(couponIssueQueue).to(couponExchange).with(ROUTING_KEY);
    }
}
```

### Management UI

- URL: http://localhost:15772
- 계정: app / apppw

---

## 비동기 발급 흐름

### 전체 흐름도

```
┌─────────┐      ┌─────────────┐      ┌──────────┐      ┌──────────┐
│  Client │─────▶│   API       │─────▶│ RabbitMQ │─────▶│ Consumer │
└─────────┘      │  Controller │      │  Queue   │      └────┬─────┘
     │           └──────┬──────┘      └──────────┘           │
     │                  │                                     │
     │           ┌──────▼──────┐                       ┌──────▼──────┐
     │           │ Request DB  │◀──────────────────────│   Service   │
     │           │  (PENDING)  │      상태 업데이트      │ (발급 처리)  │
     │           └──────┬──────┘                       └─────────────┘
     │                  │
     └──────────────────┘
          Polling 조회
```

### 처리 순서

#### 1. 요청 접수 (`POST /issue-async`)

```java
@PostMapping("/issue-async")
public ResponseEntity<?> issueAsync(@RequestBody IssueRequest req) {
    // 1. 요청 저장 (status: PENDING)
    var request = CouponIssueRequest.create(req.couponId(), req.userId());
    requestRepository.save(request);

    // 2. MQ에 메시지 발행
    publisher.publish(new CouponIssueMessage(
        request.getRequestId(),
        req.couponId(),
        req.userId()
    ));

    // 3. 즉시 응답 (requestId 반환)
    return ResponseEntity.ok(Map.of(
        "requestId", request.getRequestId(),
        "status", "PENDING"
    ));
}
```

#### 2. 비동기 처리 (`CouponIssueConsumer`)

```java
@RabbitListener(queues = RabbitConfig.QUEUE)
public void onMessage(CouponIssueMessage msg) {
    // 1. 쿠폰 발급 처리
    var result = couponIssueService.issueSyncAtomic(msg.userId(), msg.couponId());

    // 2. 요청 상태 업데이트
    var request = requestRepository.findByRequestId(msg.requestId());
    if (result == IssueResult.ISSUED) {
        request.success();
    } else {
        request.fail(result.name());
    }
    requestRepository.save(request);
}
```

#### 3. 결과 조회 (`GET /requests/{requestId}`)

```java
@GetMapping("/requests/{requestId}")
public ResponseEntity<?> getRequest(@PathVariable String requestId) {
    var request = requestRepository.findByRequestId(requestId);
    return ResponseEntity.ok(Map.of(
        "requestId", request.getRequestId(),
        "status", request.getStatus(),
        "reason", request.getReason()
    ));
}
```

---

## RPC 패턴

### 일반 MQ vs RPC

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

### RPC Producer

```java
@PostMapping("/issue-mq-sync")
public ResponseEntity<?> issueMqSync(@RequestBody IssueReq req) {
    // convertSendAndReceive: 응답 올 때까지 블로킹
    var response = (RpcCouponResponse) rabbitTemplate.convertSendAndReceive(
        RabbitConfig.EXCHANGE,
        RabbitConfig.RPC_ROUTING_KEY,
        new TestCouponMessage(req.userId(), req.couponId())
    );

    return ResponseEntity.ok(Map.of(
        "mode", "MQ_RPC",
        "result", response.result()
    ));
}
```

### RPC Consumer

```java
@RabbitListener(queues = RabbitConfig.RPC_QUEUE)
public RpcCouponResponse onMessage(TestCouponMessage msg) {
    // 반환값이 자동으로 replyTo 큐로 전송됨
    var result = couponIssueService.issueSyncAtomic(msg.userId(), msg.couponId());
    return new RpcCouponResponse(result.name());
}
```

**핵심:** `@RabbitListener` 메서드가 값을 반환하면 Spring AMQP가 자동으로 `replyTo` 큐로 응답을 보냅니다.

---

## 프로젝트 구조

```
src/main/java/gguip1/study/messagequeuestudy/
├── api/
│   └── CouponApiController.java      # REST API 엔드포인트
├── config/
│   └── RabbitConfig.java             # RabbitMQ 설정
├── domain/
│   ├── Coupon.java                   # 쿠폰 엔티티
│   ├── CouponIssue.java              # 쿠폰 발급 내역
│   └── CouponIssueRequest.java       # 비동기 발급 요청 추적
├── mq/
│   ├── CouponIssueMessage.java       # MQ 메시지 DTO (record)
│   ├── CouponPublisher.java          # RabbitMQ Producer
│   └── CouponIssueConsumer.java      # RabbitMQ Consumer
├── repository/
│   ├── CouponRepository.java
│   ├── CouponIssueRepository.java
│   └── CouponIssueRequestRepository.java
├── service/
│   └── CouponIssueService.java       # 쿠폰 발급 비즈니스 로직
├── test/
│   ├── TestCouponController.java     # MQ 적용/미적용 공정 비교 테스트용 API
│   ├── TestCouponConsumer.java       # 테스트용 MQ Consumer
│   ├── TestCouponMessage.java        # 테스트용 MQ 메시지
│   ├── RpcCouponConsumer.java        # RPC 패턴 Consumer
│   └── RpcCouponResponse.java        # RPC 응답 메시지
└── ui/
    └── HoleController.java           # 메인 페이지 컨트롤러
```
