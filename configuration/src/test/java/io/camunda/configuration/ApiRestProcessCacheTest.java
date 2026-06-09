/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({UnifiedConfiguration.class, UnifiedConfigurationHelper.class})
public class ApiRestProcessCacheTest {

  @Nested
  class WithDefaultValues {
    final ProcessCache processCache;

    WithDefaultValues(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.processCache = unifiedConfiguration.getCamunda().getApi().getRest().getProcessCache();
    }

    @Test
    void shouldHaveDefaultMaxSize() {
      assertThat(processCache.getMaxSize()).isEqualTo(100);
    }

    @Test
    void shouldHaveDefaultExpirationIdle() {
      assertThat(processCache.getExpirationIdle()).isEqualTo(Duration.ofMillis(0));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.rest.process-cache.max-size=200",
        "camunda.api.rest.process-cache.expiration-idle=10ms",
      })
  class WithOnlyUnifiedConfigSet {
    final ProcessCache processCache;

    WithOnlyUnifiedConfigSet(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.processCache = unifiedConfiguration.getCamunda().getApi().getRest().getProcessCache();
    }

    @Test
    void shouldSetMaxSize() {
      assertThat(processCache.getMaxSize()).isEqualTo(200);
    }

    @Test
    void shouldSetExpirationIdle() {
      assertThat(processCache.getExpirationIdle()).isEqualTo(Duration.ofMillis(10));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.rest.processCache.maxSize=300",
        "camunda.rest.processCache.expirationIdleMillis=20",
      })
  class WithOnlyLegacySet {
    final ProcessCache processCache;

    WithOnlyLegacySet(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.processCache = unifiedConfiguration.getCamunda().getApi().getRest().getProcessCache();
    }

    @Test
    void shouldSetMaxSizeFromLegacy() {
      assertThat(processCache.getMaxSize()).isEqualTo(300);
    }

    @Test
    void shouldSetExpirationIdleFromLegacy() {
      assertThat(processCache.getExpirationIdle()).isEqualTo(Duration.ofMillis(20));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.api.rest.process-cache.max-size=200",
        "camunda.api.rest.process-cache.expiration-idle=10ms",
        // legacy
        "camunda.rest.processCache.maxSize=300",
        "camunda.rest.processCache.expirationIdleMillis=20",
      })
  class WithNewAndLegacySet {
    final ProcessCache processCache;

    WithNewAndLegacySet(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.processCache = unifiedConfiguration.getCamunda().getApi().getRest().getProcessCache();
    }

    @Test
    void shouldPreferNewMaxSize() {
      assertThat(processCache.getMaxSize()).isEqualTo(200);
    }

    @Test
    void shouldPreferNewExpirationIdle() {
      assertThat(processCache.getExpirationIdle()).isEqualTo(Duration.ofMillis(10));
    }
  }
}
