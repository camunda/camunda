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
import io.camunda.auth.domain.port.outbound.TokenCachePort;
import io.camunda.auth.domain.port.outbound.TokenExchangeClient;
import io.camunda.auth.domain.port.outbound.TokenStorePort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Domain service that orchestrates token exchange. Delegates to the configured {@link
 * TokenExchangeClient}, checks the cache, validates delegation chains, and persists audit records.
 */
public class TokenExchangeService implements TokenExchangePort {

  private static final Logger LOG = LoggerFactory.getLogger(TokenExchangeService.class);

  private final TokenExchangeClient client;
  private final TokenCachePort cache;
  private final TokenStorePort tokenStore;
  private final DelegationChainValidator chainValidator;

  public TokenExchangeService(
      final TokenExchangeClient client,
      final TokenCachePort cache,
      final TokenStorePort tokenStore,
      final DelegationChainValidator chainValidator) {
    this.client = Objects.requireNonNull(client, "client must not be null");
    this.cache = cache;
    this.tokenStore = tokenStore;
    this.chainValidator = chainValidator;
  }

  public TokenExchangeService(final TokenExchangeClient client, final TokenCachePort cache) {
    this(client, cache, null, null);
  }

  @Override
  public TokenExchangeResponse exchange(final TokenExchangeRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    // Check cache first
    if (cache != null) {
      final String cacheKey = computeCacheKey(request);
      final var cached = cache.get(cacheKey);
      if (cached.isPresent() && !cached.get().isExpired()) {
        LOG.debug("Cache hit for token exchange: audience={}", request.audience());
        return cached.get();
      }
    }

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

      // Cache the result
      if (cache != null) {
        cache.put(computeCacheKey(request), response);
      }

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

  static String computeCacheKey(final TokenExchangeRequest request) {
    final String raw =
        request.subjectToken()
            + "|"
            + request.audience()
            + "|"
            + String.join(",", request.scopes());
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      final byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (final NoSuchAlgorithmException e) {
      // SHA-256 is always available in JDK
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
