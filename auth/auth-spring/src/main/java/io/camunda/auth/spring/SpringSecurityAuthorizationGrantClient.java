/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring;

import io.camunda.auth.domain.exception.AuthorizationGrantException;
import io.camunda.auth.domain.model.AuthorizationCodeGrantRequest;
import io.camunda.auth.domain.model.AuthorizationCodeGrantResponse;
import io.camunda.auth.domain.model.AuthorizationGrantRequest;
import io.camunda.auth.domain.model.AuthorizationGrantResponse;
import io.camunda.auth.domain.model.ClientCredentialsGrantRequest;
import io.camunda.auth.domain.model.ClientCredentialsGrantResponse;
import io.camunda.auth.domain.model.JwtBearerGrantRequest;
import io.camunda.auth.domain.model.JwtBearerGrantResponse;
import io.camunda.auth.domain.model.TokenExchangeGrantRequest;
import io.camunda.auth.domain.model.TokenExchangeGrantResponse;
import io.camunda.auth.domain.model.TokenType;
import io.camunda.auth.domain.port.outbound.AuthorizationGrantClient;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Implements the domain's {@link AuthorizationGrantClient} by delegating to Spring Security's
 * {@link OAuth2AuthorizedClientManager}. Dispatches to the correct OAuth2 flow based on the sealed
 * request type.
 *
 * <p>Consumers configure grant types via standard Spring Security OAuth2 Client properties:
 *
 * <pre>{@code
 * spring.security.oauth2.client.registration.<id>.authorization-grant-type=...
 * spring.security.oauth2.client.registration.<id>.client-id=...
 * spring.security.oauth2.client.registration.<id>.client-secret=...
 * spring.security.oauth2.client.provider.<id>.token-uri=...
 * }</pre>
 */
public class SpringSecurityAuthorizationGrantClient implements AuthorizationGrantClient {

  private static final Logger LOG =
      LoggerFactory.getLogger(SpringSecurityAuthorizationGrantClient.class);

  private final OAuth2AuthorizedClientManager authorizedClientManager;
  private final String clientRegistrationId;

  public SpringSecurityAuthorizationGrantClient(
      final OAuth2AuthorizedClientManager authorizedClientManager,
      final String clientRegistrationId) {
    this.authorizedClientManager = authorizedClientManager;
    this.clientRegistrationId = clientRegistrationId;
  }

  @Override
  public AuthorizationGrantResponse authorize(final AuthorizationGrantRequest request) {
    final OAuth2AuthorizeRequest authorizeRequest = buildAuthorizeRequest(request);

    final OAuth2AuthorizedClient authorizedClient =
        authorizedClientManager.authorize(authorizeRequest);

    if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
      throw new AuthorizationGrantException.InvalidGrant(
          "Authorization grant failed — no authorized client returned for registration: "
              + clientRegistrationId);
    }

    final var accessToken = authorizedClient.getAccessToken();
    final Instant issuedAt = accessToken.getIssuedAt();
    final Instant expiresAt = accessToken.getExpiresAt();
    final long expiresIn =
        (issuedAt != null && expiresAt != null)
            ? expiresAt.getEpochSecond() - issuedAt.getEpochSecond()
            : 0;

    final Set<String> scopes = accessToken.getScopes();
    final String refreshTokenValue =
        authorizedClient.getRefreshToken() != null
            ? authorizedClient.getRefreshToken().getTokenValue()
            : null;

    LOG.debug(
        "Authorization grant successful via Spring Security OAuth2 Client for registration={}",
        clientRegistrationId);

    return buildResponse(
        request, accessToken.getTokenValue(), expiresIn, scopes, refreshTokenValue, issuedAt);
  }

  private OAuth2AuthorizeRequest buildAuthorizeRequest(final AuthorizationGrantRequest request) {
    return switch (request) {
      case final TokenExchangeGrantRequest tokenExchange ->
          OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
              .principal(tokenExchange.subjectToken())
              .attribute(
                  OAuth2AuthorizeRequest.class.getName() + ".SUBJECT_TOKEN",
                  tokenExchange.subjectToken())
              .attribute(
                  OAuth2AuthorizeRequest.class.getName() + ".SUBJECT_TOKEN_TYPE",
                  tokenExchange.subjectTokenType().uri())
              .build();
      case final ClientCredentialsGrantRequest clientCredentials ->
          OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
              .principal("client-credentials")
              .build();
      case final JwtBearerGrantRequest jwtBearer ->
          OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
              .principal(jwtBearer.assertion())
              .attribute(
                  OAuth2AuthorizeRequest.class.getName() + ".JWT_ASSERTION", jwtBearer.assertion())
              .build();
      case final AuthorizationCodeGrantRequest authCode ->
          OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
              .principal(authCode.code())
              .attribute(
                  OAuth2AuthorizeRequest.class.getName() + ".AUTHORIZATION_CODE", authCode.code())
              .attribute(
                  OAuth2AuthorizeRequest.class.getName() + ".REDIRECT_URI", authCode.redirectUri())
              .build();
    };
  }

  private AuthorizationGrantResponse buildResponse(
      final AuthorizationGrantRequest request,
      final String accessTokenValue,
      final long expiresIn,
      final Set<String> scopes,
      final String refreshTokenValue,
      final Instant issuedAt) {
    return switch (request) {
      case TokenExchangeGrantRequest ignored ->
          TokenExchangeGrantResponse.builder()
              .accessToken(accessTokenValue)
              .issuedTokenType(TokenType.ACCESS_TOKEN)
              .tokenType("Bearer")
              .expiresIn(expiresIn)
              .scope(scopes)
              .refreshToken(refreshTokenValue)
              .issuedAt(issuedAt)
              .build();
      case ClientCredentialsGrantRequest ignored ->
          ClientCredentialsGrantResponse.builder()
              .accessToken(accessTokenValue)
              .tokenType("Bearer")
              .expiresIn(expiresIn)
              .scope(scopes)
              .issuedAt(issuedAt)
              .build();
      case JwtBearerGrantRequest ignored ->
          JwtBearerGrantResponse.builder()
              .accessToken(accessTokenValue)
              .tokenType("Bearer")
              .expiresIn(expiresIn)
              .scope(scopes)
              .issuedAt(issuedAt)
              .build();
      case AuthorizationCodeGrantRequest ignored ->
          AuthorizationCodeGrantResponse.builder()
              .accessToken(accessTokenValue)
              .tokenType("Bearer")
              .expiresIn(expiresIn)
              .scope(scopes)
              .refreshToken(refreshTokenValue)
              .issuedAt(issuedAt)
              .build();
    };
  }
}
