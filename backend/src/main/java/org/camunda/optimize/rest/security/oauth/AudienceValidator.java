/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security.oauth;

import lombok.AllArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

@AllArgsConstructor
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final String audience;

    public OAuth2TokenValidatorResult validate(Jwt jwt) {
      if (jwt.getAudience().contains(audience)) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "The required audience is missing",
                                                                 null));
    }
}
