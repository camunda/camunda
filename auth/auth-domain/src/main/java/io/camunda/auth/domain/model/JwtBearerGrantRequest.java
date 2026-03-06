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
 * Represents an OAuth2 JWT Bearer grant request as defined by RFC 7523.
 *
 * <p>The {@link #grantType()} is always {@link GrantType#JWT_BEARER}. The {@code assertion} is the
 * JWT used as an authorization grant.
 *
 * @param assertion the JWT assertion used as the authorization grant
 * @param audience the logical name or URI of the target service
 * @param scopes the requested scopes for the resulting token
 * @param additionalParameters IdP-specific additional parameters
 */
public record JwtBearerGrantRequest(
    String assertion, String audience, Set<String> scopes, Map<String, String> additionalParameters)
    implements AuthorizationGrantRequest {

  public JwtBearerGrantRequest {
    Objects.requireNonNull(assertion, "assertion must not be null");
    scopes = scopes != null ? Set.copyOf(scopes) : Set.of();
    additionalParameters =
        additionalParameters != null ? Collections.unmodifiableMap(additionalParameters) : Map.of();
  }

  @Override
  public GrantType grantType() {
    return GrantType.JWT_BEARER;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String assertion;
    private String audience;
    private Set<String> scopes;
    private Map<String, String> additionalParameters;

    private Builder() {}

    public Builder assertion(final String assertion) {
      this.assertion = assertion;
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

    public Builder additionalParameters(final Map<String, String> additionalParameters) {
      this.additionalParameters = additionalParameters;
      return this;
    }

    public JwtBearerGrantRequest build() {
      return new JwtBearerGrantRequest(assertion, audience, scopes, additionalParameters);
    }
  }
}
