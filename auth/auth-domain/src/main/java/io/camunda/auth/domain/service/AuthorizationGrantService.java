/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.service;

import io.camunda.auth.domain.exception.AuthorizationGrantException;
import io.camunda.auth.domain.model.AuthorizationGrantRequest;
import io.camunda.auth.domain.model.AuthorizationGrantResponse;
import io.camunda.auth.domain.model.TokenExchangeGrantRequest;
import io.camunda.auth.domain.model.TokenMetadata;
import io.camunda.auth.domain.model.TokenMetadata.ExchangeStatus;
import io.camunda.auth.domain.port.inbound.AuthorizationGrantPort;
import io.camunda.auth.domain.port.outbound.AuthorizationGrantClient;
import io.camunda.auth.domain.port.outbound.TokenStorePort;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Domain service that orchestrates authorization grant operations for all grant types. Delegates to
 * the configured {@link AuthorizationGrantClient}, validates delegation chains (for token exchange
 * only), and persists audit records.
 *
 * <p>Token caching is handled by Spring Security's {@code OAuth2AuthorizedClientService} — this
 * service focuses on the grant itself and audit trail.
 */
public class AuthorizationGrantService implements AuthorizationGrantPort {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationGrantService.class);

  private final AuthorizationGrantClient client;
  private final TokenStorePort tokenStore;
  private final DelegationChainValidator chainValidator;

  public AuthorizationGrantService(
      final AuthorizationGrantClient client,
      final TokenStorePort tokenStore,
      final DelegationChainValidator chainValidator) {
    this.client = Objects.requireNonNull(client, "client must not be null");
    this.tokenStore = tokenStore;
    this.chainValidator = chainValidator;
  }

  public AuthorizationGrantService(final AuthorizationGrantClient client) {
    this(client, null, null);
  }

  @Override
  public AuthorizationGrantResponse authorize(final AuthorizationGrantRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    // Validate delegation chain only for token exchange with an actor token
    if (chainValidator != null
        && request instanceof final TokenExchangeGrantRequest tokenExchange
        && tokenExchange.actorToken() != null) {
      chainValidator.validate(tokenExchange.subjectToken());
    }

    // Perform authorization grant
    final String exchangeId = UUID.randomUUID().toString();
    final Instant exchangeTime = Instant.now();

    try {
      LOG.debug(
          "Performing authorization grant type={} for audience={}",
          request.grantType(),
          request.audience());
      final AuthorizationGrantResponse response = client.authorize(request);

      // Store audit record
      if (tokenStore != null) {
        tokenStore.store(
            TokenMetadata.builder()
                .exchangeId(exchangeId)
                .targetAudience(request.audience())
                .grantedScopes(response.scope())
                .exchangeTime(exchangeTime)
                .expiryTime(exchangeTime.plusSeconds(response.expiresIn()))
                .exchangeStatus(ExchangeStatus.SUCCESS)
                .build());
      }

      return response;

    } catch (final AuthorizationGrantException e) {
      // Store failed audit record
      if (tokenStore != null) {
        tokenStore.store(
            TokenMetadata.builder()
                .exchangeId(exchangeId)
                .targetAudience(request.audience())
                .exchangeTime(exchangeTime)
                .exchangeStatus(ExchangeStatus.FAILED)
                .build());
      }
      throw e;
    }
  }
}
