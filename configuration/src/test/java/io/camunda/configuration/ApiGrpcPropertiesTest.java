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
public class ApiGrpcPropertiesTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.grpc.address=10.0.0.7",
        "camunda.api.grpc.port=27900",
        "camunda.api.grpc.management-threads=5",
      })
  class WithOnlyUnifiedConfigSet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyUnifiedConfigSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetAddress() {
      assertThat(gatewayCfg.getNetwork().getHost()).isEqualTo("10.0.0.7");
    }

    @Test
    void shouldSetPort() {
      assertThat(gatewayCfg.getNetwork().getPort()).isEqualTo(27900);
    }

    @Test
    void shouldSetManagementThreads() {
      assertThat(gatewayCfg.getThreads().getManagementThreads()).isEqualTo(5);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.network.host=192.0.0.1",
        "zeebe.gateway.network.port=28900",
        "zeebe.gateway.threads.managementThreads=6",
      })
  class WithOnlyLegacySet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyLegacySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetAddress() {
      assertThat(gatewayCfg.getNetwork().getHost()).isEqualTo("192.0.0.1");
    }

    @Test
    void shouldSetPort() {
      assertThat(gatewayCfg.getNetwork().getPort()).isEqualTo(28900);
    }

    @Test
    void shouldSetManagementThreads() {
      assertThat(gatewayCfg.getThreads().getManagementThreads()).isEqualTo(6);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.api.grpc.address=10.0.0.7",
        "camunda.api.grpc.port=27900",
        "camunda.api.grpc.management-threads=5",
        // legacy
        "zeebe.gateway.network.host=192.0.0.1",
        "zeebe.gateway.network.port=28900",
        "zeebe.gateway.threads.managementThreads=6",
      })
  class WithNewAndLegacySet {
    final GatewayBasedProperties gatewayCfg;

    WithNewAndLegacySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetAddressFromNew() {
      assertThat(gatewayCfg.getNetwork().getHost()).isEqualTo("10.0.0.7");
    }

    @Test
    void shouldSetPortFromNew() {
      assertThat(gatewayCfg.getNetwork().getPort()).isEqualTo(27900);
    }

    @Test
    void shouldSetManagementThreads() {
      assertThat(gatewayCfg.getThreads().getManagementThreads()).isEqualTo(5);
    }
  }
}
