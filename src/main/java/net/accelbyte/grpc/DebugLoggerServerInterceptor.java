package net.accelbyte.grpc;

import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.lognet.springboot.grpc.GRpcGlobalInterceptor;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@GRpcGlobalInterceptor
public class DebugLoggerServerInterceptor implements ServerInterceptor {
    private static final String REQUEST = "REQUEST";
    private static final String RESPONSE = "RESPONSE";

    @Value("${plugin.grpc.server.interceptor.debug-logger.enabled:false}")
    private boolean enabled;

    public DebugLoggerServerInterceptor() {
        log.info("LoggingServerInterceptor initialized");
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        if(enabled) {
            logMethod(REQUEST, call.getMethodDescriptor());
            logHeaders(REQUEST, headers);
        }
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(
                next.startCall(new SimpleForwardingServerCall<ReqT, RespT>(call) {

                    @Override
                    public void sendHeaders(Metadata responseHeaders) {
                        if(enabled) {
                            logMethod(RESPONSE, call.getMethodDescriptor());
                            logHeaders(RESPONSE, responseHeaders);
                        }
                        super.sendHeaders(responseHeaders);
                    }

                    @Override
                    public void sendMessage(RespT message) {
                        if(enabled) {
                            logMessage(RESPONSE, message);
                        }
                        super.sendMessage(message);
                    }
                }, headers)) {

            @Override
            public void onMessage(ReqT message) {
                if(enabled) {
                    logMessage(REQUEST, message);
                }
                super.onMessage(message);
            }

            @Override
            public void onCancel() {
                if(enabled) logCancellation(call.getMethodDescriptor());
                super.onCancel();

            }

        };
    }

    private <ReqT, RespT> void logMethod(String type, MethodDescriptor<ReqT, RespT> method) {
        log.info("{} PATH: {}", type, method.getFullMethodName());
    }

    private void logHeaders(String type, Metadata headers) {
        log.info("{} HEADERS: {}}", type, headers);
    }

    private <T> void logMessage(String type, T message) {
        log.info("{} MESSAGE: {}", type, message);
    }

    private <ReqT, RespT> void logCancellation(MethodDescriptor<ReqT, RespT> method) {
        log.info("CALL CANCELLED for method {}", method.getFullMethodName());
    }

}
