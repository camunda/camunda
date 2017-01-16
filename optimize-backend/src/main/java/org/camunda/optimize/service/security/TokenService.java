package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import org.camunda.optimize.service.exceptions.InvalidTokenException;

import java.io.UnsupportedEncodingException;
import java.sql.Date;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Askar Akhmerov
 */
public class TokenService {
  private static final String SECRET = "obfuscate";
  public static final int LIFETIME = 15;
  private static ConcurrentHashMap<String, LocalDateTime> tokenExpiry = new ConcurrentHashMap<>();

  public static void validateToken(String token) throws InvalidTokenException {
    JWT decoded = JWT.decode(token);
    String username = decoded.getSubject();
    LocalDateTime expiry = tokenExpiry.get(username);
    if (expiry == null || LocalDateTime.now().isAfter(expiry)) {
      throw new InvalidTokenException();
    } else {
      expiry = expiry.plus(LIFETIME,ChronoUnit.MINUTES);
      tokenExpiry.put(username, expiry);
    }
  }

  public static String issueToken(String username) {
    String token = null;
    try {
      LocalDateTime expiryDate = LocalDateTime.now().plus(LIFETIME, ChronoUnit.MINUTES);
      token = JWT.create()
          .withSubject(username)
          .sign(Algorithm.HMAC256(SECRET));

      tokenExpiry.put(username, expiryDate);
    } catch (JWTCreationException exception) {
      //Invalid Signing configuration / Couldn't convert Claims.
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return token;
  }
}
