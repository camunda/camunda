/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security.oauth;

import java.util.Map;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@AllArgsConstructor
public class CustomClaimValidator implements OAuth2TokenValidator<Jwt> {
  private final String claimName;
  private final String expectedClaimValue;
  private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt jwt) {
    final JwtAuthenticationToken auth = (JwtAuthenticationToken) delegate.convert(jwt);
    final Map<String, Object> payload = auth.getTokenAttributes();
    return isClaimValid(payload);
  }

  private OAuth2TokenValidatorResult isClaimValid(final Map<String, Object> payload) {
    final String claimValue = getClaimValue(payload);
    if (!StringUtils.isBlank(claimValue)) {
      if (claimValue.equals(expectedClaimValue)) {
        return OAuth2TokenValidatorResult.success();
      } else {
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error(
                "invalid_token",
                "The claim provided in the token does not match the expected claim",
                null));
      }
    } else {
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("missing_token", "The required claim" + claimName + " is missing", null));
    }
  }

  private String getClaimValue(final Map<String, Object> payload) {
    return (String) payload.get(claimName);
  }
}
