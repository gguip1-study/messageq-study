package gguip1.study.messagequeuestudy.test;

import gguip1.study.messagequeuestudy.config.RabbitConfig;
import gguip1.study.messagequeuestudy.service.CouponIssueService;
import jakarta.validation.constraints.NotNull;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * MQ 적용/미적용 공정한 비교 테스트용 API
 *
 * - /test/issue-direct: MQ 미적용 (API에서 직접 쿠폰 발급)
 * - /test/issue-mq: MQ 적용 (API는 MQ 발행만, Consumer가 쿠폰 발급) - 처리 완료 안 기다림
 * - /test/issue-mq-sync: MQ 적용 + RPC (MQ 거치지만 처리 완료까지 대기) - 공정한 비교용
 *
 * 두 API 모두 동일한 쿠폰 발급 로직(issueSyncAtomic)을 수행하며,
 * 차이점은 오직 MQ를 거치느냐 아니냐뿐임.
 */
@RestController
@RequestMapping("/test")
@Validated
public class TestCouponController {

    private final CouponIssueService couponIssueService;
    private final RabbitTemplate rabbitTemplate;

    public TestCouponController(CouponIssueService couponIssueService, RabbitTemplate rabbitTemplate) {
        this.couponIssueService = couponIssueService;
        this.rabbitTemplate = rabbitTemplate;
    }

    public record IssueReq(@NotNull Long userId, @NotNull Long couponId) {}

    /**
     * MQ 미적용: API에서 직접 쿠폰 발급
     */
    @PostMapping("/issue-direct")
    public ResponseEntity<?> issueDirect(@RequestBody @Validated IssueReq req) {
        var result = couponIssueService.issueSyncAtomic(req.userId(), req.couponId());
        return ResponseEntity.ok(Map.of(
            "mode", "DIRECT",
            "result", result.name(),
            "userId", req.userId(),
            "couponId", req.couponId()
        ));
    }

    /**
     * MQ 적용: API는 MQ 발행만, Consumer가 쿠폰 발급
     * - API에서 DB 사용 안 함
     * - 응답은 "MQ에 넣었음"만 의미
     */
    @PostMapping("/issue-mq")
    public ResponseEntity<?> issueMq(@RequestBody @Validated IssueReq req) {
        rabbitTemplate.convertAndSend(
            RabbitConfig.EXCHANGE,
            RabbitConfig.TEST_ROUTING_KEY,
            new TestCouponMessage(req.userId(), req.couponId())
        );
        return ResponseEntity.ok(Map.of(
            "mode", "MQ",
            "status", "QUEUED",
            "userId", req.userId(),
            "couponId", req.couponId()
        ));
    }

    /**
     * MQ 적용 + RPC: MQ를 거치지만 처리 완료까지 대기
     * - API에서 DB 사용 안 함
     * - Consumer가 처리 완료하면 응답 반환
     * - issue-direct와 공정한 비교 가능 (둘 다 처리 완료 후 응답)
     */
    @PostMapping("/issue-mq-sync")
    public ResponseEntity<?> issueMqSync(@RequestBody @Validated IssueReq req) {
        // RPC 패턴: 메시지 보내고 응답 올 때까지 대기
        var response = (RpcCouponResponse) rabbitTemplate.convertSendAndReceive(
            RabbitConfig.EXCHANGE,
            RabbitConfig.RPC_ROUTING_KEY,
            new TestCouponMessage(req.userId(), req.couponId())
        );

        if (response == null) {
            return ResponseEntity.status(500).body(Map.of(
                "mode", "MQ_RPC",
                "error", "TIMEOUT",
                "userId", req.userId(),
                "couponId", req.couponId()
            ));
        }

        return ResponseEntity.ok(Map.of(
            "mode", "MQ_RPC",
            "result", response.result(),
            "userId", req.userId(),
            "couponId", req.couponId()
        ));
    }
}
