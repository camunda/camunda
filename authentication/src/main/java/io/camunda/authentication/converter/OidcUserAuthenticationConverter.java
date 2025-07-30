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
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

public class OidcUserAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OidcUserAuthenticationConverter.class);

  private final OAuth2AuthorizedClientRepository authorizedClientRepository;
  private final JwtDecoder jwtDecoder;
  private final TokenClaimsConverter tokenClaimsConverter;
  private final HttpServletRequest request;

  public OidcUserAuthenticationConverter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final JwtDecoder jwtDecoder,
      final TokenClaimsConverter tokenClaimsConverter,
      final HttpServletRequest request) {
    this.authorizedClientRepository = authorizedClientRepository;
    this.jwtDecoder = jwtDecoder;
    this.tokenClaimsConverter = tokenClaimsConverter;
    this.request = request;
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return Optional.ofNullable(authentication)
        .filter(OAuth2AuthenticationToken.class::isInstance)
        .isPresent();
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    return Optional.of(authentication)
        .map(OAuth2AuthenticationToken.class::cast)
        .map(this::getClaims)
        .map(tokenClaimsConverter::convert)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Failed to convert 'OAuth2AuthenticationToken' to 'CamundaAuthentication"));
  }

  protected Map<String, Object> getClaims(final OAuth2AuthenticationToken authenticationToken) {
    return Optional.ofNullable(getAccessTokenClaims(authenticationToken))
        .orElseGet(
            () -> {
              LOGGER.warn("Falling back to ID Token claims");
              return getIdTokenClaims(authenticationToken);
            });
  }

  protected Map<String, Object> getAccessTokenClaims(
      final OAuth2AuthenticationToken authenticationToken) {
    final var authorizedClient = getAuthorizedClient(authenticationToken, request);
    return Optional.ofNullable(authorizedClient)
        .map(OAuth2AuthorizedClient::getAccessToken)
        .map(OAuth2AccessToken::getTokenValue)
        .map(this::decodeAccessToken)
        .map(Jwt::getClaims)
        .orElse(null);
  }

  protected OAuth2AuthorizedClient getAuthorizedClient(
      final OAuth2AuthenticationToken authenticationToken, final HttpServletRequest request) {
    final var clientRegistrationId = authenticationToken.getAuthorizedClientRegistrationId();
    return authorizedClientRepository.loadAuthorizedClient(
        clientRegistrationId, authenticationToken, request);
  }

  protected Jwt decodeAccessToken(final String accessTokenValue) {
    try {
      return jwtDecoder.decode(accessTokenValue);
    } catch (final JwtException e) {
      LOGGER.warn("Failed to decode Access Token: '{}', returning null", e.getMessage());
      return null;
    }
  }

  protected Map<String, Object> getIdTokenClaims(
      final OAuth2AuthenticationToken authenticationToken) {
    return authenticationToken.getPrincipal().getAttributes();
  }
}
