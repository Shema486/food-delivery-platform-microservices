package order_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String DELIVERY_EXCHANGE    = "delivery.exchange";
    public static final String ORDER_PLACED_ROOT_KEY    = "order.placed";
    public static final String DELIVERY_STATUS_ROOt_KEY = "delivery.status.updated";
    public static final String ORDER_DELIVERY_UPDATED_STATUS_QUEUE = "order.delivery.status.queue";
    public static final String ORDER_CANCELLED_KEY = "order.cancelled";


    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange deliveryExchange() {
        return new TopicExchange(DELIVERY_EXCHANGE);
    }

    @Bean
    public Queue orderDeliveryUpdated() {
        return new Queue(ORDER_DELIVERY_UPDATED_STATUS_QUEUE);
    }

    @Bean
    public Binding orderDeliveryUpdatedBinding(Queue orderDeliveryUpdated,TopicExchange deliveryExchange) {
        return BindingBuilder
                .bind(orderDeliveryUpdated)
                .to(deliveryExchange)
                .with(DELIVERY_STATUS_ROOt_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}