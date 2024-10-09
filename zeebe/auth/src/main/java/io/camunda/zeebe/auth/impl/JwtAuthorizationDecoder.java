/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.auth.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Verification;
import io.camunda.zeebe.auth.api.AuthorizationDecoder;
import io.camunda.zeebe.auth.api.JwtAuthorizationBuilder;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtAuthorizationDecoder
    implements JwtAuthorizationBuilder<JwtAuthorizationDecoder, Algorithm, DecodedJWT>,
        AuthorizationDecoder<Map<String, Object>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthorizationDecoder.class);

  private String issuer = DEFAULT_ISSUER;
  private String audience = DEFAULT_AUDIENCE;
  private String subject = DEFAULT_SUBJECT;
  private Algorithm signingAlgorithm = Algorithm.none();
  private final Set<String> claims = new HashSet<>();
  private String jwtToken;

  public JwtAuthorizationDecoder(final String jwtToken) {
    this.jwtToken = jwtToken;
  }

  @Override
  public JwtAuthorizationDecoder withSubject(final String subject) {
    this.subject = subject;
    return this;
  }

  @Override
  public JwtAuthorizationDecoder withIssuer(final String issuer) {
    this.issuer = issuer;
    return this;
  }

  @Override
  public JwtAuthorizationDecoder withAudience(final String audience) {
    this.audience = audience;
    return this;
  }

  @Override
  public JwtAuthorizationDecoder withSigningAlgorithm(final Algorithm signingAlgorithm) {
    this.signingAlgorithm = signingAlgorithm;
    return this;
  }

  /**
   * Validates the signature and decodes a provided JWT token. It is also possible to pass the names
   * of the claims that are expected to be contained in the token.
   *
   * @return a decoded JWT token
   */
  @Override
  public DecodedJWT build() {
    return validateJwtToken();
  }

  @Override
  public Map<String, Object> decode() {
    final DecodedJWT decodedJWT = withClaim(Authorization.AUTHORIZED_TENANTS).build();
    final var claimMap = new HashMap<String, Object>();
    claimMap.put(
        Authorization.AUTHORIZED_TENANTS,
        decodedJWT.getClaim(Authorization.AUTHORIZED_TENANTS).asList(String.class));

    if (decodedJWT.getClaims().containsKey(Authorization.AUTHORIZED_USER_KEY)) {
      claimMap.put(
          Authorization.AUTHORIZED_USER_KEY,
          decodedJWT.getClaim(Authorization.AUTHORIZED_USER_KEY).asLong());
    }

    claimMap.putAll(
        decodedJWT.getClaims().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(USER_TOKEN_CLAIM_PREFIX))
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, claimEntry -> claimEntry.getValue().as(Object.class))));

    return claimMap;
  }

  /**
   * Sets the name of a JWT token claim expected to be part of the token. This method can be called
   * multiple times. The token will be validated agains all the added claims.
   *
   * @param claimName - the name of the claim
   * @return the builder instance
   */
  public JwtAuthorizationDecoder withClaim(final String claimName) {
    claims.add(claimName);
    return this;
  }

  /**
   * Sets the JWT token String that should be validated and decoded.
   *
   * @param token - the JWT token String
   * @return the builder instance
   */
  public JwtAuthorizationDecoder withJwtToken(final String token) {
    jwtToken = token;
    return this;
  }

  private DecodedJWT validateJwtToken() {
    final Verification verificationBuilder =
        JWT.require(signingAlgorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(subject)
            .ignoreIssuedAt();

    for (final String claim : claims) {
      verificationBuilder.withClaimPresence(claim);
    }
    final JWTVerifier jwtVerification = verificationBuilder.build();

    try {
      return jwtVerification.verify(jwtToken);
    } catch (final JWTVerificationException | NullPointerException ex) {
      LOGGER.error("Authorization data unavailable: {}", ex.getMessage());
      throw new UnrecoverableException("Authorization data unavailable: " + ex.getMessage(), ex);
    }
  }
}
