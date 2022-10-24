package net.accelbyte.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import net.accelbyte.platform.exception.TokenIsExpiredException;
import net.accelbyte.platform.security.OAuthToken;
import net.accelbyte.platform.security.Permission;
import net.accelbyte.platform.security.service.OAuthService;
import org.lognet.springboot.grpc.GRpcGlobalInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;

@Slf4j
@GRpcGlobalInterceptor
@Order(20)
public class AuthorizationInterceptor implements ServerInterceptor {

    @Value("${justice.grpc.interceptor.auth.enabled:true}")
    private boolean enabled;

    private String namespace;

    private OAuthService oAuthService;

    private Permission requiredPermission;

    @Autowired
    public AuthorizationInterceptor(OAuthService oAuthService, @Value("${app.config.resource_name}") String resourceName, @Value("${app.config.namespace}") String namespace) {
        this.oAuthService = oAuthService;
        this.requiredPermission = new Permission("NAMESPACE:" + namespace + ":" + resourceName, 2);
        this.namespace = namespace;
        log.info("AuthorizationInterceptor initialized");
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if(enabled) {
            final String auth_token = headers.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER));

            if (auth_token != null) {
                try {
                    String token = auth_token.split(" ")[1];
                    OAuthToken oAuthToken = oAuthService.getOAuthToken(token);
                    if(oAuthToken == null || !oAuthService.validateTokenPermission(oAuthToken, requiredPermission, namespace, null)) {
                        unAuthorizedCall(call, headers);
                    }
                } catch (TokenIsExpiredException e) {
                    log.warn("Caught TokenIsExpiredException, throw unauthorized exception");
                    unAuthorizedCall(call, headers);
                }
            } else {
                unAuthorizedCall(call, headers);
            }
        }
         return next.startCall(call, headers);
    }

    private <ReqT, RespT> void unAuthorizedCall(ServerCall<ReqT, RespT> call, Metadata headers) {
        call.close(Status.UNAUTHENTICATED.withDescription("call not authorized"), headers);
    }
}
