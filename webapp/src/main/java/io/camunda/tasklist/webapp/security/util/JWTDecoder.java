/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.util;

import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.impl.JWTParser;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Header;
import com.auth0.jwt.interfaces.Payload;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * This class is based on com.auth0.jwt.JWTDecoder As we don't have access to it (default package
 * accessible)
 */
public class JWTDecoder implements DecodedJWT, Serializable {
  private static final long serialVersionUID = 1873362438023312895L;
  private final String[] parts;
  private final Header header;
  private final Payload payload;

  public JWTDecoder(String jwt) throws JWTDecodeException {
    this(new JWTParser(), jwt);
  }

  public JWTDecoder(JWTParser converter, String jwt) throws JWTDecodeException {
    this.parts = TokenUtils.splitToken(jwt);

    final String headerJson;
    final String payloadJson;
    try {
      headerJson = new String(Base64.getUrlDecoder().decode(this.parts[0]), StandardCharsets.UTF_8);
      payloadJson =
          new String(Base64.getUrlDecoder().decode(this.parts[1]), StandardCharsets.UTF_8);
    } catch (NullPointerException var6) {
      throw new JWTDecodeException("The UTF-8 Charset isn't initialized.", var6);
    } catch (IllegalArgumentException var7) {
      throw new JWTDecodeException("The input is not a valid base 64 encoded string.", var7);
    }

    this.header = converter.parseHeader(headerJson);
    this.payload = converter.parsePayload(payloadJson);
  }

  public Payload getPayloadObject() {
    return payload;
  }

  public String getAlgorithm() {
    return this.header.getAlgorithm();
  }

  public String getType() {
    return this.header.getType();
  }

  public String getContentType() {
    return this.header.getContentType();
  }

  public String getKeyId() {
    return this.header.getKeyId();
  }

  public Claim getHeaderClaim(String name) {
    return this.header.getHeaderClaim(name);
  }

  public String getIssuer() {
    return this.payload.getIssuer();
  }

  public String getSubject() {
    return this.payload.getSubject();
  }

  public List<String> getAudience() {
    return this.payload.getAudience();
  }

  public Date getExpiresAt() {
    return this.payload.getExpiresAt();
  }

  public Date getNotBefore() {
    return this.payload.getNotBefore();
  }

  public Date getIssuedAt() {
    return this.payload.getIssuedAt();
  }

  public String getId() {
    return this.payload.getId();
  }

  public Claim getClaim(String name) {
    return this.payload.getClaim(name);
  }

  public Map<String, Claim> getClaims() {
    return this.payload.getClaims();
  }

  public String getHeader() {
    return this.parts[0];
  }

  public String getPayload() {
    return this.parts[1];
  }

  public String getSignature() {
    return this.parts[2];
  }

  public String getToken() {
    return String.format("%s.%s.%s", this.parts[0], this.parts[1], this.parts[2]);
  }

  abstract static class TokenUtils {
    TokenUtils() {}

    static String[] splitToken(String token) throws JWTDecodeException {
      String[] parts = token.split("\\.");
      if (parts.length == 2 && token.endsWith(".")) {
        parts = new String[] {parts[0], parts[1], ""};
      }

      if (parts.length != 3) {
        throw new JWTDecodeException(
            String.format("The token was expected to have 3 parts, but got %s.", parts.length));
      } else {
        return parts;
      }
    }
  }
}
