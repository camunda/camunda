/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.exchange.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.auth.domain.model.TokenExchangeResponse;
import io.camunda.auth.domain.model.TokenType;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InMemoryTokenCacheTest {

  @Test
  void shouldReturnEmptyForMissingKey() {
    // given
    final InMemoryTokenCache cache = new InMemoryTokenCache();

    // when
    final Optional<TokenExchangeResponse> result = cache.get("non-existent-key");

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldCacheAndRetrieveToken() {
    // given
    final InMemoryTokenCache cache = new InMemoryTokenCache();
    final String cacheKey = "test-key";
    final TokenExchangeResponse response = createResponse(3600);

    // when
    cache.put(cacheKey, response);
    final Optional<TokenExchangeResponse> result = cache.get(cacheKey);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().accessToken()).isEqualTo("test-access-token");
  }

  @Test
  void shouldEvictAll() {
    // given
    final InMemoryTokenCache cache = new InMemoryTokenCache();
    cache.put("key-1", createResponse(3600));
    cache.put("key-2", createResponse(3600));

    // when
    cache.evictAll();

    // then
    assertThat(cache.get("key-1")).isEmpty();
    assertThat(cache.get("key-2")).isEmpty();
  }

  @Test
  void shouldRespectMaxSize() throws InterruptedException {
    // given — maxSize=1, so only one entry should survive
    final InMemoryTokenCache cache = new InMemoryTokenCache(1, Duration.ofMinutes(5), 60);
    cache.put("key-1", createResponse(3600));
    cache.put("key-2", createResponse(3600));

    // Caffeine eviction is asynchronous — allow time for eviction processing
    Thread.sleep(200);

    // when
    final Optional<TokenExchangeResponse> result1 = cache.get("key-1");
    final Optional<TokenExchangeResponse> result2 = cache.get("key-2");

    // then — with maxSize=1, at most one entry should be present
    final long presentCount = (result1.isPresent() ? 1 : 0) + (result2.isPresent() ? 1 : 0);
    assertThat(presentCount).isLessThanOrEqualTo(1);
  }

  private TokenExchangeResponse createResponse(final long expiresIn) {
    return TokenExchangeResponse.builder()
        .accessToken("test-access-token")
        .issuedTokenType(TokenType.ACCESS_TOKEN)
        .tokenType("Bearer")
        .expiresIn(expiresIn)
        .scope(Set.of("read"))
        .issuedAt(Instant.now())
        .build();
  }
}
