package org.camunda.optimize.rest.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import java.util.Map;
import java.util.Optional;

public class AuthenticationUtil {

  public static String OPTIMIZE_AUTHORIZATION = "X-Optimize-Authorization";
  private final static Logger logger = LoggerFactory.getLogger(AuthenticationUtil.class);

  public static String getToken(ContainerRequestContext requestContext) {
    String authorizationHeader = extractAuthorizationCookie(requestContext);
    return extractTokenFromAuthorizationValue(authorizationHeader);
  }

  public static Optional<String> getSessionIssuer(String token) {
    try {
      final DecodedJWT decoded = JWT.decode(token);
      return Optional.of(decoded.getIssuer());
    } catch (Exception e) {
      String errorMessage = "Could not decode security token to extract issuer!";
      logger.debug(errorMessage, e);
    }
    return Optional.empty();
  }

  public static String getRequestUser(ContainerRequestContext requestContext) {
    String token = AuthenticationUtil.getToken(requestContext);
    Optional<String> sessionIssuer = getSessionIssuer(token);
    return sessionIssuer.orElseThrow(() -> new NotAuthorizedException("Could not extract request user!"));
  }

  private static String extractAuthorizationCookie(ContainerRequestContext requestContext) {
    // load just issued token if set by previous filter
    String authorizationHeader = (String) requestContext.getProperty(OPTIMIZE_AUTHORIZATION);
    if (authorizationHeader == null) {
      /* Check cookies for optimize authorization header*/
      for (Map.Entry<String, Cookie> c : requestContext.getCookies().entrySet()) {
        if (OPTIMIZE_AUTHORIZATION.equals(c.getKey())) {
          authorizationHeader = c.getValue().getValue();
        }
      }
    }
    return authorizationHeader;
  }

  public static String extractTokenFromAuthorizationValue(String authorizationHeader) {
    // Check if the HTTP Authorization header is present and formatted correctly
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
      throw new NotAuthorizedException("Authorization header must be provided");
    }

    // Extract the token from the HTTP Authorization header
    return authorizationHeader.substring("Bearer".length()).trim();
  }

  public static NewCookie createDeleteOptimizeAuthCookie() {
    logger.trace("Deleting Optimize authentication cookie.");
    return new NewCookie(OPTIMIZE_AUTHORIZATION, "", "/", null, "delete cookie", 0, false);
  }

  public static NewCookie createNewOptimizeAuthCookie(String securityToken) {
    logger.trace("Creating Optimize authentication cookie.");
    return new NewCookie(
      OPTIMIZE_AUTHORIZATION,
      createOptimizeAuthCookieValue(securityToken),
      "/",
      null,
      1,
      null,
      -1,
      null,
      false,
      false
    );
  }

  public static String createOptimizeAuthCookieValue(String securityToken) {
    return String.format("Bearer %s", securityToken);
  }

}
