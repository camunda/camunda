/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.oauth;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

  private final String expectedAudience;

  public AudienceValidator(final String expectedAudience) {
    this.expectedAudience = expectedAudience;
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt jwt) {
    if (audienceIsValid()) {
      if (jwt.getAudience().contains(expectedAudience)) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "The required audience is missing", null));
    } else {
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("bad_configuration", "The configured audience is invalid", null));
    }
  }

  private boolean audienceIsValid() {
    return !(expectedAudience == null || expectedAudience.isEmpty());
  }
}
