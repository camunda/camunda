/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import io.camunda.authentication.config.OidcAccessTokenDecoderFactory;
import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.model.CamundaAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
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
  private final OidcAccessTokenDecoderFactory accessTokenDecoderFactory;
  private final TokenClaimsConverter tokenClaimsConverter;
  private final HttpServletRequest request;
  private final Map<String, JwtDecoder> jwtDecoders;
  private final Map<String, List<String>> additionalJwkSetUrisByIssuer;
  private final Map<String, Boolean> preferIdTokenClaimsByRegistrationId;

  public OidcUserAuthenticationConverter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OidcAccessTokenDecoderFactory accessTokenDecoderFactory,
      final TokenClaimsConverter tokenClaimsConverter,
      final HttpServletRequest request) {
    this(
        authorizedClientRepository,
        accessTokenDecoderFactory,
        tokenClaimsConverter,
        request,
        Collections.emptyMap(),
        Collections.emptyMap());
  }

  public OidcUserAuthenticationConverter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OidcAccessTokenDecoderFactory accessTokenDecoderFactory,
      final TokenClaimsConverter tokenClaimsConverter,
      final HttpServletRequest request,
      final Map<String, List<String>> additionalJwkSetUrisByIssuer) {
    this(
        authorizedClientRepository,
        accessTokenDecoderFactory,
        tokenClaimsConverter,
        request,
        additionalJwkSetUrisByIssuer,
        Collections.emptyMap());
  }

  public OidcUserAuthenticationConverter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OidcAccessTokenDecoderFactory accessTokenDecoderFactory,
      final TokenClaimsConverter tokenClaimsConverter,
      final HttpServletRequest request,
      final Map<String, List<String>> additionalJwkSetUrisByIssuer,
      final Map<String, Boolean> preferIdTokenClaimsByRegistrationId) {
    this.authorizedClientRepository = authorizedClientRepository;
    this.accessTokenDecoderFactory = accessTokenDecoderFactory;
    this.tokenClaimsConverter = tokenClaimsConverter;
    this.request = request;
    this.additionalJwkSetUrisByIssuer =
        additionalJwkSetUrisByIssuer != null
            ? additionalJwkSetUrisByIssuer.entrySet().stream()
                .collect(
                    Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())))
            : Collections.emptyMap();
    this.preferIdTokenClaimsByRegistrationId =
        preferIdTokenClaimsByRegistrationId != null
            ? Map.copyOf(preferIdTokenClaimsByRegistrationId)
            : Collections.emptyMap();
    jwtDecoders = new ConcurrentHashMap<>();
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
    // When prefer-id-token-claims is enabled for the current registration we short-circuit the
    // access-token decode path entirely and use the OidcUser principal attributes (which Spring
    // populates with the ID-token and userInfo merged claims). This lets operators opt in to
    // sourcing authorisation-relevant claims from userInfo in setups where the access token is
    // signed by a key set that Camunda cannot reach, or lacks claims Camunda needs.
    if (shouldPreferIdTokenClaims(authenticationToken)) {
      return getIdTokenClaims(authenticationToken);
    }
    return Optional.ofNullable(getAccessTokenClaims(authenticationToken))
        .orElseGet(
            () -> {
              LOGGER.warn("Falling back to ID Token claims");
              return getIdTokenClaims(authenticationToken);
            });
  }

  private boolean shouldPreferIdTokenClaims(final OAuth2AuthenticationToken authenticationToken) {
    if (preferIdTokenClaimsByRegistrationId.isEmpty()) {
      return false;
    }
    final var registrationId = authenticationToken.getAuthorizedClientRegistrationId();
    return registrationId != null
        && Boolean.TRUE.equals(preferIdTokenClaimsByRegistrationId.get(registrationId));
  }

  protected Map<String, Object> getAccessTokenClaims(
      final OAuth2AuthenticationToken authenticationToken) {
    final var authorizedClient = getAuthorizedClient(authenticationToken, request);
    return Optional.ofNullable(authorizedClient)
        .map(OAuth2AuthorizedClient::getAccessToken)
        .map(OAuth2AccessToken::getTokenValue)
        .map(
            tokenValue -> {
              final var clientRegistration = authorizedClient.getClientRegistration();
              final var jwtDecoder = getJwtDecoder(clientRegistration);
              return decodeAccessToken(jwtDecoder, tokenValue);
            })
        .map(Jwt::getClaims)
        .orElse(null);
  }

  protected OAuth2AuthorizedClient getAuthorizedClient(
      final OAuth2AuthenticationToken authenticationToken, final HttpServletRequest request) {
    final var clientRegistrationId = authenticationToken.getAuthorizedClientRegistrationId();
    return authorizedClientRepository.loadAuthorizedClient(
        clientRegistrationId, authenticationToken, request);
  }

  protected Jwt decodeAccessToken(final JwtDecoder jwtDecoder, final String accessTokenValue) {
    try {
      return jwtDecoder.decode(accessTokenValue);
    } catch (final JwtException e) {
      LOGGER.warn("Failed to decode Access Token: '{}', returning null", e.getMessage());
      return null;
    }
  }

  protected JwtDecoder getJwtDecoder(final ClientRegistration clientRegistration) {
    final var clientRegistrationId = clientRegistration.getRegistrationId();
    return jwtDecoders.computeIfAbsent(
        clientRegistrationId,
        k -> {
          final var issuerUri = clientRegistration.getProviderDetails().getIssuerUri();
          // issuerUri may be null when configured without auto-discovery (e.g. explicit
          // jwkSetUri/authorizationUri/tokenUri). Guard against NPE on the immutable map.
          final var additionalUris =
              issuerUri != null ? additionalJwkSetUrisByIssuer.get(issuerUri) : null;
          return accessTokenDecoderFactory.createAccessTokenDecoder(
              clientRegistration, additionalUris);
        });
  }

  protected Map<String, Object> getIdTokenClaims(
      final OAuth2AuthenticationToken authenticationToken) {
    return authenticationToken.getPrincipal().getAttributes();
  }
}
