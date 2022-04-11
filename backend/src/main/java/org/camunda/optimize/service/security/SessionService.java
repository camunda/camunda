/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class SessionService implements ConfigurationReloadable {

  private static final String ISSUER = "Optimize-" + Version.RAW_VERSION;
  private static final byte[] DEFAULT_SECRET_BYTES = new byte[64];

  private final TerminatedSessionService terminatedSessionService;
  private final ConfigurationService configurationService;
  private final List<SessionListener> sessionListeners;

  private Algorithm hashingAlgorithm;
  private JWTVerifier jwtVerifier;

  static {
    new SecureRandom().nextBytes(DEFAULT_SECRET_BYTES);
  }

  public SessionService(final TerminatedSessionService terminatedSessionService,
                        final ConfigurationService configurationService,
                        final List<SessionListener> sessionListeners) {
    this.terminatedSessionService = terminatedSessionService;
    this.configurationService = configurationService;
    this.sessionListeners = sessionListeners;

    initJwtVerifier();
  }

  public String createAuthToken(String userId) {
    final String token = generateAuthToken(UUID.randomUUID().toString(), userId);

    notifyOnSessionCreate(userId);

    return token;
  }

  public boolean hasValidSession(final HttpServletRequest servletRequest) {
    final String token = AuthCookieService.getToken(servletRequest).orElse(null);
    return isValidToken(token);
  }

  public boolean isValidToken(final String token) {
    return Optional.ofNullable(token)
      .map(this::isValidAuthToken)
      .orElse(false);
  }

  public boolean isTokenPresent(final HttpServletRequest servletRequest) {
    return AuthCookieService.getToken(servletRequest).isPresent();
  }

  private boolean isValidAuthToken(String token) {
    boolean isValid = false;

    try {
      final DecodedJWT decodedJWT = jwtVerifier.verify(token);
      isValid = isStillValid(decodedJWT);
    } catch (JWTVerificationException exception) {
      log.error("Error while validating authentication token [{}]. Invalid signature or claims!", token, exception);
    }

    return isValid;
  }

  public Optional<String> refreshAuthToken(String currentToken) {
    String newToken = null;
    try {
      final DecodedJWT decodedJWT = jwtVerifier.verify(currentToken);

      if (isStillValid(decodedJWT)) {
        newToken = generateAuthToken(decodedJWT.getId(), decodedJWT.getSubject());
        notifyOnSessionRefresh(decodedJWT.getSubject());
      }

    } catch (JWTVerificationException exception) {
      log.error(
        "Error while validating authentication token [{}]. Invalid signature or claims!",
        currentToken,
        exception
      );
    }

    return Optional.ofNullable(newToken);
  }

  public void invalidateSession(ContainerRequestContext requestContext) {
    AuthCookieService.getToken(requestContext).ifPresent(this::invalidateAuthToken);
  }

  private void invalidateAuthToken(final String token) {
    try {
      final DecodedJWT decodedJwt = JWT.decode(token);
      terminatedSessionService.terminateUserSession(decodedJwt.getId());
      sessionListeners.forEach(sessionListener -> sessionListener.onSessionDestroy(decodedJwt.getSubject()));
    } catch (Exception e) {
      final String message = "Could not decode security token for invalidation!";
      log.debug(message, e);
      throw new IllegalArgumentException(message, e);
    }
  }

  public Optional<LocalDateTime> getExpiresAtLocalDateTime(final String token) {
    DecodedJWT decodedJWT = null;
    try {
      decodedJWT = JWT.decode(token);
    } catch (JWTDecodeException e) {
      log.debug("Invalid jwt token {}", token, e);
    }
    return Optional.ofNullable(decodedJWT).flatMap(this::getDynamicExpiresAtLocalDateTime);
  }

  public String getRequestUserOrFailNotAuthorized(ContainerRequestContext requestContext) {
    return AuthCookieService.getToken(requestContext)
      .flatMap(AuthCookieService::getTokenSubject)
      .orElseThrow(() -> new NotAuthorizedException("Could not extract request user!"));
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    initJwtVerifier();
  }

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  private void notifyOnSessionCreate(final String userId) {
    sessionListeners.forEach(sessionListener -> sessionListener.onSessionCreate(userId));
  }

  private void notifyOnSessionRefresh(final String userId) {
    sessionListeners.forEach(sessionListener -> sessionListener.onSessionRefresh(userId));
  }

  private boolean isStillValid(final DecodedJWT decodedJWT) {
    boolean isValid = true;

    final Optional<LocalDateTime> dynamicExpiresAtDate = getDynamicExpiresAtLocalDateTime(decodedJWT);
    if (dynamicExpiresAtDate.map(date -> LocalDateUtil.getCurrentLocalDateTime().isAfter(date)).orElse(false)) {
      log.debug("Authentication token [{}] has expired at {}!", decodedJWT.getToken(), dynamicExpiresAtDate);
      isValid = false;
    }

    try {
      if (terminatedSessionService.isSessionTerminated(decodedJWT.getId())) {
        log.warn(
          "Authentication token [{}] of already terminated session {} was used!",
          decodedJWT.getToken(),
          decodedJWT.getId()
        );
        isValid = false;
      }
    } catch (OptimizeRuntimeException e) {
      log.warn(
        "Failed checking if session {} is a terminated session, defaulting to handle it as not terminated",
        decodedJWT.getId(),
        e
      );
    }

    return isValid;
  }

  private Optional<LocalDateTime> getDynamicExpiresAtLocalDateTime(final DecodedJWT decodedJWT) {
    // we calculate expiry based on current configuration and the issuedAt date of the token
    // this allows to apply life time config changes onto existing tokens
    return Optional.ofNullable(decodedJWT.getIssuedAt())
      .map(Date::toInstant)
      .map(instant -> instant.atZone(ZoneId.systemDefault()).toLocalDateTime())
      .map(localDateTime -> localDateTime.plus(getAuthConfiguration().getTokenLifeTimeMinutes(), ChronoUnit.MINUTES));
  }

  private String generateAuthToken(final String sessionId, final String userId) {
    Instant issuedAt = LocalDateUtil.getCurrentLocalDateTime().atZone(ZoneId.systemDefault()).toInstant();
    return JWT.create()
      .withJWTId(sessionId)
      .withIssuer(ISSUER)
      .withSubject(userId)
      .withIssuedAt(Date.from(issuedAt))
      .sign(hashingAlgorithm);
  }

  private void initJwtVerifier() {
    final byte[] secretBytes = getAuthConfiguration().getTokenSecret()
      .map(secretString -> secretString.getBytes(StandardCharsets.UTF_8))
      .orElse(DEFAULT_SECRET_BYTES);

    this.hashingAlgorithm = Algorithm.HMAC256(secretBytes);
    // ignore issued at validation, we validate that in #isStillValid
    this.jwtVerifier = JWT.require(hashingAlgorithm).withIssuer(ISSUER).ignoreIssuedAt().build();
  }

  private AuthConfiguration getAuthConfiguration() {
    return configurationService.getAuthConfiguration();
  }

}
