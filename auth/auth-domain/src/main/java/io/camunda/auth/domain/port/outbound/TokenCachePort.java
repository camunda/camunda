/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.port.outbound;

import io.camunda.auth.domain.model.TokenExchangeResponse;
import java.util.Optional;

/** SPI for caching exchanged tokens to reduce IdP round-trips. */
public interface TokenCachePort {

  /**
   * Retrieves a cached token for the given cache key.
   *
   * @param cacheKey the cache key (typically a hash of subjectToken + audience + scope)
   * @return the cached response if present and not expired
   */
  Optional<TokenExchangeResponse> get(String cacheKey);

  /**
   * Stores a token exchange response in the cache.
   *
   * @param cacheKey the cache key
   * @param response the token exchange response to cache
   */
  void put(String cacheKey, TokenExchangeResponse response);

  /**
   * Evicts all cached tokens for the given subject principal.
   *
   * @param subjectId the subject principal ID
   */
  void evictBySubject(String subjectId);

  /** Evicts all cached tokens. */
  void evictAll();
}
