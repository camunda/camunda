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
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.jwt.Jwt;

public class CamundaJwtAuthenticationTokenV2 extends AbstractAuthenticationToken {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaJwtAuthenticationTokenV2.class);

  private Function<OAuth2AuthorizeRequest, OAuth2AuthorizedClient> reAuthFunction;
  private Function<String, Jwt> stringTokenToJwt;

  private CamundaJwtUser principal;
  private Object credentials;

  private OAuth2AuthorizedClient authorizedClient;

  private volatile Jwt accessToken;

  public CamundaJwtAuthenticationTokenV2(
      final Jwt accessToken,
      final CamundaJwtUser principal,
      final OAuth2AuthorizedClient authorizedClient,
      final Object credentials,
      final Collection<? extends GrantedAuthority> authorities,
      final Function<OAuth2AuthorizeRequest, OAuth2AuthorizedClient> reAuthFunction,
      final Function<String, Jwt> stringTokenToJwt) {
    super(authorities);
    this.accessToken = accessToken;
    this.principal = principal;
    this.authorizedClient = authorizedClient;
    this.credentials = credentials;
    this.reAuthFunction = reAuthFunction;
    this.stringTokenToJwt = stringTokenToJwt;
    setAuthenticated(true);
  }

  public Jwt getAccessToken() {
    return accessToken;
  }

  @Override
  public Object getCredentials() {
    return credentials;
  }

  @Override
  public Object getPrincipal() {
    return principal;
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
        return false;
      }
    }
    return super.isAuthenticated();
  }

  // TODO: consider concurrency
  private void renewAccessToken() {
    LOG.debug("Renewing access token...");

    final OAuth2AuthorizeRequest req =
        OAuth2AuthorizeRequest.withAuthorizedClient(authorizedClient).build();

    authorizedClient = reAuthFunction.apply(req);
    final var newAccessToken = authorizedClient.getAccessToken();
    this.accessToken = stringTokenToJwt.apply(newAccessToken.getTokenValue());
    this.principal.refreshJwt(accessToken);

    LOG.info(
        "Access token renewed successfully. New expiration: {}", newAccessToken.getExpiresAt());
  }

  public Map<String, Object> getTokenAttributes() {
    return accessToken.getClaims();
  }

  private boolean hasExpired() {
    return accessToken.getExpiresAt().isAfter(Instant.now());
  }
}
