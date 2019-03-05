package org.camunda.optimize.rest.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class AuthenticationUtil {

  private static final Logger logger = LoggerFactory.getLogger(AuthenticationUtil.class);
  public static final String AUTH_COOKIE_TOKEN_VALUE_PREFIX = "Bearer ";

  public static String OPTIMIZE_AUTHORIZATION = "X-Optimize-Authorization";

  public static Optional<String> getToken(ContainerRequestContext requestContext) {
    return extractAuthorizationCookie(requestContext)
      .map(AuthenticationUtil::extractTokenFromAuthorizationValueOrFailNotAuthorized);
  }

  private static Optional<Date> getTokenIssuedAt(String token) {
    return getTokenAttribute(token, DecodedJWT::getIssuedAt);
  }

  private static Optional<String> getTokenSubject(String token) {
    return getTokenAttribute(token, DecodedJWT::getSubject);
  }

  private static <T> Optional<T> getTokenAttribute(final String token,
                                                   final Function<DecodedJWT, T> getTokenAttributeFunction) {
    try {
      final DecodedJWT decoded = JWT.decode(token);
      return Optional.of(getTokenAttributeFunction.apply(decoded));
    } catch (Exception e) {
      logger.debug("Could not decode security token to extract attribute!", e);
    }
    return Optional.empty();
  }

  public static String getRequestUserOrFailNotAuthorized(ContainerRequestContext requestContext) {
    return getToken(requestContext)
      .flatMap(AuthenticationUtil::getTokenSubject)
      .orElseThrow(() -> new NotAuthorizedException("Could not extract request user!"));
  }

  private static Optional<String> extractAuthorizationCookie(ContainerRequestContext requestContext) {
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
    return Optional.ofNullable(authorizationHeader);
  }

  public static String extractTokenFromAuthorizationValueOrFailNotAuthorized(String authorizationHeader) {
    // Check if the HTTP Authorization header is present and formatted correctly
    if (authorizationHeader == null || !authorizationHeader.startsWith(AUTH_COOKIE_TOKEN_VALUE_PREFIX)) {
      throw new NotAuthorizedException("Authorization header must be provided");
    }

    // Extract the token from the HTTP Authorization header
    return authorizationHeader.substring(AUTH_COOKIE_TOKEN_VALUE_PREFIX.length()).trim();
  }

  public static NewCookie createDeleteOptimizeAuthCookie() {
    logger.trace("Deleting Optimize authentication cookie.");
    return new NewCookie(
      OPTIMIZE_AUTHORIZATION, "", "/", null, "delete cookie", 0, false
    );
  }

  public static NewCookie createNewOptimizeAuthCookie(final String securityToken, final int lifeTimeMinutes) {
    logger.trace("Creating Optimize authentication cookie.");
    return new NewCookie(
      OPTIMIZE_AUTHORIZATION,
      createOptimizeAuthCookieValue(securityToken),
      "/",
      null,
      1,
      null,
      -1,
      getTokenIssuedAt(securityToken)
        .map(Date::toInstant)
        .map(issuedAt -> issuedAt.plus(lifeTimeMinutes, ChronoUnit.MINUTES))
        .map(Date::from)
        .orElse(null),
      false,
      false
    );
  }

  public static String createOptimizeAuthCookieValue(final String securityToken) {
    return AUTH_COOKIE_TOKEN_VALUE_PREFIX + securityToken;
  }

}
