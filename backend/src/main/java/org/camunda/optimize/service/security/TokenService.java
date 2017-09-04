package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.camunda.optimize.service.exceptions.InvalidTokenException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Askar Akhmerov
 */
@Component
public class TokenService {
  private final Logger logger = LoggerFactory.getLogger(TokenService.class);
  private static ConcurrentHashMap<String, TokenVerifier> tokenVerifiers = new ConcurrentHashMap<>();
  protected Random secureRandom = new SecureRandom();
  private final static int SECRET_LENGTH = 16;

  @Autowired
  private ConfigurationService configurationService;

  public void validateToken(String token) throws InvalidTokenException {
    JWT decoded = JWT.decode(token);
    String username = decoded.getIssuer();
    TokenVerifier tokenVerifier = tokenVerifiers.get(username);
    if (tokenVerifier == null ) {
      throw new InvalidTokenException("Error while validating authentication token [" + token + "]. " +
        "User [" + username + "] is not logged in!");
    }
    tokenVerifier.isTokenValid(token);
    tokenVerifier.isExpired(token);
    LocalDateTime expiry = calculateExpiryDate();
    tokenVerifier.updateExpiryDate(expiry);
  }

  private LocalDateTime calculateExpiryDate() {
    return LocalDateUtil.getCurrentDateTime().plus(configurationService.getLifetime(), ChronoUnit.MINUTES);
  }

  public String issueToken(String username) {
    String token = null;
    try {
      LocalDateTime expiryDate = calculateExpiryDate();

      Algorithm hashingAlgorithm = generateAlgorithm();
      token = JWT.create()
          .withIssuer(username)
          .sign(hashingAlgorithm);

      JWTVerifier verifier = JWT.require(hashingAlgorithm)
        .withIssuer(username)
        .build(); //Reusable verifier instance
      TokenVerifier tokenVerifier = new TokenVerifier(expiryDate, verifier);
      tokenVerifiers.put(username, tokenVerifier);
    } catch (JWTCreationException exception) {
      //Invalid Signing configuration / Couldn't convert Claims.
    }
    return token;
  }

  private Algorithm generateAlgorithm() {
    byte[] secretBytes = new byte[SECRET_LENGTH];
    secureRandom.nextBytes(secretBytes);
    return Algorithm.HMAC256(secretBytes);
  }

  public void expireToken(String token) {
    JWT decoded = JWT.decode(token);
    String username = decoded.getIssuer();
    tokenVerifiers.remove(username);
  }

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  public void setConfigurationService(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  private class TokenVerifier {

    private LocalDateTime expiryDate;
    private JWTVerifier verifier;

    public TokenVerifier(LocalDateTime expiryDate, JWTVerifier verifier) {
      this.expiryDate = expiryDate;
      this.verifier = verifier;
    }

    public void updateExpiryDate(LocalDateTime newExpiryDate) {
      this.expiryDate = newExpiryDate;
    }

    public void isTokenValid(String tokenKey) throws InvalidTokenException {
      try {
        verifier.verify(tokenKey);
      } catch (JWTVerificationException exception) {
        //Invalid signature/claims
        throw new InvalidTokenException("Error while validating authentication token [" + tokenKey + "]. " +
          "Invalid signature or claims! Presumably, the user is already logged in somewhere else."
        );
      }
      isExpired(tokenKey);
    }

    private void isExpired(String tokenKey) throws InvalidTokenException {
      if (expiryDate == null || LocalDateUtil.getCurrentDateTime().isAfter(expiryDate)) {
        throw new InvalidTokenException("Error while validating authentication token [" + tokenKey + "]" +
          "Date has expired!");
      }
    }
  }
}
