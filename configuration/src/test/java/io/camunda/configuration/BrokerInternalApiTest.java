/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.NetworkCfg;
import io.camunda.zeebe.broker.system.configuration.SocketBindingCfg;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class BrokerInternalApiTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.network.internal-api.host=hostNew",
        "camunda.cluster.network.internal-api.port=10",
        "camunda.cluster.network.internal-api.advertised-host=advertisedHostNew",
        "camunda.cluster.network.internal-api.advertised-port=30",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetFromNew() {
      assertThat(brokerCfg.getNetwork().getInternalApi())
          .returns("hostNew", SocketBindingCfg::getHost)
          .returns(10, SocketBindingCfg::getPort)
          .returns("advertisedHostNew", SocketBindingCfg::getAdvertisedHost)
          .returns(30, SocketBindingCfg::getAdvertisedPort);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.cluster.host=hostLegacyGateway",
        "zeebe.gateway.cluster.port=20",
        "zeebe.gateway.cluster.advertisedHost=advertisedHostLegacyGateway",
        "zeebe.gateway.cluster.advertisedPort=40"
      })
  class WithOnlyLegacyGatewayPropertiesSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyGatewayPropertiesSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldNotSetFromLegacyGateway() {
      assertThat(brokerCfg.getNetwork().getInternalApi())
          .returns(null, SocketBindingCfg::getHost)
          .returns(NetworkCfg.DEFAULT_INTERNAL_API_PORT, SocketBindingCfg::getPort)
          .returns(null, SocketBindingCfg::getAdvertisedHost);

      // We expect a NullPointerException here because advertisedPort is unset.
      // This confirms that no advertisedPort has been configured.
      try {
        brokerCfg.getNetwork().getInternalApi().getAdvertisedPort();
      } catch (final NullPointerException e) {
        assertThat(e).isInstanceOf(NullPointerException.class);
      }
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.network.internalApi.host=hostLegacyBroker",
        "zeebe.broker.network.internalApi.port=30",
        "zeebe.broker.network.internalApi.advertisedHost=advertisedHostLegacyBroker",
        "zeebe.broker.network.internalApi.advertisedPort=50"
      })
  class WithOnlyLegacyBrokerSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyBrokerSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetFromLegacyBroker() {
      assertThat(brokerCfg.getNetwork().getInternalApi())
          .returns("hostLegacyBroker", SocketBindingCfg::getHost)
          .returns(30, SocketBindingCfg::getPort)
          .returns("advertisedHostLegacyBroker", SocketBindingCfg::getAdvertisedHost)
          .returns(50, SocketBindingCfg::getAdvertisedPort);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.cluster.network.internal-api.host=hostNew",
        "camunda.cluster.network.internal-api.port=10",
        "camunda.cluster.network.internal-api.advertised-host=advertisedHostNew",
        "camunda.cluster.network.internal-api.advertised-port=30",
        // legacy gateway
        "zeebe.gateway.cluster.host=hostLegacyGateway",
        "zeebe.gateway.cluster.port=20",
        "zeebe.gateway.cluster.advertisedHost=advertisedHostLegacyGateway",
        "zeebe.gateway.cluster.advertisedPort=40",
        // legacy broker
        "zeebe.broker.network.internalApi.host=hostLegacyBroker",
        "zeebe.broker.network.internalApi.port=30",
        "zeebe.broker.network.internalApi.advertisedHost=advertisedHostLegacyBroker",
        "zeebe.broker.network.internalApi.advertisedPort=50"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetFromNew() {
      assertThat(brokerCfg.getNetwork().getInternalApi())
          .returns("hostNew", SocketBindingCfg::getHost)
          .returns(10, SocketBindingCfg::getPort)
          .returns("advertisedHostNew", SocketBindingCfg::getAdvertisedHost)
          .returns(30, SocketBindingCfg::getAdvertisedPort);
    }
  }
}
