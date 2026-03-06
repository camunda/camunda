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
import java.util.Set;

/**
 * Represents an OAuth2 Client Credentials grant request as defined by RFC 6749 Section 4.4.
 *
 * <p>The {@link #grantType()} is always {@link GrantType#CLIENT_CREDENTIALS}. Client identity
 * (client_id / client_secret) is handled by the transport layer (e.g., Spring Security) and is not
 * part of this domain model.
 *
 * @param audience the logical name or URI of the target service
 * @param scopes the requested scopes for the resulting token
 * @param additionalParameters IdP-specific additional parameters
 */
public record ClientCredentialsGrantRequest(
    String audience, Set<String> scopes, Map<String, String> additionalParameters)
    implements AuthorizationGrantRequest {

  public ClientCredentialsGrantRequest {
    scopes = scopes != null ? Set.copyOf(scopes) : Set.of();
    additionalParameters =
        additionalParameters != null ? Collections.unmodifiableMap(additionalParameters) : Map.of();
  }

  @Override
  public GrantType grantType() {
    return GrantType.CLIENT_CREDENTIALS;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String audience;
    private Set<String> scopes;
    private Map<String, String> additionalParameters;

    private Builder() {}

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

    public ClientCredentialsGrantRequest build() {
      return new ClientCredentialsGrantRequest(audience, scopes, additionalParameters);
    }
  }
}
