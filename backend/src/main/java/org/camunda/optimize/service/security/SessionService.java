package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static org.camunda.optimize.rest.util.AuthenticationUtil.getSessionIssuer;

@Component
public class SessionService {
  private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

  private static ConcurrentHashMap<String, Session> userSessions = new ConcurrentHashMap<>();
  private Random secureRandom = new SecureRandom();
  private final static int SECRET_LENGTH = 16;

  private final ConfigurationService configurationService;
  private final List<SessionListener> sessionListeners;

  @Autowired
  public SessionService(final ConfigurationService configurationService,
                        final List<SessionListener> sessionListeners) {
    this.configurationService = configurationService;
    this.sessionListeners = sessionListeners;
  }

  public boolean isValidToken(String token) {
    Optional<String> username = getSessionIssuer(token);
    if (username.isPresent()) {
      Session session = userSessions.get(username.get());
      if (session != null) {
        return session.isTokenValid(token);
      }
    }
    logger.debug("Error while validating authentication token [{}]. " +
                   "User [{}] is not logged in!", token, username);
    return false;
  }

  public boolean hasTokenExpired(String token) {
    Optional<String> username = getSessionIssuer(token);
    if (username.isPresent()) {
      Session session = userSessions.get(username.get());
      if (session != null) {
        return session.hasTokenExpired(token);
      }
    }
    return false;
  }

  private Algorithm generateAlgorithm() {
    byte[] secretBytes = new byte[SECRET_LENGTH];
    secureRandom.nextBytes(secretBytes);
    return Algorithm.HMAC256(secretBytes);
  }

  public void expireToken(String token) {
    Optional<String> username = getSessionIssuer(token);
    username.ifPresent(user -> {
      userSessions.remove(user);
      sessionListeners.forEach(sessionListener -> sessionListener.onSessionDestroy(user));
    });
  }

  public void updateExpiryDate(String token) {
    Optional<String> username = getSessionIssuer(token);
    username.ifPresent(
      user -> userSessions.computeIfPresent(user, (u, session) -> {
        session.updateExpiryDate();
        return session;
      })
    );
  }

  public String createSessionAndReturnSecurityToken(String userId) {

    Algorithm hashingAlgorithm = generateAlgorithm();
    String token = JWT.create()
      .withIssuer(userId)
      .sign(hashingAlgorithm);

    JWTVerifier verifier = JWT.require(hashingAlgorithm)
      .withIssuer(userId)
      .build(); //Reusable verifier instance

    TokenVerifier tokenVerifier = new TokenVerifier(configurationService.getTokenLifeTime(), verifier);
    Session session = new Session(tokenVerifier);
    userSessions.put(userId, session);

    sessionListeners.forEach(sessionListener -> sessionListener.onSessionCreate(userId));

    return token;
  }

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

}
