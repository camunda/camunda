/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.sdk;

import io.camunda.auth.domain.model.AuthorizationGrantRequest;
import io.camunda.auth.domain.model.AuthorizationGrantResponse;
import io.camunda.auth.domain.model.TokenExchangeGrantRequest;
import io.camunda.auth.domain.model.TokenExchangeGrantResponse;
import io.camunda.auth.domain.model.TokenType;
import io.camunda.auth.domain.port.inbound.AuthenticationPort;
import io.camunda.auth.domain.port.inbound.AuthorizationGrantPort;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Public facade for the Camunda Auth SDK. This is the primary entry point for consumers — the only
 * class they need to interact with.
 *
 * <p>Instances are created by the Spring Boot auto-configuration or programmatically via {@link
 * #builder()}.
 */
public final class CamundaAuthSdk {

  private final AuthenticationFacade authentication;
  private final TokenExchangeFacade tokenExchange;
  private final AuthorizationGrantFacade authorizationGrant;

  CamundaAuthSdk(
      final AuthenticationFacade authentication,
      final TokenExchangeFacade tokenExchange,
      final AuthorizationGrantFacade authorizationGrant) {
    this.authentication = authentication;
    this.tokenExchange = tokenExchange;
    this.authorizationGrant = authorizationGrant;
  }

  public AuthenticationFacade authentication() {
    return authentication;
  }

  public TokenExchangeFacade tokenExchange() {
    return tokenExchange;
  }

  public AuthorizationGrantFacade authorizationGrant() {
    return authorizationGrant;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private AuthenticationPort authenticationPort;
    private AuthorizationGrantPort authorizationGrantPort;

    private Builder() {}

    public Builder authenticationPort(final AuthenticationPort authenticationPort) {
      this.authenticationPort = authenticationPort;
      return this;
    }

    public Builder authorizationGrantPort(final AuthorizationGrantPort authorizationGrantPort) {
      this.authorizationGrantPort = authorizationGrantPort;
      return this;
    }

    public CamundaAuthSdk build() {
      final AuthenticationFacade authFacade =
          authenticationPort != null
              ? new DefaultAuthenticationFacade(authenticationPort)
              : new NoOpAuthenticationFacade();

      final TokenExchangeFacade exchangeFacade =
          authorizationGrantPort != null
              ? new DefaultTokenExchangeFacade(authorizationGrantPort)
              : new NoOpTokenExchangeFacade();

      final AuthorizationGrantFacade grantFacade =
          authorizationGrantPort != null
              ? new DefaultAuthorizationGrantFacade(authorizationGrantPort)
              : new NoOpAuthorizationGrantFacade();

      return new CamundaAuthSdk(authFacade, exchangeFacade, grantFacade);
    }
  }

  // --- Default implementations ---

  private static final class DefaultAuthenticationFacade implements AuthenticationFacade {
    private final AuthenticationPort port;

    DefaultAuthenticationFacade(final AuthenticationPort port) {
      this.port = Objects.requireNonNull(port);
    }

    @Override
    public Optional<String> getCurrentUsername() {
      return port.getCurrentUsername();
    }

    @Override
    public Optional<String> getCurrentClientId() {
      return port.getCurrentClientId();
    }

    @Override
    public Optional<String> getCurrentToken() {
      return port.getCurrentToken();
    }

    @Override
    public List<String> getCurrentGroupIds() {
      return port.getCurrentGroupIds();
    }

    @Override
    public List<String> getCurrentRoleIds() {
      return port.getCurrentRoleIds();
    }

    @Override
    public List<String> getCurrentTenantIds() {
      return port.getCurrentTenantIds();
    }

    @Override
    public Map<String, Object> getCurrentClaims() {
      return port.getCurrentClaims();
    }

    @Override
    public boolean isAuthenticated() {
      return port.isAuthenticated();
    }
  }

  private static final class DefaultTokenExchangeFacade implements TokenExchangeFacade {
    private final AuthorizationGrantPort port;

    DefaultTokenExchangeFacade(final AuthorizationGrantPort port) {
      this.port = Objects.requireNonNull(port);
    }

    @Override
    public String getOnBehalfOfToken(
        final String subjectToken, final String targetAudience, final Set<String> scopes) {
      final TokenExchangeGrantRequest request =
          TokenExchangeGrantRequest.builder()
              .subjectToken(subjectToken)
              .subjectTokenType(TokenType.ACCESS_TOKEN)
              .audience(targetAudience)
              .scopes(scopes)
              .build();
      return port.authorize(request).accessToken();
    }

    @Override
    public TokenExchangeGrantResponse exchangeToken(final TokenExchangeGrantRequest request) {
      return (TokenExchangeGrantResponse) port.authorize(request);
    }

    @Override
    public boolean isTokenExchangeSupported() {
      return true;
    }
  }

  private static final class DefaultAuthorizationGrantFacade implements AuthorizationGrantFacade {
    private final AuthorizationGrantPort port;

    DefaultAuthorizationGrantFacade(final AuthorizationGrantPort port) {
      this.port = Objects.requireNonNull(port);
    }

    @Override
    public AuthorizationGrantResponse authorize(final AuthorizationGrantRequest request) {
      return port.authorize(request);
    }

    @Override
    public boolean isAvailable() {
      return true;
    }
  }

  // --- No-op implementations for when components are not configured ---

  private static final class NoOpAuthenticationFacade implements AuthenticationFacade {
    @Override
    public Optional<String> getCurrentUsername() {
      return Optional.empty();
    }

    @Override
    public Optional<String> getCurrentClientId() {
      return Optional.empty();
    }

    @Override
    public Optional<String> getCurrentToken() {
      return Optional.empty();
    }

    @Override
    public List<String> getCurrentGroupIds() {
      return List.of();
    }

    @Override
    public List<String> getCurrentRoleIds() {
      return List.of();
    }

    @Override
    public List<String> getCurrentTenantIds() {
      return List.of();
    }

    @Override
    public Map<String, Object> getCurrentClaims() {
      return Map.of();
    }

    @Override
    public boolean isAuthenticated() {
      return false;
    }
  }

  private static final class NoOpTokenExchangeFacade implements TokenExchangeFacade {
    @Override
    public String getOnBehalfOfToken(
        final String subjectToken, final String targetAudience, final Set<String> scopes) {
      throw new UnsupportedOperationException(
          "Token exchange is not configured. Enable it via camunda.auth.token-exchange.enabled=true");
    }

    @Override
    public TokenExchangeGrantResponse exchangeToken(final TokenExchangeGrantRequest request) {
      throw new UnsupportedOperationException(
          "Token exchange is not configured. Enable it via camunda.auth.token-exchange.enabled=true");
    }

    @Override
    public boolean isTokenExchangeSupported() {
      return false;
    }
  }

  private static final class NoOpAuthorizationGrantFacade implements AuthorizationGrantFacade {
    @Override
    public AuthorizationGrantResponse authorize(final AuthorizationGrantRequest request) {
      throw new UnsupportedOperationException(
          "Authorization grants are not configured. Enable via camunda.auth.token-exchange.enabled=true");
    }

    @Override
    public boolean isAvailable() {
      return false;
    }
  }
}
