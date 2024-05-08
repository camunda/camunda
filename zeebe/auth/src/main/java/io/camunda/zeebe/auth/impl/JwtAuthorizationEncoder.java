/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.auth.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import io.camunda.zeebe.auth.api.AuthorizationEncoder;
import io.camunda.zeebe.auth.api.JwtAuthorizationBuilder;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtAuthorizationEncoder
    implements JwtAuthorizationBuilder<JwtAuthorizationEncoder, Algorithm, String>,
        AuthorizationEncoder {

  private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthorizationEncoder.class);
  private String issuer = DEFAULT_ISSUER;
  private String audience = DEFAULT_AUDIENCE;
  private String subject = DEFAULT_SUBJECT;
  private Algorithm signingAlgorithm = Algorithm.none();
  private final Map<String, Object> claims = new HashMap<>();

  public JwtAuthorizationEncoder() {}

  @Override
  public JwtAuthorizationEncoder withSubject(final String subject) {
    this.subject = subject;
    return this;
  }

  @Override
  public JwtAuthorizationEncoder withIssuer(final String issuer) {
    this.issuer = issuer;
    return this;
  }

  @Override
  public JwtAuthorizationEncoder withAudience(final String audience) {
    this.audience = audience;
    return this;
  }

  @Override
  public JwtAuthorizationEncoder withSigningAlgorithm(final Algorithm signingAlgorithm) {
    this.signingAlgorithm = signingAlgorithm;
    return this;
  }

  @Override
  public String build() {
    try {
      return JWT.create()
          .withIssuer(issuer)
          .withAudience(audience)
          .withSubject(subject)
          .withPayload(claims)
          .sign(signingAlgorithm);
    } catch (final IllegalArgumentException | JWTCreationException ex) {
      LOGGER.error("Authorization data couldn't be encoded: {}", ex.getMessage());
      throw new UnrecoverableException(
          "Authorization data couldn't be encoded: " + ex.getMessage(), ex);
    }
  }

  public JwtAuthorizationEncoder withClaim(final String claimName, final Object claimValue) {
    claims.put(claimName, claimValue);
    return this;
  }

  @Override
  public String encode() {
    return build();
  }
}
