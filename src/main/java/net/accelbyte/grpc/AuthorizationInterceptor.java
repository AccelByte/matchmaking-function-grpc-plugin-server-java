package net.accelbyte.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import lombok.extern.slf4j.Slf4j;
import net.accelbyte.platform.exception.TokenIsExpiredException;
import net.accelbyte.platform.exception.UnauthorizedException;
import net.accelbyte.platform.security.OAuthToken;
import net.accelbyte.platform.security.service.OAuthService;
import org.lognet.springboot.grpc.GRpcGlobalInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@GRpcGlobalInterceptor

public class AuthorizationInterceptor implements ServerInterceptor {

    @Value("${justice.grpc.interceptor.auth.enabled:true}")
    private boolean enabled;
    private OAuthService oAuthService;

    @Autowired
    public AuthorizationInterceptor(OAuthService oAuthService) {
        this.oAuthService = oAuthService;
        log.info("AuthorizationInterceptor initialized");
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if(enabled) {
            final String auth_token = headers.get(Metadata.Key.of("auth_token", Metadata.ASCII_STRING_MARSHALLER));

            if (auth_token != null) {
                try {
                    OAuthToken oAuthToken = oAuthService.getOAuthToken(auth_token);
                } catch (TokenIsExpiredException e) {
                    log.warn("Caught TokenIsExpiredException, throw unauthorized exception");
                    throw new UnauthorizedException();
                }
            } else {
                throw new UnauthorizedException();
            }
        }
        return next.startCall(call, headers);
    }
}
