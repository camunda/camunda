/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MANAGEMENT_THREADS;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_PORT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.unit.DataSize;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class ApiGrpcBrokerPropertiesTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.grpc.address=10.0.0.7",
        "camunda.api.grpc.port=27900",
        "camunda.api.grpc.min-keep-alive-interval=40s",
        "camunda.api.grpc.max-message-size=40MB",
        "camunda.api.grpc.management-threads=5",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetAddress() {
      assertThat(brokerCfg.getGateway().getNetwork().getHost()).isEqualTo("10.0.0.7");
    }

    @Test
    void shouldSetPort() {
      assertThat(brokerCfg.getGateway().getNetwork().getPort()).isEqualTo(27900);
    }

    @Test
    void shouldSetMinKeepAliveInterval() {
      assertThat(brokerCfg.getGateway().getNetwork().getMinKeepAliveInterval())
          .isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void shouldSetMaxMessageSize() {
      assertThat(brokerCfg.getGateway().getNetwork().getMaxMessageSize())
          .isEqualTo(DataSize.ofMegabytes(40));
    }

    @Test
    void shouldSetManagementThreads() {
      assertThat(brokerCfg.getGateway().getThreads().getManagementThreads()).isEqualTo(5);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.network.host=198.0.0.1",
        "zeebe.gateway.network.port=38900",
        "zeebe.gateway.network.minKeepAliveInterval=50s",
        "zeebe.gateway.network.maxMessageSize=50MB",
        "zeebe.gateway.threads.managementThreads=10",
      })
  class WithOnlyLegacyGatewayPropertiesSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyGatewayPropertiesSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldNotSetAddressFromLegacyGatewayNetwork() {
      assertThat(brokerCfg.getGateway().getNetwork().getHost()).isNull();
    }

    @Test
    void shouldNotSetPortFromLegacyGatewayNetwork() {
      assertThat(brokerCfg.getGateway().getNetwork().getPort()).isEqualTo(DEFAULT_PORT);
    }

    @Test
    void shouldNotSetMinKeepAliveIntervalFromLegacyGatewayNetwork() {
      assertThat(brokerCfg.getGateway().getNetwork().getMinKeepAliveInterval())
          .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldNotSetMaxMessageSizeFromLegacyGatewayNetwork() {
      assertThat(brokerCfg.getGateway().getNetwork().getMaxMessageSize())
          .isEqualTo(DataSize.ofMegabytes(4));
    }

    @Test
    void shouldNotSetManagementThreadsFromLegacyGatewayThreads() {
      assertThat(brokerCfg.getGateway().getThreads().getManagementThreads())
          .isEqualTo(DEFAULT_MANAGEMENT_THREADS);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.gateway.network.host=192.0.0.1",
        "zeebe.broker.gateway.network.port=28900",
        "zeebe.broker.gateway.network.minKeepAliveInterval=60s",
        "zeebe.broker.gateway.network.maxMessageSize=60MB",
        "zeebe.broker.gateway.threads.managementThreads=6",
      })
  class WithOnlyLegacyBrokerPropertiesSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyBrokerPropertiesSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetAddressFromLegacyBrokerNetwork() {
      assertThat(brokerCfg.getGateway().getNetwork().getHost()).isEqualTo("192.0.0.1");
    }

    @Test
    void shouldSetPortFromLegacyBrokerNetwork() {
      assertThat(brokerCfg.getGateway().getNetwork().getPort()).isEqualTo(28900);
    }

    @Test
    void shouldSetMinKeepAliveIntervalFromLegacyBrokerNetwork() {
      assertThat(brokerCfg.getGateway().getNetwork().getMinKeepAliveInterval())
          .isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void shouldSetMaxMessageSizeFromLegacyBrokerNetwork() {
      assertThat(brokerCfg.getGateway().getNetwork().getMaxMessageSize())
          .isEqualTo(DataSize.ofMegabytes(60));
    }

    @Test
    void shouldSetManagementThreadsFromLegacyBrokerThreads() {
      assertThat(brokerCfg.getGateway().getThreads().getManagementThreads()).isEqualTo(6);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new unified configuration
        "camunda.api.grpc.address=10.0.0.7",
        "camunda.api.grpc.port=27900",
        "camunda.api.grpc.min-keep-alive-interval=40s",
        "camunda.api.grpc.max-message-size=40MB",
        "camunda.api.grpc.management-threads=5",
        // legacy gateway configuration
        "zeebe.gateway.network.host=198.0.0.1",
        "zeebe.gateway.network.port=38900",
        "zeebe.gateway.network.minKeepAliveInterval=50s",
        "zeebe.gateway.network.maxMessageSize=50MB",
        "zeebe.gateway.threads.managementThreads=10",
        // legacy broker configuration
        "zeebe.broker.gateway.network.host=192.0.0.1",
        "zeebe.broker.gateway.network.port=28900",
        "zeebe.broker.gateway.network.minKeepAliveInterval=60s",
        "zeebe.broker.gateway.network.maxMessageSize=60MB",
        "zeebe.broker.gateway.threads.managementThreads=6"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetAddressFromNew() {
      assertThat(brokerCfg.getGateway().getNetwork().getHost()).isEqualTo("10.0.0.7");
    }

    @Test
    void shouldSetPortFromNew() {
      assertThat(brokerCfg.getGateway().getNetwork().getPort()).isEqualTo(27900);
    }

    @Test
    void shouldSetMinKeepAliveIntervalFromNew() {
      assertThat(brokerCfg.getGateway().getNetwork().getMinKeepAliveInterval())
          .isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void shouldSetMaxMessageSizeFromNew() {
      assertThat(brokerCfg.getGateway().getNetwork().getMaxMessageSize())
          .isEqualTo(DataSize.ofMegabytes(40));
    }

    @Test
    void shouldSetManagementThreads() {
      assertThat(brokerCfg.getGateway().getThreads().getManagementThreads()).isEqualTo(5);
    }
  }
}
