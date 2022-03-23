/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security.platform;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
public class OptimizeStaticTokenDecoder implements JwtDecoder {
  private final ConfigurationService configurationService;

  @Override
  public Jwt decode(final String token) throws JwtException {
    Map<String, Object> headers = new HashMap<>();
    // The static token is not really a JWT Token, so the decoding here is a bit of a stretch in order to get the token
    // value. The header below has no meaning, it just needs to be set otherwise the Jwt constructor will not
    // generate an instance
    headers.put("typ", "SelfMade");
    Map<String, Object> claims = new HashMap<>();
    claims.put("token", token);
    validateToken(token).ifPresent(
      error -> {
        throw new JwtValidationException("validationErrorString", List.of(error));
      });
    return new Jwt(token, Instant.now(), Instant.MAX, headers, claims);
  }

  private Optional<OAuth2Error> validateToken(final String providedToken) {
    String expectedAccessToken = configurationService.getOptimizeApiConfiguration().getAccessToken();
    if (expectedAccessToken == null || expectedAccessToken.isEmpty()) {
     return Optional.of(new OAuth2Error("The config property 'api.accessToken' is not configured, therefore" +
                                          " all public API requests will be blocked. Please check the documentation " +
                                          "to set this property appropriately and restart the server."));
    } else if (!expectedAccessToken.equals(providedToken)) {
      return Optional.of(new OAuth2Error("Provided authorization token is invalid."));
    }
    return Optional.empty();
  }
}
