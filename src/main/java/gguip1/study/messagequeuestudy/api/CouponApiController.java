package gguip1.study.messagequeuestudy.api;

import gguip1.study.messagequeuestudy.domain.CouponIssueRequest;
import gguip1.study.messagequeuestudy.mq.CouponIssueMessage;
import gguip1.study.messagequeuestudy.mq.CouponPublisher;
import gguip1.study.messagequeuestudy.repository.CouponIssueRepository;
import gguip1.study.messagequeuestudy.repository.CouponIssueRequestRepository;
import gguip1.study.messagequeuestudy.service.CouponIssueService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/coupons")
@Validated
public class CouponApiController {

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponIssueService couponIssueService;
    private final CouponPublisher couponPublisher;

    public CouponApiController(
            CouponIssueRequestRepository couponIssueRequestRepository,
            CouponIssueService couponIssueService,
            CouponPublisher couponPublisher) {
        this.couponIssueRequestRepository = couponIssueRequestRepository;
        this.couponIssueService = couponIssueService;
        this.couponPublisher = couponPublisher;
    }

    public record IssueReq(@NotNull Long userId, @NotNull Long couponId) {}

    @PostMapping("/issue-sync")
    public ResponseEntity<?> issueSync(@RequestBody @Validated IssueReq req) {
        var r = couponIssueService.issueSync(req.userId(), req.couponId());
        return ResponseEntity.ok(Map.of(
                "mode", "SYNC",
                "result", r.name(),
                "userId", req.userId(),
                "couponId", req.couponId()
        ));
    }

    @PostMapping("/issue-async")
    public ResponseEntity<?> issueAsync(@RequestBody @Validated IssueReq req) {
        String requestId = UUID.randomUUID().toString();

        var saved = couponIssueRequestRepository.save(
                new CouponIssueRequest(requestId, req.couponId(), req.userId())
        );

        couponPublisher.publish(new CouponIssueMessage(requestId, req.userId(), req.couponId()));

        return ResponseEntity.ok(Map.of(
                "mode", "MQ_ACCEPTED",
                "requestId", saved.getRequestId(),
                "status", saved.getStatus().name(),
                "userId", req.userId(),
                "couponId", req.couponId()
        ));
    }

    @PostMapping("/issue-sync-atomic")
    public ResponseEntity<?> issueSyncAtomic(@RequestBody @Validated IssueReq req) {
        var r = couponIssueService.issueSyncAtomic(req.userId(), req.couponId());
        return ResponseEntity.ok(Map.of(
            "mode", "SYNC_ATOMIC_UPDATE",
            "result", r.name(),
            "userId", req.userId(),
            "couponId", req.couponId()
        ));
    }

    @GetMapping("/requests/{requestId}")
    public ResponseEntity<?> getRequest(@PathVariable String requestId) {
        var req = couponIssueRequestRepository.findByRequestId(requestId)
                .orElse(null);
        if (req == null) {
            return ResponseEntity.status(404).body(Map.of("requestId", requestId, "status", "NOT_FOUND"));
        }
        return ResponseEntity.ok(Map.of(
                "requestId", req.getRequestId(),
                "status", req.getStatus().name(),
                "reason", req.getReason(),
                "userId", req.getUserId(),
                "couponId", req.getCouponId()
        ));
    }
}
