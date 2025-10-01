package dev.dealcart.gateway.config;

import dev.dealcart.gateway.interceptor.MdcRequestIdInterceptor;
import dev.dealcart.v1.CheckoutGrpc;
import dev.dealcart.v1.VendorPricingGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for gRPC client connections to backend services.
 */
@Configuration
public class GrpcClientConfig {
    private static final Logger logger = LoggerFactory.getLogger(GrpcClientConfig.class);
    
    @Value("${grpc.pricing.host}")
    private String pricingHost;
    
    @Value("${grpc.pricing.port}")
    private int pricingPort;
    
    @Value("${grpc.checkout.host}")
    private String checkoutHost;
    
    @Value("${grpc.checkout.port}")
    private int checkoutPort;
    
    @Value("${grpc-client.deadline-seconds}")
    private long deadlineSeconds;
    
    private ManagedChannel pricingChannel;
    private ManagedChannel checkoutChannel;
    
    @Bean
    public ManagedChannel pricingChannel() {
        pricingChannel = ManagedChannelBuilder
                .forAddress(pricingHost, pricingPort)
                .usePlaintext()
                .intercept(new MdcRequestIdInterceptor())
                .build();
        
        logger.info("Created pricing channel to {}:{}", pricingHost, pricingPort);
        return pricingChannel;
    }
    
    @Bean
    public ManagedChannel checkoutChannel() {
        checkoutChannel = ManagedChannelBuilder
                .forAddress(checkoutHost, checkoutPort)
                .usePlaintext()
                .intercept(new MdcRequestIdInterceptor())
                .build();
        
        logger.info("Created checkout channel to {}:{}", checkoutHost, checkoutPort);
        return checkoutChannel;
    }
    
    @Bean
    public VendorPricingGrpc.VendorPricingStub pricingStub(ManagedChannel pricingChannel) {
        return VendorPricingGrpc.newStub(pricingChannel)
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
    }
    
    @Bean
    public CheckoutGrpc.CheckoutBlockingStub checkoutStub(ManagedChannel checkoutChannel) {
        return CheckoutGrpc.newBlockingStub(checkoutChannel)
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
    }
    
    @Bean
    public CheckoutGrpc.CheckoutStub checkoutAsyncStub(ManagedChannel checkoutChannel) {
        return CheckoutGrpc.newStub(checkoutChannel)
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
    }
    
    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down gRPC channels...");
        
        if (pricingChannel != null) {
            try {
                pricingChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while shutting down pricing channel");
            }
        }
        
        if (checkoutChannel != null) {
            try {
                checkoutChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while shutting down checkout channel");
            }
        }
    }
}

