/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_HOST;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MANAGEMENT_THREADS;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_PORT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import io.camunda.configuration.beans.GatewayBasedProperties;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.unit.DataSize;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  GatewayBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
public class ApiGrpcGatewayPropertiesTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.grpc.address=10.0.0.7",
        "camunda.api.grpc.port=27900",
        "camunda.api.grpc.min-keep-alive-interval=40s",
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
    void shouldSetMinKeepAliveInterval() {
      assertThat(gatewayCfg.getNetwork().getMinKeepAliveInterval())
          .isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void shouldSetManagementThreads() {
      assertThat(gatewayCfg.getThreads().getManagementThreads()).isEqualTo(5);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.gateway.network.host=198.0.0.1",
        "zeebe.broker.gateway.network.port=38900",
        "zeebe.broker.gateway.network.minKeepAliveInterval=50s",
        "zeebe.broker.gateway.threads.managementThreads=10",
      })
  class WithOnlyLegacyBrokerPropertiesSet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyLegacyBrokerPropertiesSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldNotSetAddressFromLegacyBrokerNetwork() {
      assertThat(gatewayCfg.getNetwork().getHost()).isEqualTo(DEFAULT_HOST);
    }

    @Test
    void shouldNotSetPortFromLegacyBrokerNetwork() {
      assertThat(gatewayCfg.getNetwork().getPort()).isEqualTo(DEFAULT_PORT);
    }

    @Test
    void shouldNotSetMinKeepAliveIntervalFromLegacyBrokerNetwork() {
      assertThat(gatewayCfg.getNetwork().getMinKeepAliveInterval())
          .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldNotSetMaxMessageSizeFromLegacyBrokerNetwork() {
      assertThat(gatewayCfg.getNetwork().getMaxMessageSize()).isEqualTo(DataSize.ofMegabytes(4));
    }

    @Test
    void shouldNotSetManagementThreadsFromLegacyBrokerThreads() {
      assertThat(gatewayCfg.getThreads().getManagementThreads())
          .isEqualTo(DEFAULT_MANAGEMENT_THREADS);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.network.host=192.0.0.1",
        "zeebe.gateway.network.port=28900",
        "zeebe.gateway.network.minKeepAliveInterval=60s",
        "zeebe.gateway.threads.managementThreads=6",
      })
  class WithOnlyLegacyGatewayPropertiesSet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyLegacyGatewayPropertiesSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetAddressFromLegacyGatewayNetwork() {
      assertThat(gatewayCfg.getNetwork().getHost()).isEqualTo("192.0.0.1");
    }

    @Test
    void shouldSetPortFromLegacyGatewayNetwork() {
      assertThat(gatewayCfg.getNetwork().getPort()).isEqualTo(28900);
    }

    @Test
    void shouldSetMinKeepAliveIntervalFromLegacyGatewayNetwork() {
      assertThat(gatewayCfg.getNetwork().getMinKeepAliveInterval())
          .isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void shouldSetManagementThreadsFromLegacyGatewayThreads() {
      assertThat(gatewayCfg.getThreads().getManagementThreads()).isEqualTo(6);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new unified configuration
        "camunda.api.grpc.address=10.0.0.7",
        "camunda.api.grpc.port=27900",
        "camunda.api.grpc.min-keep-alive-interval=40s",
        "camunda.api.grpc.management-threads=5",
        // legacy broker configuration
        "zeebe.broker.gateway.network.host=198.0.0.1",
        "zeebe.broker.gateway.network.port=38900",
        "zeebe.broker.gateway.network.minKeepAliveInterval=60s",
        "zeebe.broker.gateway.network.maxMessageSize=50MB",
        "zeebe.broker.gateway.threads.managementThreads=10",
        // legacy gateway configuration
        "zeebe.gateway.network.host=192.0.0.1",
        "zeebe.gateway.network.port=28900",
        "zeebe.gateway.network.minKeepAliveInterval=50s",
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
    void shouldSetMinKeepAliveIntervalFromNew() {
      assertThat(gatewayCfg.getNetwork().getMinKeepAliveInterval())
          .isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void shouldSetManagementThreads() {
      assertThat(gatewayCfg.getThreads().getManagementThreads()).isEqualTo(5);
    }
  }
}
