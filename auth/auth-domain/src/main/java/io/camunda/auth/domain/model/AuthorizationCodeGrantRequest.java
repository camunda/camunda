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
 * Represents an OAuth2 Authorization Code grant request as defined by RFC 6749 Section 4.1.
 *
 * <p>The {@link #grantType()} is always {@link GrantType#AUTHORIZATION_CODE}. The {@code code} is
 * the authorization code received from the authorization server, and {@code redirectUri} must match
 * the URI used in the authorization request.
 *
 * @param code the authorization code received from the authorization server
 * @param redirectUri the redirect URI used in the authorization request
 * @param codeVerifier the PKCE code verifier (RFC 7636), optional
 * @param audience the logical name or URI of the target service
 * @param scopes the requested scopes for the resulting token
 * @param additionalParameters IdP-specific additional parameters
 */
public record AuthorizationCodeGrantRequest(
    String code,
    String redirectUri,
    String codeVerifier,
    String audience,
    Set<String> scopes,
    Map<String, String> additionalParameters)
    implements AuthorizationGrantRequest {

  public AuthorizationCodeGrantRequest {
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(redirectUri, "redirectUri must not be null");
    scopes = scopes != null ? Set.copyOf(scopes) : Set.of();
    additionalParameters =
        additionalParameters != null ? Collections.unmodifiableMap(additionalParameters) : Map.of();
  }

  @Override
  public GrantType grantType() {
    return GrantType.AUTHORIZATION_CODE;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String code;
    private String redirectUri;
    private String codeVerifier;
    private String audience;
    private Set<String> scopes;
    private Map<String, String> additionalParameters;

    private Builder() {}

    public Builder code(final String code) {
      this.code = code;
      return this;
    }

    public Builder redirectUri(final String redirectUri) {
      this.redirectUri = redirectUri;
      return this;
    }

    public Builder codeVerifier(final String codeVerifier) {
      this.codeVerifier = codeVerifier;
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

    public AuthorizationCodeGrantRequest build() {
      return new AuthorizationCodeGrantRequest(
          code, redirectUri, codeVerifier, audience, scopes, additionalParameters);
    }
  }
}
