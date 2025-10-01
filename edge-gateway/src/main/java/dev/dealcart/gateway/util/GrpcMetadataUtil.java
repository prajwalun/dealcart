package dev.dealcart.gateway.util;

import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Utility for propagating request metadata to gRPC calls.
 */
@Component
public class GrpcMetadataUtil {
    private static final Metadata.Key<String> REQUEST_ID_KEY =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
    
    /**
     * Attach request ID from MDC to gRPC stub.
     */
    public <T extends AbstractStub<T>> T attachRequestId(T stub) {
        String requestId = MDC.get("request-id");
        if (requestId != null) {
            Metadata metadata = new Metadata();
            metadata.put(REQUEST_ID_KEY, requestId);
            return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
        }
        return stub;
    }
}

