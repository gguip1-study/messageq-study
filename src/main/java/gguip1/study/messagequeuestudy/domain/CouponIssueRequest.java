package gguip1.study.messagequeuestudy.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "coupon_issue_request", uniqueConstraints = @UniqueConstraint(name="uk_request_id", columnNames = {"request_id"}))
public class CouponIssueRequest {

    public enum Status { PENDING, SUCCESS, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="request_id", nullable=false, length=36)
    private String requestId;

    @Column(name="coupon_id", nullable=false)
    private Long couponId;

    @Column(name="user_id", nullable=false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable=false, length=20)
    private Status status;

    @Column(name="reason", length=255)
    private String reason;

    @Column(name="created_at", nullable=false)
    private Instant createdAt;

    @Column(name="updated_at", nullable=false)
    private Instant updatedAt;

    protected CouponIssueRequest() {}

    public CouponIssueRequest(String requestId, Long couponId, Long userId) {
        this.requestId = requestId;
        this.couponId = couponId;
        this.userId = userId;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getRequestId() { return requestId; }
    public Status getStatus() { return status; }
    public String getReason() { return reason; }
    public Long getCouponId() { return couponId; }
    public Long getUserId() { return userId; }

    public void success() { this.status = Status.SUCCESS; this.reason = null; this.updatedAt = Instant.now(); }
    public void fail(String reason) { this.status = Status.FAILED; this.reason = reason; this.updatedAt = Instant.now(); }

}
