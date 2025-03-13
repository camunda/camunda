/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.configuration.TokenClaim;
import java.util.Collection;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Validates the claims of a JWT token against the configured claims.
 *
 * <p>The claims are treated as an any match, meaning that at least one of the configured claims
 * must be present in the token with the expected value.
 */
public class ClaimValidator implements OAuth2TokenValidator<Jwt> {
  private static final Logger LOG = LoggerFactory.getLogger(ClaimValidator.class);
  private final Set<TokenClaim> expectedClaims;

  /** Creates a new validator with the given expected claims. */
  public ClaimValidator(final Set<TokenClaim> expectedClaims) {
    this.expectedClaims = Set.copyOf(expectedClaims);
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt token) {
    final var tokenClaims = token.getClaims();

    for (final var expectedClaim : expectedClaims) {
      final var tokenClaim = tokenClaims.get(expectedClaim.getClaim());
      if (tokenClaim == null) {
        continue;
      }

      if (tokenClaim instanceof final Collection<?> claim) {
        if (claim.contains(expectedClaim.getValue())) {
          return OAuth2TokenValidatorResult.success();
        }
      } else {
        if (expectedClaim.getValue().equals(tokenClaim)) {
          return OAuth2TokenValidatorResult.success();
        }
      }
    }

    LOG.debug("Invalid token: expected claims not found in token");
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Invalid token: expected claims not found in token",
            null));
  }
}
