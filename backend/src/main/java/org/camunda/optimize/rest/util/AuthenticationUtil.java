package org.camunda.optimize.rest.util;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
public class AuthenticationUtil {

  public static String OPTIMIZE_AUTHORIZATION = "X-Optimize-Authorization";

  public static String getToken(ContainerRequestContext requestContext) {
    // Get the HTTP Authorization header from the request
    String tokenValue =
      requestContext.getHeaderString(OPTIMIZE_AUTHORIZATION);
    if(tokenValue == null) {
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

  private static String extractTokenFromAuthorizationValue(String authorizationHeader) {
    // Check if the HTTP Authorization header is present and formatted correctly
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
      throw new NotAuthorizedException("Authorization header must be provided");
    }

    // Extract the token from the HTTP Authorization header
    return authorizationHeader.substring("Bearer".length()).trim();
  }
}
