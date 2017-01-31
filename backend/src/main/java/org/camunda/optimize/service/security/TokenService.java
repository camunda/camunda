package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import org.camunda.optimize.service.exceptions.InvalidTokenException;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Askar Akhmerov
 */
@Component
public class TokenService {
  private final Logger logger = LoggerFactory.getLogger(TokenService.class);
  private static ConcurrentHashMap<String, LocalDateTime> tokenExpiry = new ConcurrentHashMap<>();

  @Autowired
  private ConfigurationService configurationService;

  public void validateToken(String token) throws InvalidTokenException {
    JWT decoded = JWT.decode(token);
    String username = decoded.getSubject();
    LocalDateTime expiry = tokenExpiry.get(username);
    if (expiry == null || LocalDateTime.now().isAfter(expiry)) {
      throw new InvalidTokenException();
    } else {
      expiry = expiry.plus(configurationService.getLifetime(),ChronoUnit.MINUTES);
      tokenExpiry.put(username, expiry);
    }
  }

  public String issueToken(String username) {
    String token = null;
    try {
      LocalDateTime expiryDate = LocalDateTime.now()
          .plus(configurationService.getLifetime(), ChronoUnit.MINUTES);
      token = JWT.create()
          .withSubject(username)
          .sign(Algorithm.HMAC256(configurationService.getSecret()));

      tokenExpiry.put(username, expiryDate);
    } catch (JWTCreationException exception) {
      //Invalid Signing configuration / Couldn't convert Claims.
    } catch (UnsupportedEncodingException e) {
      logger.error("unsupported encoding for authentication token generation", e);
    }
    return token;
  }

  public void expireToken(String token) {
    JWT decoded = JWT.decode(token);
    String username = decoded.getSubject();
    tokenExpiry.remove(username);
  }
}
