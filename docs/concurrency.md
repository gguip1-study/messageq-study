# 동시성 제어

쿠폰 발급 시 재고 초과 발급을 방지하기 위한 두 가지 동시성 제어 전략을 비교합니다.

## 문제 상황

```
쿠폰 재고: 1개
동시 요청: 2개

[락 없이 처리하면]
Thread A: SELECT → 재고 1개 확인 → 발급 가능
Thread B: SELECT → 재고 1개 확인 → 발급 가능  ← A가 UPDATE 하기 전에 읽음
Thread A: UPDATE → 재고 0개
Thread B: UPDATE → 재고 -1개  ← 초과 발급!
```

---

## 1. 비관적 락 (Pessimistic Lock)

### 개념

다른 트랜잭션이 데이터에 접근하지 못하도록 **미리 잠금**을 거는 방식입니다.

### 구현

```java
// Repository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select c from Coupon c where c.id = :id")
Optional<Coupon> findByIdForUpdate(@Param("id") Long id);
```

```java
// Service
@Transactional
public IssueResult issueSync(Long userId, Long couponId) {
    // 1. 쿠폰 조회 + 락 획득 (SELECT ... FOR UPDATE)
    Coupon coupon = couponRepository.findByIdForUpdate(couponId)
        .orElseThrow();

    // 2. 재고 확인
    if (coupon.soldOut()) {
        return IssueResult.SOLD_OUT;
    }

    // 3. 발급 처리
    coupon.increaseIssued();
    couponIssueRepository.save(new CouponIssue(couponId, userId));

    return IssueResult.ISSUED;
}
```

### 동작 방식

```
Thread A: SELECT ... FOR UPDATE → 락 획득 → 처리 중
Thread B: SELECT ... FOR UPDATE → 대기 (락 획득 불가)
Thread A: COMMIT → 락 해제
Thread B: 락 획득 → 처리 시작
```

### SQL

```sql
SELECT c.* FROM coupon c WHERE c.id = 100 FOR UPDATE;
-- 다른 트랜잭션은 이 row에 대해 UPDATE/DELETE/FOR UPDATE 불가
```

---

## 2. 원자적 업데이트 (Atomic Update)

### 개념

조건부 UPDATE 쿼리로 **재고 확인과 차감을 한 번에** 처리합니다.

### 구현

```java
// Repository
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
    update Coupon c
       set c.issuedQuantity = c.issuedQuantity + 1
     where c.id = :id
       and c.issuedQuantity < c.totalQuantity
""")
int tryIncreaseIssued(@Param("id") Long id);
```

```java
// Service
@Transactional
public IssueResult issueSyncAtomic(Long userId, Long couponId) {
    // 1. 원자적 업데이트 시도
    int updated = couponRepository.tryIncreaseIssued(couponId);

    if (updated == 0) {
        // 업데이트 실패 = 재고 없음
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return IssueResult.SOLD_OUT;
    }

    // 2. 발급 내역 저장
    couponIssueRepository.save(new CouponIssue(couponId, userId));

    return IssueResult.ISSUED;
}
```

### 동작 방식

```
Thread A: UPDATE ... WHERE issued < total → 1 row affected (성공)
Thread B: UPDATE ... WHERE issued < total → 1 row affected (성공)
...
Thread N: UPDATE ... WHERE issued < total → 0 row affected (재고 소진)
```

### SQL

```sql
UPDATE coupon
   SET issued_quantity = issued_quantity + 1
 WHERE id = 100
   AND issued_quantity < total_quantity;

-- affected rows = 1: 성공
-- affected rows = 0: 재고 없음 (조건 불충족)
```

---

## 비교

| 항목 | 비관적 락 | 원자적 업데이트 |
|------|----------|----------------|
| **락 방식** | 행 잠금 (FOR UPDATE) | 락 없음 |
| **동시 처리** | 순차 처리 (대기) | 병렬 처리 가능 |
| **충돌 처리** | 대기 후 처리 | 실패 시 롤백 |
| **DB 부하** | 락 관리 오버헤드 | UPDATE 쿼리만 |
| **코드 복잡도** | 단순 (일반 로직) | 반환값 체크 필요 |
| **데드락 위험** | 있음 | 없음 |

### 장단점

**비관적 락:**
- 장점: 로직이 직관적, 재고 확인 후 추가 작업 가능
- 단점: 대기 시간 발생, 데드락 위험

**원자적 업데이트:**
- 장점: 락 없이 높은 동시성, 데드락 없음
- 단점: 조건부 로직만 가능, 반환값 체크 필수

---

## 언제 어떤 방식을 사용할까?

### 비관적 락이 적합한 경우

```java
// 복잡한 비즈니스 로직이 필요한 경우
@Transactional
public void issueWithBonus(Long userId, Long couponId) {
    Coupon coupon = couponRepository.findByIdForUpdate(couponId);

    if (coupon.soldOut()) return;

    // 추가 로직들
    if (coupon.isVipOnly() && !userService.isVip(userId)) return;
    if (coupon.isFirstComeFirstServed() && coupon.issuedQuantity() >= 100) return;

    // 보너스 포인트 지급
    pointService.addBonus(userId, 1000);

    coupon.increaseIssued();
    couponIssueRepository.save(new CouponIssue(couponId, userId));
}
```

### 원자적 업데이트가 적합한 경우

```java
// 단순 재고 차감만 필요한 경우
@Transactional
public IssueResult issueSimple(Long userId, Long couponId) {
    int updated = couponRepository.tryIncreaseIssued(couponId);
    if (updated == 0) return IssueResult.SOLD_OUT;

    couponIssueRepository.save(new CouponIssue(couponId, userId));
    return IssueResult.ISSUED;
}
```

---

## 중복 발급 방지

동시성 제어와 별개로, 같은 사용자가 같은 쿠폰을 중복 발급받는 것을 방지해야 합니다.

### DB 유니크 제약

```sql
ALTER TABLE coupon_issue
ADD CONSTRAINT uk_coupon_user UNIQUE (coupon_id, user_id);
```

### 코드에서 처리

```java
@Transactional
public IssueResult issueSyncAtomic(Long userId, Long couponId) {
    // 1. 중복 발급 체크
    if (couponIssueRepository.existsByCouponIdAndUserId(couponId, userId)) {
        return IssueResult.ALREADY_ISSUED;
    }

    // 2. 원자적 업데이트
    int updated = couponRepository.tryIncreaseIssued(couponId);
    if (updated == 0) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return IssueResult.SOLD_OUT;
    }

    // 3. 발급 내역 저장 (유니크 제약 위반 시 예외)
    try {
        couponIssueRepository.save(new CouponIssue(couponId, userId));
    } catch (DataIntegrityViolationException e) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return IssueResult.ALREADY_ISSUED;
    }

    return IssueResult.ISSUED;
}
```

---

## 트랜잭션 롤백

원자적 업데이트 실패 시 트랜잭션을 롤백해야 합니다.

```java
// 방법 1: setRollbackOnly()
TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
return IssueResult.SOLD_OUT;

// 방법 2: 예외 던지기
throw new SoldOutException();
```

`setRollbackOnly()`를 사용하면 예외 없이 정상 반환하면서도 트랜잭션을 롤백할 수 있습니다.
