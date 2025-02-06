/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.metadata.Version;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class SessionService implements ConfigurationReloadable {

  private static final String ISSUER = "Optimize-" + Version.RAW_VERSION;
  private static final byte[] DEFAULT_SECRET_BYTES = new byte[64];
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(SessionService.class);

  static {
    new SecureRandom().nextBytes(DEFAULT_SECRET_BYTES);
  }

  private final TerminatedSessionService terminatedSessionService;
  private final ConfigurationService configurationService;
  private Algorithm hashingAlgorithm;
  private JWTVerifier jwtVerifier;

  public SessionService(
      final TerminatedSessionService terminatedSessionService,
      final ConfigurationService configurationService) {
    this.terminatedSessionService = terminatedSessionService;
    this.configurationService = configurationService;

    initJwtVerifier();
  }

  public String createAuthToken(final String userId) {
    return generateAuthToken(UUID.randomUUID().toString(), userId);
  }

  public boolean isValidToken(final String token) {
    return Optional.ofNullable(token).map(this::isValidAuthToken).orElse(false);
  }

  private boolean isValidAuthToken(final String token) {
    boolean isValid = false;

    try {
      final DecodedJWT decodedJWT = jwtVerifier.verify(token);
      isValid = isStillValid(decodedJWT);
    } catch (final JWTVerificationException exception) {
      LOG.error(
          "Error while validating authentication token. Invalid signature or claims!", exception);
    }

    return isValid;
  }

  public String getRequestUserOrFailNotAuthorized(final HttpServletRequest request) {
    return AuthCookieService.getAuthCookieToken(request)
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

  private boolean isStillValid(final DecodedJWT decodedJWT) {
    boolean isValid = true;

    final Optional<LocalDateTime> dynamicExpiresAtDate =
        getDynamicExpiresAtLocalDateTime(decodedJWT);
    if (dynamicExpiresAtDate
        .map(date -> LocalDateUtil.getCurrentLocalDateTime().isAfter(date))
        .orElse(false)) {
      LOG.debug(
          "Authentication token [{}] has expired at {}!",
          decodedJWT.getToken(),
          dynamicExpiresAtDate);
      isValid = false;
    }

    try {
      if (terminatedSessionService.isSessionTerminated(decodedJWT.getId())) {
        LOG.warn(
            "Authentication token [{}] of already terminated session {} was used!",
            decodedJWT.getToken(),
            decodedJWT.getId());
        isValid = false;
      }
    } catch (final OptimizeRuntimeException e) {
      LOG.warn(
          "Failed checking if session {} is a terminated session, defaulting to handle it as not terminated",
          decodedJWT.getId(),
          e);
    }

    return isValid;
  }

  private Optional<LocalDateTime> getDynamicExpiresAtLocalDateTime(final DecodedJWT decodedJWT) {
    // we calculate expiry based on current configuration and the issuedAt date of the token
    // this allows to apply lifetime config changes onto existing tokens
    return Optional.ofNullable(decodedJWT.getIssuedAt())
        .map(Date::toInstant)
        .map(instant -> instant.atZone(ZoneId.systemDefault()).toLocalDateTime())
        .map(
            localDateTime ->
                localDateTime.plus(
                    getAuthConfiguration().getTokenLifeTimeMinutes(), ChronoUnit.MINUTES));
  }

  private String generateAuthToken(final String sessionId, final String userId) {
    final Instant issuedAt =
        LocalDateUtil.getCurrentLocalDateTime().atZone(ZoneId.systemDefault()).toInstant();
    return JWT.create()
        .withJWTId(sessionId)
        .withIssuer(ISSUER)
        .withSubject(userId)
        .withIssuedAt(Date.from(issuedAt))
        .sign(hashingAlgorithm);
  }

  private void initJwtVerifier() {
    final byte[] secretBytes =
        getAuthConfiguration()
            .getTokenSecret()
            .map(secretString -> secretString.getBytes(StandardCharsets.UTF_8))
            .orElse(DEFAULT_SECRET_BYTES);

    hashingAlgorithm = Algorithm.HMAC256(secretBytes);
    // ignore issued at validation, we validate that in #isStillValid
    jwtVerifier = JWT.require(hashingAlgorithm).withIssuer(ISSUER).ignoreIssuedAt().build();
  }

  private AuthConfiguration getAuthConfiguration() {
    return configurationService.getAuthConfiguration();
  }
}
