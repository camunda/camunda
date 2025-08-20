/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.GatewayRestPropertiesOverride;
import io.camunda.configuration.beans.GatewayRestProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  GatewayRestPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
public class ApiRestProcessCacheTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.rest.process-cache.max-size=200",
        "camunda.api.rest.process-cache.expiration-idle=10ms",
      })
  class WithOnlyUnifiedConfigSet {
    final GatewayRestProperties gatewayRestProperties;

    WithOnlyUnifiedConfigSet(@Autowired final GatewayRestProperties gatewayRestProperties) {
      this.gatewayRestProperties = gatewayRestProperties;
    }

    @Test
    void shouldSetMaxSize() {
      assertThat(gatewayRestProperties.getProcessCache().getMaxSize()).isEqualTo(200);
    }

    @Test
    void shouldSetExpirationIdle() {
      assertThat(gatewayRestProperties.getProcessCache().getExpirationIdleMillis()).isEqualTo(10);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.rest.processCache.maxSize=300",
        "camunda.rest.processCache.expirationIdleMillis=20",
      })
  class WithOnlyLegacySet {
    final GatewayRestProperties gatewayRestProperties;

    WithOnlyLegacySet(@Autowired final GatewayRestProperties gatewayRestProperties) {
      this.gatewayRestProperties = gatewayRestProperties;
    }

    @Test
    void shouldSetMaxSize() {
      assertThat(gatewayRestProperties.getProcessCache().getMaxSize()).isEqualTo(300);
    }

    @Test
    void shouldSetExpirationIdle() {
      assertThat(gatewayRestProperties.getProcessCache().getExpirationIdleMillis()).isEqualTo(20);
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
    final GatewayRestProperties gatewayRestProperties;

    WithNewAndLegacySet(@Autowired final GatewayRestProperties gatewayRestProperties) {
      this.gatewayRestProperties = gatewayRestProperties;
    }

    @Test
    void shouldSetMaxSizeFromNew() {
      assertThat(gatewayRestProperties.getProcessCache().getMaxSize()).isEqualTo(200);
    }

    @Test
    void shouldSetExpirationIdleFromNew() {
      assertThat(gatewayRestProperties.getProcessCache().getExpirationIdleMillis()).isEqualTo(10);
    }
  }
}
