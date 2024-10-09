/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.oauth;

import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class ScopeValidator implements OAuth2TokenValidator<Jwt> {

  private final String expectedScope;
  private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

  public ScopeValidator(final String expectedScope) {
    this.expectedScope = expectedScope;
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt jwt) {
    final JwtAuthenticationToken auth = (JwtAuthenticationToken) delegate.convert(jwt);
    final Map<String, Object> payload = auth.getTokenAttributes();
    return isScopeValid(payload);
  }

  private OAuth2TokenValidatorResult isScopeValid(final Map<String, Object> payload) {
    final String scope = getScope(payload);
    if (scope != null && !scope.isBlank()) {
      if (scope.contains(expectedScope)) {
        return OAuth2TokenValidatorResult.success();
      } else {
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error(
                "invalid_token",
                "The scope provided in the token does not match the expected scope",
                null));
      }
    } else {
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("missing_token", "The required scope is" + " missing", null));
    }
  }

  private String getScope(final Map<String, Object> payload) {
    return (String) payload.get("scope");
  }
}
