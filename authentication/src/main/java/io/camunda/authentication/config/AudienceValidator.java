/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Validates the audience of a JWT token. A token can have multiple audiences, but at least one of
 * the valid audiences must be present.
 */
final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

  private static final Logger LOG = LoggerFactory.getLogger(AudienceValidator.class);
  private final Set<String> validAudiences;

  /**
   * Creates a new validator with the given valid audiences.
   *
   * @param validAudiences the valid audiences. Must not be empty.
   */
  AudienceValidator(final Set<String> validAudiences) {
    if (validAudiences.isEmpty()) {
      throw new IllegalArgumentException("At least one valid audience must be provided");
    }
    this.validAudiences = Set.copyOf(validAudiences);
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt token) {
    final var tokenAudiences = token.getAudience();

    // Iterate over token audiences first, usually there is only one
    for (final var tokenAudience : tokenAudiences) {
      if (validAudiences.contains(tokenAudience)) {
        return OAuth2TokenValidatorResult.success();
      }
    }

    LOG.debug(
        "Rejected token with audiences '{}', expected at least one of '{}'",
        tokenAudiences,
        validAudiences);
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Token audiences are %s, expected at least one of %s"
                .formatted(tokenAudiences, validAudiences),
            null));
  }
}
