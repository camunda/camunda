/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.sdk;

import io.camunda.auth.domain.model.TokenExchangeGrantRequest;
import io.camunda.auth.domain.model.TokenExchangeGrantResponse;
import java.util.Set;

/** Public interface for token exchange operations. */
public interface TokenExchangeFacade {

  /**
   * Performs an on-behalf-of token exchange, returning the access token for the target audience.
   *
   * @param subjectToken the user's current access token
   * @param targetAudience the logical name or URI of the target service
   * @param scopes the requested scopes
   * @return the exchanged access token string
   */
  String getOnBehalfOfToken(String subjectToken, String targetAudience, Set<String> scopes);

  /**
   * Performs a full token exchange and returns the complete response.
   *
   * @param request the token exchange request
   * @return the full token exchange response
   */
  TokenExchangeGrantResponse exchangeToken(TokenExchangeGrantRequest request);

  /** Returns whether token exchange is supported. */
  boolean isTokenExchangeSupported();
}
