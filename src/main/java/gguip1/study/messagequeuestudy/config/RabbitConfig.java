package gguip1.study.messagequeuestudy.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "coupon.exchange";
    public static final String QUEUE = "coupon.issue.queue";
    public static final String ROUTING_KEY = "coupon.issue";

    // 테스트용 (MQ만 차이나게 테스트)
    public static final String TEST_QUEUE = "coupon.issue.test.queue";
    public static final String TEST_ROUTING_KEY = "coupon.issue.test";

    // RPC 테스트용 (MQ 거치지만 처리 완료까지 대기)
    public static final String RPC_QUEUE = "coupon.issue.rpc.queue";
    public static final String RPC_ROUTING_KEY = "coupon.issue.rpc";

    @Bean
    DirectExchange couponExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    Queue couponIssueQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    Binding couponIssueBinding(Queue couponIssueQueue, DirectExchange couponExchange) {
        return BindingBuilder.bind(couponIssueQueue).to(couponExchange).with(ROUTING_KEY);
    }

    // 테스트용 큐
    @Bean
    Queue testCouponIssueQueue() {
        return QueueBuilder.durable(TEST_QUEUE).build();
    }

    @Bean
    Binding testCouponIssueBinding(Queue testCouponIssueQueue, DirectExchange couponExchange) {
        return BindingBuilder.bind(testCouponIssueQueue).to(couponExchange).with(TEST_ROUTING_KEY);
    }

    // RPC 테스트용 큐
    @Bean
    Queue rpcCouponIssueQueue() {
        return QueueBuilder.durable(RPC_QUEUE).build();
    }

    @Bean
    Binding rpcCouponIssueBinding(Queue rpcCouponIssueQueue, DirectExchange couponExchange) {
        return BindingBuilder.bind(rpcCouponIssueQueue).to(couponExchange).with(RPC_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
