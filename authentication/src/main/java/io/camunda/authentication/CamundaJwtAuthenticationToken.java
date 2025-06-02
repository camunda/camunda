/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.entity.CamundaJwtUser;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

public class CamundaJwtAuthenticationToken extends AbstractOAuth2TokenAuthenticationToken<Jwt> {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaJwtAuthenticationToken.class);
  final Function<OAuth2AuthorizeRequest, OAuth2AuthorizedClient> reAuthFunction;
  private OAuth2AuthorizedClient authorizedClient;

  public CamundaJwtAuthenticationToken(
      final Jwt token,
      final CamundaJwtUser principal,
      final OAuth2AuthorizedClient authorizedClient,
      final Object credentials,
      final Collection<? extends GrantedAuthority> authorities,
      final Function<OAuth2AuthorizeRequest, OAuth2AuthorizedClient> reAuthFunction) {
    super(token, principal, credentials, authorities);
    this.authorizedClient = authorizedClient;
    this.reAuthFunction = reAuthFunction;
    setAuthenticated(true);
  }

  @Override
  public Map<String, Object> getTokenAttributes() {
    return getToken().getClaims();
  }

  @Override
  public boolean isAuthenticated() {
    if (hasExpired()) {
      LOG.info("Access token is expired");
      LOG.info("Get a new access token by using refresh token");
      try {
        renewAccessToken();
      } catch (final Exception e) {
        LOG.error("Renewing access token failed with exception", e);
        setAuthenticated(false);
      }
    }
    return super.isAuthenticated();
  }

  private boolean hasExpired() {
    return Optional.ofNullable(authorizedClient)
        .map(OAuth2AuthorizedClient::getAccessToken)
        .map(AbstractOAuth2Token::getExpiresAt)
        .map(ea -> ea.isBefore(Instant.now()))
        .orElse(false);
  }

  private void renewAccessToken() {
    final OAuth2AuthorizeRequest req =
        OAuth2AuthorizeRequest.withAuthorizedClient(authorizedClient).build();
    authorizedClient = reAuthFunction.apply(req);
    final var newAccessToken = authorizedClient.getAccessToken();

    final JwtDecoder decoder = null; // will pass decoder or function
    final var jwtToken = decoder.decode(newAccessToken.getTokenValue());
    // update this auth token
    // update principal token
  }
}
