package api_gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Configuration
public class GatewayConfig {

    @Value("${services.customer.url:http://localhost:8081}")
    private String customerServiceUrl;

    @Value("${services.restaurant.url:http://localhost:8082}")
    private String restaurantServiceUrl;

    @Value("${services.order.url:http://localhost:8083}")
    private String orderServiceUrl;

    @Value("${services.delivery.url:http://localhost:8084}")
    private String deliveryServiceUrl;

    @Bean
    public RouterFunction<ServerResponse> routes() {
        return RouterFunctions.route()
                .path("/api/auth", builder -> builder
                        .GET("/**", proxyTo(customerServiceUrl))
                        .POST("/**", proxyTo(customerServiceUrl)))
                .path("/api/customers", builder -> builder
                        .GET("/**", proxyTo(customerServiceUrl))
                        .PUT("/**", proxyTo(customerServiceUrl)))
                .path("/api/restaurants", builder -> builder
                        .GET("/**", proxyTo(restaurantServiceUrl))
                        .POST("/**", proxyTo(restaurantServiceUrl))
                        .PUT("/**", proxyTo(restaurantServiceUrl))
                        .PATCH("/**", proxyTo(restaurantServiceUrl)))
                .path("/api/orders", builder -> builder
                        .GET("/**", proxyTo(orderServiceUrl))
                        .POST("/**", proxyTo(orderServiceUrl))
                        .PATCH("/**", proxyTo(orderServiceUrl)))
                .path("/api/deliveries", builder -> builder
                        .GET("/**", proxyTo(deliveryServiceUrl))
                        .PATCH("/**", proxyTo(deliveryServiceUrl)))
                .build();
    }

    private HandlerFunction<ServerResponse> proxyTo(String targetUrl) {
        return request -> {
            URI uri = UriComponentsBuilder
                    .fromUriString(targetUrl)
                    .path(request.path())
                    .query(request.uri().getQuery())
                    .build()
                    .toUri();

            RestClient restClient = RestClient.create();

            return restClient
                    .method(request.method())
                    .uri(uri)
                    .headers(headers -> headers.addAll(request.headers().asHttpHeaders()))
                    .body(request.body(byte[].class))
                    .exchange((req, res) -> {
                        byte[] body = res.bodyTo(byte[].class);
                        ServerResponse.BodyBuilder builder = ServerResponse
                                .status(res.getStatusCode());
                        res.getHeaders().forEach((key, values) -> {
                            if (!key.equalsIgnoreCase("Transfer-Encoding")) {
                                values.forEach(value -> builder.header(key, value));
                            }
                        });
                        if (body != null && body.length > 0) {
                            return builder.body(body);
                        } else {
                            return builder.build();
                        }
                    });
        };
    }
}