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

@TestPropertySource(properties = {"camunda.rest.process-cache.expiration-millis=10"})
public class ProcessCacheInvalidationTest extends ProcessCacheTestBase {

  @Test
  void shouldSetExpirationMillis() {
    assertThat(configuration.getProcessCache().getMaxSize()).isEqualTo(100);
    assertThat(configuration.getProcessCache().getExpirationMillis()).isEqualTo(10);
  }

  @Test
  void shouldRemoveExpiredItem() throws InterruptedException {
    // given
    processCache.getCacheItem(1L);
    processCache.getCache().cleanUp();
    assertThat(processCache.getCache().asMap()).hasSize(1);

    // when - waiting ttl millis
    Thread.sleep(10);
    processCache.getCache().cleanUp();

    // then - cache should be empty
    assertThat(processCache.getCache().asMap()).isEmpty();
  }
}
