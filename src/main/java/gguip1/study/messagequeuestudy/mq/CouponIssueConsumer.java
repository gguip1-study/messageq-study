package gguip1.study.messagequeuestudy.mq;

import gguip1.study.messagequeuestudy.config.RabbitConfig;
import gguip1.study.messagequeuestudy.domain.CouponIssueRequest;
import gguip1.study.messagequeuestudy.repository.CouponIssueRequestRepository;
import gguip1.study.messagequeuestudy.service.CouponIssueService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CouponIssueConsumer {

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponIssueService couponIssueService;

    public CouponIssueConsumer(
            CouponIssueRequestRepository couponIssueRequestRepository,
            CouponIssueService couponIssueService) {
        this.couponIssueRequestRepository = couponIssueRequestRepository;
        this.couponIssueService = couponIssueService;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
//    @Transactional
    public void onMessage(CouponIssueMessage msg) {
        var reqOpt = couponIssueRequestRepository.findByRequestId(msg.requestId());
        if (reqOpt.isEmpty()) return;

        var req = reqOpt.get();
        if (req.getStatus() != CouponIssueRequest.Status.PENDING) return;

//        try { Thread.sleep(200); } catch (InterruptedException ignored) {}

        var result = couponIssueService.issueSyncAtomic(msg.userId(), msg.couponId());

        switch (result) {
            case ISSUED -> req.success();
            case SOLD_OUT -> req.fail("SOLD_OUT");
            case ALREADY_ISSUED -> req.fail("ALREADY_ISSUED");
            case COUPON_NOT_FOUND -> req.fail("COUPON_NOT_FOUND");
        }

        couponIssueRequestRepository.save(req);
    }
}
