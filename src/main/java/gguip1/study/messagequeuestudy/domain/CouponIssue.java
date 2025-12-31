package gguip1.study.messagequeuestudy.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "coupon_issue", uniqueConstraints = @UniqueConstraint(name="uk_coupon_user", columnNames = {"coupon_id", "user_id"}))
public class CouponIssue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="coupon_id", nullable = false)
    private Long couponId;

    @Column(name="user_id", nullable = false)
    private Long userId;

//    @Column(name="issued_at", nullable = false)
//    private Instant issuedAt;

    protected CouponIssue() {}
    public CouponIssue(Long couponId, Long userId) {
        this.couponId = couponId;
        this.userId = userId;
//        this.issuedAt = Instant.now();
    }
}
