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
 * Represents the response from an OAuth2 Client Credentials grant as defined by RFC 6749 Section
 * 4.4.3.
 *
 * <p>Contains only the base response fields — Client Credentials grants do not return additional
 * grant-type-specific fields.
 *
 * @param accessToken the security token issued by the authorization server
 * @param tokenType the token type (typically "Bearer")
 * @param expiresIn the lifetime in seconds of the access token
 * @param scope the scopes associated with the issued token
 * @param issuedAt the time the token was issued
 */
public record ClientCredentialsGrantResponse(
    String accessToken, String tokenType, long expiresIn, Set<String> scope, Instant issuedAt)
    implements AuthorizationGrantResponse {

  public ClientCredentialsGrantResponse {
    Objects.requireNonNull(accessToken, "accessToken must not be null");
    scope = scope != null ? Set.copyOf(scope) : Set.of();
    issuedAt = issuedAt != null ? issuedAt : Instant.now();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String accessToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private Set<String> scope;
    private Instant issuedAt;

    private Builder() {}

    public Builder accessToken(final String accessToken) {
      this.accessToken = accessToken;
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

    public Builder issuedAt(final Instant issuedAt) {
      this.issuedAt = issuedAt;
      return this;
    }

    public ClientCredentialsGrantResponse build() {
      return new ClientCredentialsGrantResponse(accessToken, tokenType, expiresIn, scope, issuedAt);
    }
  }
}
