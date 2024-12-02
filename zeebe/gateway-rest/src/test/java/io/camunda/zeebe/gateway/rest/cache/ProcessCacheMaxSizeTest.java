/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"camunda.rest.process-cache.max-size=2"})
public class ProcessCacheMaxSizeTest extends ProcessCacheTestBase {

  @Test
  void shouldSetMaxSize() {
    assertThat(configuration.getProcessCache().getMaxSize()).isEqualTo(2);
    assertThat(configuration.getProcessCache().getExpirationMillis()).isNull();
  }

  @Test
  void shouldRefreshReadItemAndRemoveLeastRecentlyUsed() {
    // given
    processCache.getCacheItem(1L);
    processCache.getCacheItem(2L);
    processCache.getCache().cleanUp();
    assertThat(processCache.getCache().asMap()).hasSize(2);

    // when - read 1 and adding 3
    processCache.getCacheItem(1L);
    processCache.getCacheItem(3L);
    processCache.getCache().cleanUp();

    // then - 2 should be removed
    assertThat(processCache.getCache().asMap()).hasSize(2);
    assertThat(processCache.getCache().asMap().keySet()).containsExactlyInAnyOrder(1L, 3L);
  }
}
