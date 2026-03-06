/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

import java.util.Map;
import java.util.Set;

/**
 * Base type for all OAuth2 authorization grant requests. Following Spring Security's composability
 * pattern, each grant type is modeled as a permitted subtype of this sealed interface.
 *
 * <p>Common fields shared by all grant types are declared here; grant-type-specific fields live in
 * the concrete subtypes.
 */
public sealed interface AuthorizationGrantRequest
    permits TokenExchangeGrantRequest,
        ClientCredentialsGrantRequest,
        JwtBearerGrantRequest,
        AuthorizationCodeGrantRequest {

  /** The OAuth2 grant type for this request. */
  GrantType grantType();

  /** The logical name or URI of the target service. */
  String audience();

  /** The requested scopes for the resulting token. */
  Set<String> scopes();

  /** IdP-specific additional parameters. */
  Map<String, String> additionalParameters();
}
