package org.camunda.optimize.rest.util;

import com.auth0.jwt.JWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import java.util.Map;

public class AuthenticationUtil {

  public static String OPTIMIZE_AUTHORIZATION = "X-Optimize-Authorization";
  private static Logger logger = LoggerFactory.getLogger(AuthenticationUtil.class);

  public static String getToken(ContainerRequestContext requestContext) {
    // Get the HTTP Authorization header from the request
    String tokenValue =
      requestContext.getHeaderString(OPTIMIZE_AUTHORIZATION);
    if (tokenValue == null) {
      // no proxy server used and we can use the default authorization header
      tokenValue = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    }

    if (tokenValue == null) {
      /* If token not found get it from request cookies */
      for (Map.Entry<String, Cookie> c : requestContext.getCookies().entrySet()) {
        if (OPTIMIZE_AUTHORIZATION.equals(c.getKey())) {
          tokenValue = c.getValue().getValue();
        }
      }
    }
    return extractTokenFromAuthorizationValue(tokenValue);
  }

  public static String getSessionIssuer(String token) {
    try {
      JWT decoded = JWT.decode(token);
      return decoded.getIssuer();
    } catch (Exception e) {
      String errorMessage = "Could not decode security token to extract issuer!";
      logger.debug(errorMessage, e);
      throw new NotAuthorizedException(errorMessage);
    }
  }

  public static String getRequestUser(ContainerRequestContext requestContext) {
    String token = AuthenticationUtil.getToken(requestContext);
    return getSessionIssuer(token);
  }

  private static String extractTokenFromAuthorizationValue(String authorizationHeader) {
    // Check if the HTTP Authorization header is present and formatted correctly
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
      throw new NotAuthorizedException("Authorization header must be provided");
    }

    // Extract the token from the HTTP Authorization header
    return authorizationHeader.substring("Bearer".length()).trim();
  }
}
