package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import org.camunda.optimize.service.exceptions.InvalidTokenException;

import javax.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.sql.Date;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Askar Akhmerov
 */
public class TokenService {
  private static ConcurrentHashMap<String, LocalDateTime> tokenExpiry = new ConcurrentHashMap<>();

  @Inject
  private Properties applicationProperties;

  public void validateToken(String token) throws InvalidTokenException {
    JWT decoded = JWT.decode(token);
    String username = decoded.getSubject();
    LocalDateTime expiry = tokenExpiry.get(username);
    if (expiry == null || LocalDateTime.now().isAfter(expiry)) {
      throw new InvalidTokenException();
    } else {
      expiry = expiry.plus(getLifetime(),ChronoUnit.MINUTES);
      tokenExpiry.put(username, expiry);
    }
  }

  private int getLifetime() {
    return Integer.parseInt(applicationProperties.getProperty("camunda.optimize.auth.token.live.min"));
  }

  public String issueToken(String username) {
    String token = null;
    try {
      LocalDateTime expiryDate = LocalDateTime.now().plus(getLifetime(), ChronoUnit.MINUTES);
      token = JWT.create()
          .withSubject(username)
          .sign(Algorithm.HMAC256(applicationProperties.getProperty("camunda.optimize.auth.token.secret")));

      tokenExpiry.put(username, expiryDate);
    } catch (JWTCreationException exception) {
      //Invalid Signing configuration / Couldn't convert Claims.
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return token;
  }
}
