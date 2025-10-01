package dev.dealcart.gateway.interceptor;

import io.grpc.*;
import org.slf4j.MDC;

/**
 * gRPC client interceptor that propagates request ID from MDC to gRPC metadata.
 */
public class MdcRequestIdInterceptor implements ClientInterceptor {
    private static final Metadata.Key<String> REQUEST_ID_KEY =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
    
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, 
            CallOptions callOptions, 
            Channel next) {
        
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {
            
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String requestId = MDC.get("request-id");
                if (requestId != null) {
                    headers.put(REQUEST_ID_KEY, requestId);
                }
                super.start(responseListener, headers);
            }
        };
    }
}

