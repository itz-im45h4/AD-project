package com.ridelink.ride.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ infrastructure configuration for Ride Management Service.
 *
 * <p>Declares the AMQP topology needed to consume payment lifecycle events:
 * <ul>
 *   <li><strong>Exchange:</strong> {@code ridelink.payment} (Topic Exchange)</li>
 *   <li><strong>Queue:</strong> {@code ridelink.payment-recorded} (Durable queue)</li>
 *   <li><strong>Routing Key:</strong> {@code payment.recorded}</li>
 *   <li><strong>Message Converter:</strong> Jackson JSON converter with inferred type mapping</li>
 * </ul>
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "ridelink.payment";
    public static final String QUEUE = "ridelink.payment-recorded";
    public static final String ROUTING_KEY = "payment.recorded";

    /** Declares topic exchange for payment events. */
    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(EXCHANGE);
    }

    /** Declares durable queue surviving RabbitMQ broker restarts. */
    @Bean
    public Queue paymentRecordedQueue() {
        return new Queue(QUEUE, true);
    }

    /** Binds the durable queue to the topic exchange using the payment.recorded routing key. */
    @Bean
    public Binding paymentBinding() {
        return BindingBuilder
                .bind(paymentRecordedQueue())
                .to(paymentExchange())
                .with(ROUTING_KEY);
    }

    /**
     * Configures JSON message deserialization for incoming AMQP messages.
     * Uses type inference to map JSON payloads into strongly-typed event records.
     */
    @Bean
    public MessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        converter.getJavaTypeMapper().addTrustedPackages("*");
        return converter;
    }
}
