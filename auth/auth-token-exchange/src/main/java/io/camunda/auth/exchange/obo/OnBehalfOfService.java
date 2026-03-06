/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.exchange.obo;

import io.camunda.auth.domain.model.GrantType;
import io.camunda.auth.domain.model.TokenExchangeRequest;
import io.camunda.auth.domain.model.TokenExchangeResponse;
import io.camunda.auth.domain.model.TokenType;
import io.camunda.auth.domain.service.DelegationChainValidator;
import io.camunda.auth.domain.service.TokenExchangeService;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the On-Behalf-Of (OBO) token exchange flow. Validates the delegation chain (if a
 * validator is configured), delegates to the domain {@link TokenExchangeService}, and returns the
 * resulting access token.
 */
public class OnBehalfOfService {

  private static final Logger LOG = LoggerFactory.getLogger(OnBehalfOfService.class);

  private final TokenExchangeService tokenExchangeService;
  private final DelegationChainValidator chainValidator;

  public OnBehalfOfService(final TokenExchangeService tokenExchangeService) {
    this(tokenExchangeService, null);
  }

  public OnBehalfOfService(
      final TokenExchangeService tokenExchangeService,
      final DelegationChainValidator chainValidator) {
    this.tokenExchangeService =
        Objects.requireNonNull(tokenExchangeService, "tokenExchangeService must not be null");
    this.chainValidator = chainValidator;
  }

  /**
   * Performs an On-Behalf-Of token exchange.
   *
   * @param subjectToken the subject token representing the original user
   * @param targetAudience the logical name or URI of the target service
   * @param scopes the requested scopes for the exchanged token
   * @return the access token from the exchange response
   */
  public String getOnBehalfOfToken(
      final String subjectToken, final String targetAudience, final Set<String> scopes) {
    Objects.requireNonNull(subjectToken, "subjectToken must not be null");
    Objects.requireNonNull(targetAudience, "targetAudience must not be null");

    LOG.debug("Starting OBO token exchange for audience={}", targetAudience);

    // Validate delegation chain if validator is configured
    if (chainValidator != null) {
      chainValidator.validate(subjectToken);
    }

    // Build the token exchange request
    final TokenExchangeRequest request =
        TokenExchangeRequest.builder()
            .subjectToken(subjectToken)
            .subjectTokenType(TokenType.ACCESS_TOKEN)
            .grantType(GrantType.TOKEN_EXCHANGE)
            .audience(targetAudience)
            .scopes(scopes)
            .build();

    // Delegate to the domain service (handles caching, adapter selection, audit)
    final TokenExchangeResponse response = tokenExchangeService.exchange(request);

    LOG.debug("OBO token exchange completed for audience={}", targetAudience);
    return response.accessToken();
  }
}
