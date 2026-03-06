/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring;

import io.camunda.auth.domain.model.TokenExchangeRequest;
import io.camunda.auth.domain.model.TokenExchangeResponse;
import io.camunda.auth.domain.model.TokenType;
import io.camunda.auth.domain.port.outbound.TokenExchangeClient;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Implements the domain's {@link TokenExchangeClient} by delegating to Spring Security's {@link
 * OAuth2AuthorizedClientManager}. This leverages Spring Security's built-in support for OAuth2
 * Token Exchange (RFC 8693) via {@code TokenExchangeOAuth2AuthorizedClientProvider}.
 *
 * <p>Consumers configure token exchange via standard Spring Security OAuth2 Client properties:
 *
 * <pre>{@code
 * spring.security.oauth2.client.registration.<id>.authorization-grant-type=urn:ietf:params:oauth:grant-type:token-exchange
 * spring.security.oauth2.client.registration.<id>.client-id=...
 * spring.security.oauth2.client.registration.<id>.client-secret=...
 * spring.security.oauth2.client.provider.<id>.token-uri=...
 * }</pre>
 */
public class SpringSecurityTokenExchangeClient implements TokenExchangeClient {

  private static final Logger LOG =
      LoggerFactory.getLogger(SpringSecurityTokenExchangeClient.class);

  private final OAuth2AuthorizedClientManager authorizedClientManager;
  private final String clientRegistrationId;

  public SpringSecurityTokenExchangeClient(
      final OAuth2AuthorizedClientManager authorizedClientManager,
      final String clientRegistrationId) {
    this.authorizedClientManager = authorizedClientManager;
    this.clientRegistrationId = clientRegistrationId;
  }

  @Override
  public TokenExchangeResponse exchange(final TokenExchangeRequest request) {
    final OAuth2AuthorizeRequest authorizeRequest =
        OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
            .principal(request.subjectToken())
            .attribute(
                OAuth2AuthorizeRequest.class.getName() + ".SUBJECT_TOKEN", request.subjectToken())
            .attribute(
                OAuth2AuthorizeRequest.class.getName() + ".SUBJECT_TOKEN_TYPE",
                request.subjectTokenType().uri())
            .build();

    final OAuth2AuthorizedClient authorizedClient =
        authorizedClientManager.authorize(authorizeRequest);

    if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
      throw new io.camunda.auth.domain.exception.TokenExchangeException.InvalidGrant(
          "Token exchange failed — no authorized client returned for registration: "
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
        "Token exchange successful via Spring Security OAuth2 Client for registration={}",
        clientRegistrationId);

    return TokenExchangeResponse.builder()
        .accessToken(accessToken.getTokenValue())
        .issuedTokenType(TokenType.ACCESS_TOKEN)
        .tokenType("Bearer")
        .expiresIn(expiresIn)
        .scope(scopes)
        .refreshToken(refreshTokenValue)
        .issuedAt(issuedAt)
        .build();
  }
}
