package dev.dealcart.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Edge Gateway - HTTP/SSE bridge to gRPC backend services.
 */
@SpringBootApplication
public class EdgeGatewayApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EdgeGatewayApplication.class, args);
    }
}

