package gguip1.study.messagequeuestudy.test;

import gguip1.study.messagequeuestudy.config.RabbitConfig;
import gguip1.study.messagequeuestudy.service.CouponIssueService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RPC 패턴 Consumer - 처리 결과를 반환함
 *
 * @RabbitListener의 반환값이 자동으로 replyTo 큐로 전송됨
 */
@Component
public class RpcCouponConsumer {

    private final CouponIssueService couponIssueService;

    public RpcCouponConsumer(CouponIssueService couponIssueService) {
        this.couponIssueService = couponIssueService;
    }

    @RabbitListener(queues = RabbitConfig.RPC_QUEUE)
    public RpcCouponResponse onMessage(TestCouponMessage msg) {
        // MQ 미적용과 동일한 로직 수행
        var result = couponIssueService.issueSyncAtomic(msg.userId(), msg.couponId());
        return new RpcCouponResponse(result.name());
    }
}
