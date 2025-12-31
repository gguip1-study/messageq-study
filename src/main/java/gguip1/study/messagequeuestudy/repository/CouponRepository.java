package gguip1.study.messagequeuestudy.repository;

import gguip1.study.messagequeuestudy.domain.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    // 선착순 병목을 "확실히 체감"하려고 쿠폰 row를 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Coupon c
           set c.issuedQuantity = c.issuedQuantity + 1
         where c.id = :id
           and c.issuedQuantity < c.totalQuantity
    """)
    int tryIncreaseIssued(@Param("id") Long id);
}