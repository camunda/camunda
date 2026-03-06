/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error INVALID_AUDIENCE =
      new OAuth2Error("invalid_token", "The token audience is invalid", null);

  private final Set<String> validAudiences;

  public AudienceValidator(final Set<String> validAudiences) {
    this.validAudiences = Set.copyOf(validAudiences);
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt jwt) {
    if (jwt.getAudience() == null || jwt.getAudience().isEmpty()) {
      return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
    final boolean hasValidAudience =
        jwt.getAudience().stream().anyMatch(validAudiences::contains);
    return hasValidAudience
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
  }
}
