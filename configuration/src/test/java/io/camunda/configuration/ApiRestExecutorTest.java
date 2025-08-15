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
        "camunda.api.rest.executor.core-pool-size=5",
        "camunda.api.rest.executor.thread-count-multiplier=10",
        "camunda.api.rest.executor.keep-alive=120s",
      })
  class WithOnlyUnifiedConfigSet {
    final GatewayRestProperties gatewayRestProperties;

    WithOnlyUnifiedConfigSet(@Autowired final GatewayRestProperties gatewayRestProperties) {
      this.gatewayRestProperties = gatewayRestProperties;
    }

    @Test
    void shouldSetCorePoolSize() {
      assertThat(gatewayRestProperties.getApiExecutor().getCorePoolSize()).isEqualTo(5);
    }

    @Test
    void shouldSetThreadCountMultiplier() {
      assertThat(gatewayRestProperties.getApiExecutor().getThreadCountMultiplier()).isEqualTo(10);
    }

    @Test
    void shouldSetKeepAliveSeconds() {
      assertThat(gatewayRestProperties.getApiExecutor().getKeepAliveSeconds()).isEqualTo(120);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.rest.apiExecutor.corePoolSize=10",
        "camunda.rest.apiExecutor.threadCountMultiplier=15",
        "camunda.rest.apiExecutor.keepAliveSeconds=180",
      })
  class WithOnlyLegacySet {
    final GatewayRestProperties gatewayRestProperties;

    WithOnlyLegacySet(@Autowired final GatewayRestProperties gatewayRestProperties) {
      this.gatewayRestProperties = gatewayRestProperties;
    }

    @Test
    void shouldSetCorePoolSize() {
      assertThat(gatewayRestProperties.getApiExecutor().getCorePoolSize()).isEqualTo(10);
    }

    @Test
    void shouldSetThreadCountMultiplier() {
      assertThat(gatewayRestProperties.getApiExecutor().getThreadCountMultiplier()).isEqualTo(15);
    }

    @Test
    void shouldSetKeepAliveSeconds() {
      assertThat(gatewayRestProperties.getApiExecutor().getKeepAliveSeconds()).isEqualTo(180);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.api.rest.executor.core-pool-size=5",
        "camunda.api.rest.executor.thread-count-multiplier=10",
        "camunda.api.rest.executor.keep-alive=120s",
        // legacy
        "camunda.rest.apiExecutor.corePoolSize=10",
        "camunda.rest.apiExecutor.threadCountMultiplier=15",
        "camunda.rest.apiExecutor.keepAliveSeconds=180",
      })
  class WithNewAndLegacySet {
    final GatewayRestProperties gatewayRestProperties;

    WithNewAndLegacySet(@Autowired final GatewayRestProperties gatewayRestProperties) {
      this.gatewayRestProperties = gatewayRestProperties;
    }

    @Test
    void shouldSetCorePoolSizeFromNew() {
      assertThat(gatewayRestProperties.getApiExecutor().getCorePoolSize()).isEqualTo(5);
    }

    @Test
    void shouldSetThreadCountMultiplierFromNew() {
      assertThat(gatewayRestProperties.getApiExecutor().getThreadCountMultiplier()).isEqualTo(10);
    }

    @Test
    void shouldSetKeepAliveSecondsFromNew() {
      assertThat(gatewayRestProperties.getApiExecutor().getKeepAliveSeconds()).isEqualTo(120);
    }
  }
}
