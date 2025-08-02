/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import io.camunda.configuration.beans.GatewayBasedProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  GatewayBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
public class ApiLongPollingTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.long-polling.enabled=true",
        "camunda.api.long-polling.timeout=20000",
        "camunda.api.long-polling.probe-timeout=30000",
        "camunda.api.long-polling.min-empty-responses=5"
      })
  class WithOnlyUnifiedConfigSet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyUnifiedConfigSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetEnabled() {
      assertThat(gatewayCfg.getLongPolling().isEnabled()).isTrue();
    }

    @Test
    void shouldSetTimeout() {
      assertThat(gatewayCfg.getLongPolling().getTimeout()).isEqualTo(20000);
    }

    @Test
    void shouldSetProbeTimeout() {
      assertThat(gatewayCfg.getLongPolling().getProbeTimeout()).isEqualTo(30000);
    }

    @Test
    void shouldSetMinEmptyResponses() {
      assertThat(gatewayCfg.getLongPolling().getMinEmptyResponses()).isEqualTo(5);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.longPolling.enabled=false",
        "zeebe.gateway.longPolling.timeout=2",
        "zeebe.gateway.longPolling.probeTimeout=3",
        "zeebe.gateway.longPolling.minEmptyResponses=4"
      })
  class WithOnlyLegacySet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyLegacySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetEnabled() {
      assertThat(gatewayCfg.getLongPolling().isEnabled()).isFalse();
    }

    @Test
    void shouldSetTimeout() {
      assertThat(gatewayCfg.getLongPolling().getTimeout()).isEqualTo(2);
    }

    @Test
    void shouldSetProbeTimeout() {
      assertThat(gatewayCfg.getLongPolling().getProbeTimeout()).isEqualTo(3);
    }

    @Test
    void shouldSetMinEmptyResponses() {
      assertThat(gatewayCfg.getLongPolling().getMinEmptyResponses()).isEqualTo(4);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.api.long-polling.enabled=true",
        "camunda.api.long-polling.timeout=20000",
        "camunda.api.long-polling.probe-timeout=30000",
        "camunda.api.long-polling.min-empty-responses=5",
        // legacy
        "zeebe.gateway.longPolling.enabled=false",
        "zeebe.gateway.longPolling.timeout=2",
        "zeebe.gateway.longPolling.probeTimeout=3",
        "zeebe.gateway.longPolling.minEmptyResponses=4"
      })
  class WithNewAndLegacySet {
    final GatewayBasedProperties gatewayCfg;

    WithNewAndLegacySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetEnabledFromNew() {
      assertThat(gatewayCfg.getLongPolling().isEnabled()).isTrue();
    }

    @Test
    void shouldSetTimeoutFromNew() {
      assertThat(gatewayCfg.getLongPolling().getTimeout()).isEqualTo(20000);
    }

    @Test
    void shouldSetProbeTimeoutFromNew() {
      assertThat(gatewayCfg.getLongPolling().getProbeTimeout()).isEqualTo(30000);
    }

    @Test
    void shouldSetMinEmptyResponseFromNew() {
      assertThat(gatewayCfg.getLongPolling().getMinEmptyResponses()).isEqualTo(5);
    }
  }
}
