/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the response from an OAuth2 Token Exchange as defined by RFC 8693 Section 2.2.
 *
 * <p>Inherits common response fields from {@link AuthorizationGrantResponse} and adds
 * token-exchange-specific fields: {@code issuedTokenType} and {@code refreshToken}.
 *
 * @param accessToken the security token issued by the authorization server
 * @param issuedTokenType the type of the issued token
 * @param tokenType the token type (typically "Bearer")
 * @param expiresIn the lifetime in seconds of the access token
 * @param scope the scopes associated with the issued token
 * @param refreshToken an optional refresh token
 * @param issuedAt the time the token was issued
 */
public record TokenExchangeGrantResponse(
    String accessToken,
    TokenType issuedTokenType,
    String tokenType,
    long expiresIn,
    Set<String> scope,
    String refreshToken,
    Instant issuedAt)
    implements AuthorizationGrantResponse {

  public TokenExchangeGrantResponse {
    Objects.requireNonNull(accessToken, "accessToken must not be null");
    Objects.requireNonNull(issuedTokenType, "issuedTokenType must not be null");
    scope = scope != null ? Set.copyOf(scope) : Set.of();
    issuedAt = issuedAt != null ? issuedAt : Instant.now();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String accessToken;
    private TokenType issuedTokenType = TokenType.ACCESS_TOKEN;
    private String tokenType = "Bearer";
    private long expiresIn;
    private Set<String> scope;
    private String refreshToken;
    private Instant issuedAt;

    private Builder() {}

    public Builder accessToken(final String accessToken) {
      this.accessToken = accessToken;
      return this;
    }

    public Builder issuedTokenType(final TokenType issuedTokenType) {
      this.issuedTokenType = issuedTokenType;
      return this;
    }

    public Builder tokenType(final String tokenType) {
      this.tokenType = tokenType;
      return this;
    }

    public Builder expiresIn(final long expiresIn) {
      this.expiresIn = expiresIn;
      return this;
    }

    public Builder scope(final Set<String> scope) {
      this.scope = scope;
      return this;
    }

    public Builder refreshToken(final String refreshToken) {
      this.refreshToken = refreshToken;
      return this;
    }

    public Builder issuedAt(final Instant issuedAt) {
      this.issuedAt = issuedAt;
      return this;
    }

    public TokenExchangeGrantResponse build() {
      return new TokenExchangeGrantResponse(
          accessToken, issuedTokenType, tokenType, expiresIn, scope, refreshToken, issuedAt);
    }
  }
}
