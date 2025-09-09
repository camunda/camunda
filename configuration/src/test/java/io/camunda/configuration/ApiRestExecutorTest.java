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
public class ApiRestExecutorTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.rest.executor.core-pool-size-multiplier=5",
        "camunda.api.rest.executor.max-pool-size-multiplier=10",
        "camunda.api.rest.executor.keep-alive=120s",
        "camunda.api.rest.executor.queue-capacity=128",
      })
  class WithOnlyUnifiedConfigSet {
    final GatewayRestProperties gatewayRestProperties;

    WithOnlyUnifiedConfigSet(@Autowired final GatewayRestProperties gatewayRestProperties) {
      this.gatewayRestProperties = gatewayRestProperties;
    }

    @Test
    void shouldSetCorePoolSizeMultiplier() {
      assertThat(gatewayRestProperties.getApiExecutor().getCorePoolSizeMultiplier()).isEqualTo(5);
    }

    @Test
    void shouldSetMaxPoolSizeMultiplier() {

      assertThat(gatewayRestProperties.getApiExecutor().getMaxPoolSizeMultiplier()).isEqualTo(10);
    }

    @Test
    void shouldSetKeepAliveSeconds() {
      assertThat(gatewayRestProperties.getApiExecutor().getKeepAliveSeconds()).isEqualTo(120);
    }

    @Test
    void shouldSetQueueCapacity() {
      assertThat(gatewayRestProperties.getApiExecutor().getQueueCapacity()).isEqualTo(128);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.rest.apiExecutor.corePoolSizeMultiplier=10",
        "camunda.rest.apiExecutor.maxPoolSizeMultiplier=15",
        "camunda.rest.apiExecutor.keepAliveSeconds=180",
        "camunda.rest.apiExecutor.queueCapacity=192",
      })
  class WithOnlyLegacySet {
    final GatewayRestProperties gatewayRestProperties;

    WithOnlyLegacySet(@Autowired final GatewayRestProperties gatewayRestProperties) {
      this.gatewayRestProperties = gatewayRestProperties;
    }

    @Test
    void shouldSetCorePoolSizeMultiplier() {
      assertThat(gatewayRestProperties.getApiExecutor().getCorePoolSizeMultiplier()).isEqualTo(10);
    }

    @Test
    void shouldSetMaxPoolSizeMultiplier() {
      assertThat(gatewayRestProperties.getApiExecutor().getMaxPoolSizeMultiplier()).isEqualTo(15);
    }

    @Test
    void shouldSetKeepAliveSeconds() {
      assertThat(gatewayRestProperties.getApiExecutor().getKeepAliveSeconds()).isEqualTo(180);
    }

    @Test
    void shouldSetQueueCapacity() {
      assertThat(gatewayRestProperties.getApiExecutor().getQueueCapacity()).isEqualTo(192);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.api.rest.executor.core-pool-size-multiplier=5",
        "camunda.api.rest.executor.max-pool-size-multiplier=10",
        "camunda.api.rest.executor.keep-alive=120s",
        "camunda.api.rest.executor.queue-capacity=128",
        // legacy
        "camunda.rest.apiExecutor.corePoolSizeMultiplier=10",
        "camunda.rest.apiExecutor.maxPoolSizeMultiplier=15",
        "camunda.rest.apiExecutor.keepAliveSeconds=180",
        "camunda.rest.apiExecutor.queueCapacity=192",
      })
  class WithNewAndLegacySet {
    final GatewayRestProperties gatewayRestProperties;

    WithNewAndLegacySet(@Autowired final GatewayRestProperties gatewayRestProperties) {
      this.gatewayRestProperties = gatewayRestProperties;
    }

    @Test
    void shouldSetCorePoolSizeMultiplierFromNew() {
      assertThat(gatewayRestProperties.getApiExecutor().getCorePoolSizeMultiplier()).isEqualTo(5);
    }

    @Test
    void shouldSetMaxPoolSizeMultiplierFromNew() {
      assertThat(gatewayRestProperties.getApiExecutor().getMaxPoolSizeMultiplier()).isEqualTo(10);
    }

    @Test
    void shouldSetKeepAliveSecondsFromNew() {
      assertThat(gatewayRestProperties.getApiExecutor().getKeepAliveSeconds()).isEqualTo(120);
    }

    @Test
    void shouldSetQueueCapacity() {
      assertThat(gatewayRestProperties.getApiExecutor().getQueueCapacity()).isEqualTo(128);
    }
  }
}
