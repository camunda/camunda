package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.rest.util.AuthenticationUtil.getTokenSubject;

@Component
public class SessionService implements ConfigurationReloadable {
  private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

  private static final String ISSUER = "Optimize-" + Version.RAW_VERSION;
  private static final byte[] DEFAULT_SECRET_BYTES = new byte[64];

  private final ConfigurationService configurationService;
  private final List<SessionListener> sessionListeners;

  private Algorithm hashingAlgorithm;
  private JWTVerifier jwtVerifier;

  static {
    new SecureRandom().nextBytes(DEFAULT_SECRET_BYTES);
  }

  @Autowired
  public SessionService(final ConfigurationService configurationService,
                        final List<SessionListener> sessionListeners) {
    this.configurationService = configurationService;
    this.sessionListeners = sessionListeners;

    initJwtVerifier();
  }

  public String createAuthToken(String userId) {
    final String token = generateAuthToken(userId);

    notifyOnSessionCreateOrRefresh(userId);

    return token;
  }

  public boolean isValidAuthToken(String token) {
    boolean isValid = false;

    try {
      final DecodedJWT decodedJWT = jwtVerifier.verify(token);
      isValid = isStillValid(decodedJWT);
    } catch (JWTVerificationException exception) {
      logger.error("Error while validating authentication token [{}]. Invalid signature or claims!", token, exception);
    }

    return isValid;
  }

  public Optional<String> refreshAuthToken(String currentToken) {
    String newToken = null;
    try {
      final DecodedJWT decodedJWT = jwtVerifier.verify(currentToken);

      if (isStillValid(decodedJWT)) {
        newToken = generateAuthToken(decodedJWT.getSubject());
        notifyOnSessionCreateOrRefresh(decodedJWT.getSubject());
      }

    } catch (JWTVerificationException exception) {
      logger.error(
        "Error while validating authentication token [{}]. Invalid signature or claims!",
        currentToken,
        exception
      );
    }

    return Optional.ofNullable(newToken);
  }

  public void invalidateAuthToken(final String token) {
    Optional<String> username = getTokenSubject(token);
    username.ifPresent(user -> sessionListeners.forEach(sessionListener -> sessionListener.onSessionDestroy(user)));
  }

  public Optional<LocalDateTime> getExpiresAtLocalDateTime(final String token) {
    DecodedJWT decodedJWT = null;
    try {
      decodedJWT = JWT.decode(token);
    } catch (JWTDecodeException e) {
      logger.debug("Invalid jwt token {}", token, e);
    }
    return Optional.ofNullable(decodedJWT).flatMap(this::getDynamicExpiresAtLocalDateTime);
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    initJwtVerifier();
  }

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  private void notifyOnSessionCreateOrRefresh(final String userId) {
    sessionListeners.forEach(sessionListener -> sessionListener.onSessionCreateOrRefresh(userId));
  }

  private boolean isStillValid(final DecodedJWT decodedJWT) {
    boolean isValid = false;

    final Optional<LocalDateTime> dynamicExpiresAtDate = getDynamicExpiresAtLocalDateTime(decodedJWT);
    if (dynamicExpiresAtDate.isPresent() && LocalDateUtil.getCurrentLocalDateTime()
      .isBefore(dynamicExpiresAtDate.get())) {
      isValid = true;
    } else {
      logger.debug("Authentication token [{}] has expired at {}!", decodedJWT.getToken(), dynamicExpiresAtDate);
    }

    return isValid;
  }

  private Optional<LocalDateTime> getDynamicExpiresAtLocalDateTime(final DecodedJWT decodedJWT) {
    // we calculate expiry based on current configuration and the issuedAt date of the token
    // this allows to apply life time config changes onto existing tokens
    return Optional.ofNullable(decodedJWT.getIssuedAt())
      .map(Date::toInstant)
      .map(instant -> instant.atZone(ZoneId.systemDefault()).toLocalDateTime())
      .map(localDateTime -> localDateTime.plus(configurationService.getTokenLifeTimeMinutes(), ChronoUnit.MINUTES));
  }

  private String generateAuthToken(final String userId) {
    final Instant issuedAt = LocalDateUtil.getCurrentDateTime().toInstant();
    return JWT.create()
      .withIssuer(ISSUER)
      .withSubject(userId)
      .withIssuedAt(Date.from(issuedAt))
      .sign(hashingAlgorithm);
  }

  private void initJwtVerifier() {
    final byte[] secretBytes = configurationService.getTokenSecret()
      .map(secretString -> secretString.getBytes(StandardCharsets.UTF_8))
      .orElse(DEFAULT_SECRET_BYTES);

    this.hashingAlgorithm = Algorithm.HMAC256(secretBytes);
    // ignore issued at validation, we validate that in #isStillValid
    this.jwtVerifier = JWT.require(hashingAlgorithm).withIssuer(ISSUER).ignoreIssuedAt().build();
  }

}
