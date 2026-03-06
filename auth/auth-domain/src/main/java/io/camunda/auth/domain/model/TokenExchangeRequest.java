/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents an OAuth2 Token Exchange request as defined by RFC 8693.
 *
 * @param subjectToken the security token representing the identity of the party on behalf of whom
 *     the request is being made
 * @param subjectTokenType the type of the subject token
 * @param actorToken an optional security token representing the identity of the acting party
 * @param actorTokenType the type of the actor token (required if actorToken is present)
 * @param grantType the grant type to use for the exchange
 * @param audience the logical name or URI of the target service
 * @param scopes the requested scopes for the exchanged token
 * @param resource the resource URI (RFC 8707)
 * @param additionalParameters IdP-specific additional parameters
 */
public record TokenExchangeRequest(
    String subjectToken,
    TokenType subjectTokenType,
    String actorToken,
    TokenType actorTokenType,
    GrantType grantType,
    String audience,
    Set<String> scopes,
    String resource,
    Map<String, String> additionalParameters) {

  public TokenExchangeRequest {
    Objects.requireNonNull(subjectToken, "subjectToken must not be null");
    Objects.requireNonNull(subjectTokenType, "subjectTokenType must not be null");
    Objects.requireNonNull(grantType, "grantType must not be null");
    scopes = scopes != null ? Set.copyOf(scopes) : Set.of();
    additionalParameters =
        additionalParameters != null ? Collections.unmodifiableMap(additionalParameters) : Map.of();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String subjectToken;
    private TokenType subjectTokenType = TokenType.ACCESS_TOKEN;
    private String actorToken;
    private TokenType actorTokenType;
    private GrantType grantType = GrantType.TOKEN_EXCHANGE;
    private String audience;
    private Set<String> scopes;
    private String resource;
    private Map<String, String> additionalParameters;

    private Builder() {}

    public Builder subjectToken(final String subjectToken) {
      this.subjectToken = subjectToken;
      return this;
    }

    public Builder subjectTokenType(final TokenType subjectTokenType) {
      this.subjectTokenType = subjectTokenType;
      return this;
    }

    public Builder actorToken(final String actorToken) {
      this.actorToken = actorToken;
      return this;
    }

    public Builder actorTokenType(final TokenType actorTokenType) {
      this.actorTokenType = actorTokenType;
      return this;
    }

    public Builder grantType(final GrantType grantType) {
      this.grantType = grantType;
      return this;
    }

    public Builder audience(final String audience) {
      this.audience = audience;
      return this;
    }

    public Builder scopes(final Set<String> scopes) {
      this.scopes = scopes;
      return this;
    }

    public Builder resource(final String resource) {
      this.resource = resource;
      return this;
    }

    public Builder additionalParameters(final Map<String, String> additionalParameters) {
      this.additionalParameters = additionalParameters;
      return this;
    }

    public TokenExchangeRequest build() {
      return new TokenExchangeRequest(
          subjectToken,
          subjectTokenType,
          actorToken,
          actorTokenType,
          grantType,
          audience,
          scopes,
          resource,
          additionalParameters);
    }
  }
}
