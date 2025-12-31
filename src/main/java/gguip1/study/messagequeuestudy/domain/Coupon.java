package gguip1.study.messagequeuestudy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "coupon")
public class Coupon {
    @Id
    private Long id;

    private String name;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "issued_quantity", nullable = false)
    private Integer issuedQuantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Coupon() {}

    public Long getId() { return id; }
    public Integer getTotalQuantity() { return totalQuantity; }
    public Integer getIssuedQuantity() { return issuedQuantity; }

    public boolean soldOut() {
        return issuedQuantity >= totalQuantity;
    }

    public void increaseIssued() { this.issuedQuantity += 1;}
}
