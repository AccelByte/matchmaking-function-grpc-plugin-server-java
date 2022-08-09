package net.accelbyte.matchmaking.matchfunction.interceptor;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.accelbyte.platform.exception.TokenIsExpiredException;
import net.accelbyte.platform.exception.UnauthorizedException;
import net.accelbyte.platform.security.OAuthToken;
import net.accelbyte.platform.security.service.OAuthService;
import org.lognet.springboot.grpc.GRpcGlobalInterceptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.logging.Logger;

@GRpcGlobalInterceptor
public class AuthorizationInterceptor implements ServerInterceptor {
    private static final Logger logger = Logger.getLogger(AuthorizationInterceptor.class.getName());

    @Autowired
    OAuthService oAuthService;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        logger.info("start authorization interceptor");

        final String auth_token = headers.get(Metadata.Key.of("auth_token", Metadata.ASCII_STRING_MARSHALLER));

        if(auth_token != null) {
            try {
                OAuthToken oAuthToken = oAuthService.getOAuthToken(auth_token);
            } catch (TokenIsExpiredException e){
                logger.warning("caught TokenIsExpiredException, throw unauthorized exception");
                throw new UnauthorizedException();
            }
        } else {
            throw new UnauthorizedException();
        }

        return next.startCall(call, headers);
    }
}
