package gguip1.study.messagequeuestudy.repository;

import gguip1.study.messagequeuestudy.domain.CouponIssueRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponIssueRequestRepository extends JpaRepository<CouponIssueRequest, Long> {
    Optional<CouponIssueRequest> findByRequestId(String requestId);
}

