package org.camunda.optimize.rest.util;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;

/**
 * @author Askar Akhmerov
 */
public class AuthenticationUtil {

  public static String PROXY_OPTIMIZE_AUTHORIZATION_HEADER = "X-Optimize-Authorization";

  public static String getToken(ContainerRequestContext requestContext) {
    // Get the HTTP Authorization header from the request
    String authorizationHeader =
      requestContext.getHeaderString(PROXY_OPTIMIZE_AUTHORIZATION_HEADER);
    if(authorizationHeader == null) {
      // no proxy server used and we can use the default authorization header
      authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    }
    return extractTokenFromAuthorizationHeader(authorizationHeader);
  }

  private static String extractTokenFromAuthorizationHeader(String authorizationHeader) {
    // Check if the HTTP Authorization header is present and formatted correctly
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
      throw new NotAuthorizedException("Authorization header must be provided");
    }

    // Extract the token from the HTTP Authorization header
    return authorizationHeader.substring("Bearer".length()).trim();
  }
}
