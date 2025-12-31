# Message Queue Study

RabbitMQ를 활용한 쿠폰 발급 시스템 학습 프로젝트입니다.
동기 방식과 비동기(MQ) 방식의 쿠폰 발급을 비교하며 메시지 큐의 동작 원리를 학습합니다.

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.9 |
| Build | Gradle (Kotlin DSL) |
| Database | MySQL 8.4 |
| Message Queue | RabbitMQ 3 (Management) |
| ORM | Spring Data JPA |

## 문서

| 문서 | 설명 |
|------|------|
| [API 엔드포인트](docs/api.md) | REST API 명세 |
| [아키텍처](docs/architecture.md) | 도메인 모델, RabbitMQ 설정, 비동기 흐름 |
| [동시성 제어](docs/concurrency.md) | 비관적 락 vs 원자적 업데이트 |
| [성능 테스트](docs/performance-test.md) | MQ 적용/미적용 비교 테스트 결과 |

## 시작하기

### 1. Docker 컨테이너 실행

```bash
docker-compose up -d
```

- MySQL: `localhost:3316`
- RabbitMQ: `localhost:5682` (AMQP), `localhost:15772` (Management UI)

### 2. 데이터베이스 스키마 생성

```sql
-- 쿠폰 테이블
CREATE TABLE coupon (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255),
    total_quantity INT NOT NULL,
    issued_quantity INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 쿠폰 발급 내역 테이블
CREATE TABLE coupon_issue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT uk_coupon_user UNIQUE (coupon_id, user_id)
);

-- 비동기 발급 요청 추적 테이블
CREATE TABLE coupon_issue_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(36) NOT NULL,
    coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_request_id UNIQUE (request_id)
);

-- 테스트용 쿠폰 데이터
INSERT INTO coupon (id, name, total_quantity, issued_quantity, created_at)
VALUES (100, '신규 가입 쿠폰', 100, 0, NOW());
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

애플리케이션이 `http://localhost:8090`에서 실행됩니다.

## 프로젝트 구조

```
src/main/java/gguip1/study/messagequeuestudy/
├── api/                    # REST API 컨트롤러
├── config/                 # RabbitMQ 설정
├── domain/                 # 엔티티 (Coupon, CouponIssue, CouponIssueRequest)
├── mq/                     # MQ Producer/Consumer
├── repository/             # JPA Repository
├── service/                # 비즈니스 로직
├── test/                   # MQ 적용/미적용 테스트용 API
└── ui/                     # 메인 페이지 컨트롤러
```

## 핵심 테스트 결과

MQ 적용/미적용 비교 테스트 결과입니다. (상세: [성능 테스트](docs/performance-test.md))

| 지표 | MQ 미적용 | MQ 적용 |
|------|----------|---------|
| **요청 손실** | 105개 (2.1%) | **0개** |
| RPS | **934** | 258 |
| 평균 응답 | **501ms** | 1,868ms |

```
┌─────────────────────────────────────────────────────┐
│                                                     │
│  MQ의 이점은 "성능 향상"이 아니라                   │
│  "요청 손실 방지"다.                                │
│                                                     │
│  성능을 희생하고 안정성을 얻는다.                   │
│                                                     │
└─────────────────────────────────────────────────────┘
```

## 학습 포인트

1. **동기 vs 비동기 처리**: 요청-응답 방식과 메시지 큐 기반 처리의 차이
2. **동시성 제어 전략**: 비관적 락 vs 원자적 업데이트의 트레이드오프
3. **중복 방지**: DB 유니크 제약조건을 활용한 중복 발급 방지
4. **RabbitMQ**: Exchange, Queue, Binding 개념과 Spring AMQP 연동
5. **Producer-Consumer 패턴**: 메시지 발행과 소비의 분리
6. **RPC 패턴**: MQ를 거치지만 응답을 기다리는 동기 방식
7. **Polling 기반 상태 조회**: 비동기 처리 결과 확인 방법
