package gguip1.study.messagequeuestudy.mq;

import gguip1.study.messagequeuestudy.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class CouponPublisher {
    private final RabbitTemplate rabbitTemplate;

    public CouponPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(CouponIssueMessage msg) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, msg);
    }
}
