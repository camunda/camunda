/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.spring.converter.OidcUserAuthenticationConverter;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

final class ProviderAwareOidcUserAuthenticationConverter extends OidcUserAuthenticationConverter {

  private final LazyTokenClaimsConverter defaultTokenClaimsConverter;
  private final Map<String, LazyTokenClaimsConverter> tokenClaimsConvertersByRegistrationId;
  private final Map<String, List<String>> identityClaimsByRegistrationId;

  ProviderAwareOidcUserAuthenticationConverter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OidcAccessTokenDecoderFactory accessTokenDecoderFactory,
      final LazyTokenClaimsConverter defaultTokenClaimsConverter,
      final HttpServletRequest request,
      final Map<String, List<String>> additionalJwkSetUrisByIssuer,
      final Map<String, Boolean> preferIdTokenClaimsByRegistrationId,
      final Map<String, LazyTokenClaimsConverter> tokenClaimsConvertersByRegistrationId,
      final Map<String, List<String>> identityClaimsByRegistrationId) {
    super(
        authorizedClientRepository,
        accessTokenDecoderFactory,
        defaultTokenClaimsConverter,
        request,
        additionalJwkSetUrisByIssuer,
        preferIdTokenClaimsByRegistrationId);
    this.defaultTokenClaimsConverter = defaultTokenClaimsConverter;
    this.tokenClaimsConvertersByRegistrationId = Map.copyOf(tokenClaimsConvertersByRegistrationId);
    this.identityClaimsByRegistrationId = Map.copyOf(identityClaimsByRegistrationId);
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    final var oauth2Authentication = (OAuth2AuthenticationToken) authentication;
    final var registrationId = oauth2Authentication.getAuthorizedClientRegistrationId();
    final var tokenClaimsConverter =
        tokenClaimsConvertersByRegistrationId.getOrDefault(
            registrationId, defaultTokenClaimsConverter);
    try {
      final var claims = new HashMap<>(getClaims(oauth2Authentication));
      identityClaimsByRegistrationId
          .getOrDefault(registrationId, List.of())
          .forEach(claim -> normalizeUriClaim(claims, claim));
      return tokenClaimsConverter.convert(claims);
    } catch (final IllegalArgumentException e) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, e.getMessage(), null), e);
    }
  }

  private static void normalizeUriClaim(final Map<String, Object> claims, final String claimName) {
    final var value = claims.get(claimName);
    if (value instanceof URI || value instanceof URL) {
      claims.put(claimName, value.toString());
    }
  }
}
