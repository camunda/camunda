/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.service;

import io.camunda.auth.domain.exception.TokenExchangeException;
import io.camunda.auth.domain.model.TokenExchangeRequest;
import io.camunda.auth.domain.model.TokenExchangeResponse;
import io.camunda.auth.domain.model.TokenMetadata;
import io.camunda.auth.domain.model.TokenMetadata.ExchangeStatus;
import io.camunda.auth.domain.port.inbound.TokenExchangePort;
import io.camunda.auth.domain.port.outbound.TokenExchangeClient;
import io.camunda.auth.domain.port.outbound.TokenStorePort;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Domain service that orchestrates token exchange. Delegates to the configured {@link
 * TokenExchangeClient}, validates delegation chains, and persists audit records.
 *
 * <p>Token caching is handled by Spring Security's {@code OAuth2AuthorizedClientService} — this
 * service focuses on the exchange itself and audit trail.
 */
public class TokenExchangeService implements TokenExchangePort {

  private static final Logger LOG = LoggerFactory.getLogger(TokenExchangeService.class);

  private final TokenExchangeClient client;
  private final TokenStorePort tokenStore;
  private final DelegationChainValidator chainValidator;

  public TokenExchangeService(
      final TokenExchangeClient client,
      final TokenStorePort tokenStore,
      final DelegationChainValidator chainValidator) {
    this.client = Objects.requireNonNull(client, "client must not be null");
    this.tokenStore = tokenStore;
    this.chainValidator = chainValidator;
  }

  public TokenExchangeService(final TokenExchangeClient client) {
    this(client, null, null);
  }

  @Override
  public TokenExchangeResponse exchange(final TokenExchangeRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    // Validate delegation chain if validator is configured
    if (chainValidator != null && request.actorToken() != null) {
      chainValidator.validate(request.subjectToken());
    }

    // Perform exchange
    final String exchangeId = UUID.randomUUID().toString();
    final Instant exchangeTime = Instant.now();

    try {
      LOG.debug("Performing token exchange for audience={}", request.audience());
      final TokenExchangeResponse response = client.exchange(request);

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

    } catch (final TokenExchangeException e) {
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
