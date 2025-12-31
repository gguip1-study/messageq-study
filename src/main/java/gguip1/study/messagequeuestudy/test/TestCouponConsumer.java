package gguip1.study.messagequeuestudy.test;

import gguip1.study.messagequeuestudy.config.RabbitConfig;
import gguip1.study.messagequeuestudy.service.CouponIssueService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TestCouponConsumer {

    private final CouponIssueService couponIssueService;

    public TestCouponConsumer(CouponIssueService couponIssueService) {
        this.couponIssueService = couponIssueService;
    }

    @RabbitListener(queues = RabbitConfig.TEST_QUEUE)
    public void onMessage(TestCouponMessage msg) {
        // MQ 미적용과 동일한 로직 수행 (DB 상태 저장 없음)
        couponIssueService.issueSyncAtomic(msg.userId(), msg.couponId());
    }
}
