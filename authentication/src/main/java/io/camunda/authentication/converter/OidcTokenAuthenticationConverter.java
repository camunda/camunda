/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.oidc.OidcClaimsProvider;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class OidcTokenAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private final TokenClaimsConverter tokenClaimsConverter;
  private final OidcClaimsProvider claimsProvider;

  public OidcTokenAuthenticationConverter(
      final TokenClaimsConverter tokenClaimsConverter, final OidcClaimsProvider claimsProvider) {
    this.tokenClaimsConverter = tokenClaimsConverter;
    this.claimsProvider = claimsProvider;
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return authentication instanceof JwtAuthenticationToken;
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    return Optional.of(authentication)
        .map(JwtAuthenticationToken.class::cast)
        .map(
            token -> {
              final Jwt jwt = token.getToken();
              return claimsProvider.claimsFor(jwt.getClaims(), jwt.getTokenValue());
            })
        .map(tokenClaimsConverter::convert)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Failed to convert 'JwtAuthenticationToken' to 'CamundaAuthentication'"));
  }
}
