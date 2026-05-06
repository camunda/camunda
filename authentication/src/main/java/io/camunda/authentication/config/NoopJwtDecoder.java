/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * {@link JwtDecoder} that parses the JWT structure without verifying the signature or validating
 * any claims. Intended only for performance debugging to isolate the cost of JWT validation from
 * other filter chain overhead. Never use in production.
 */
public class NoopJwtDecoder implements JwtDecoder {

  @Override
  public Jwt decode(final String token) throws JwtException {
    try {
      final var parsed = JWTParser.parse(token);
      final var headers = new LinkedHashMap<>(parsed.getHeader().toJSONObject());
      final var rawClaims = new LinkedHashMap<>(parsed.getJWTClaimsSet().getClaims());
      convertDateClaims(rawClaims);
      return Jwt.withTokenValue(token).headers(h -> h.putAll(headers)).claims(c -> c.putAll(rawClaims)).build();
    } catch (final ParseException e) {
      throw new JwtException("Failed to parse token", e);
    }
  }

  // Spring's Jwt stores exp/iat/nbf as Instant; Nimbus returns them as Date
  private static void convertDateClaims(final Map<String, Object> claims) {
    for (final var entry : claims.entrySet()) {
      if (entry.getValue() instanceof final Date date) {
        claims.put(entry.getKey(), date.toInstant());
      }
    }
  }
}
