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
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

public class OidcTokenAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private final TokenClaimsConverter tokenClaimsConverter;

  public OidcTokenAuthenticationConverter(final TokenClaimsConverter tokenClaimsConverter) {
    this.tokenClaimsConverter = tokenClaimsConverter;
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return Optional.ofNullable(authentication)
        .filter(AbstractOAuth2TokenAuthenticationToken.class::isInstance)
        .isPresent();
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    return Optional.of(authentication)
        .map(AbstractOAuth2TokenAuthenticationToken.class::cast)
        .map(AbstractOAuth2TokenAuthenticationToken::getTokenAttributes)
        .map(tokenClaimsConverter::convert)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Failed to convert 'AbstractOAuth2TokenAuthenticationToken' to 'CamundaAuthentication"));
  }
}
