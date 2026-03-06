/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

import java.time.Instant;
import java.util.Set;

/**
 * Base type for all OAuth2 authorization grant responses. Each grant type defines a permitted
 * subtype carrying grant-type-specific fields (e.g., {@code issuedTokenType} for token exchange).
 */
public sealed interface AuthorizationGrantResponse
    permits TokenExchangeGrantResponse,
        ClientCredentialsGrantResponse,
        JwtBearerGrantResponse,
        AuthorizationCodeGrantResponse {

  /** The security token issued by the authorization server. */
  String accessToken();

  /** The token type (typically "Bearer"). */
  String tokenType();

  /** The lifetime in seconds of the access token. */
  long expiresIn();

  /** The scopes associated with the issued token. */
  Set<String> scope();

  /** The time the token was issued. */
  Instant issuedAt();

  /**
   * Returns {@code true} if the token has expired based on {@link #issuedAt()} and {@link
   * #expiresIn()}.
   */
  default boolean isExpired() {
    return Instant.now().isAfter(issuedAt().plusSeconds(expiresIn()));
  }
}
