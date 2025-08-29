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
import io.camunda.zeebe.broker.system.configuration.SocketBindingCfg.CommandApiCfg;
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
public class ClusterNetworkCommandApiTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.network.command-api.host=hostNew",
        "camunda.cluster.network.command-api.port=10",
        "camunda.cluster.network.command-api.advertised-host=advertisedHostNew",
        "camunda.cluster.network.command-api.advertised-port=30",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetFromNew() {
      assertThat(brokerCfg.getNetwork().getCommandApi())
          .returns("hostNew", CommandApiCfg::getHost)
          .returns(10, CommandApiCfg::getPort)
          .returns("advertisedHostNew", CommandApiCfg::getAdvertisedHost)
          .returns(30, CommandApiCfg::getAdvertisedPort);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.network.commandApi.host=hostLegacy",
        "zeebe.broker.network.commandApi.port=20",
        "zeebe.broker.network.commandApi.advertisedHost=advertisedHostLegacy",
        "zeebe.broker.network.commandApi.advertisedPort=40",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetFromLegacy() {
      assertThat(brokerCfg.getNetwork().getCommandApi())
          .returns("hostLegacy", CommandApiCfg::getHost)
          .returns(20, CommandApiCfg::getPort)
          .returns("advertisedHostLegacy", CommandApiCfg::getAdvertisedHost)
          .returns(40, CommandApiCfg::getAdvertisedPort);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.cluster.network.command-api.host=hostNew",
        "camunda.cluster.network.command-api.port=10",
        "camunda.cluster.network.command-api.advertised-host=advertisedHostNew",
        "camunda.cluster.network.command-api.advertised-port=30",
        // legacy
        "zeebe.broker.network.commandApi.host=hostLegacy",
        "zeebe.broker.network.commandApi.port=20",
        "zeebe.broker.network.commandApi.advertisedHost=advertisedHostLegacy",
        "zeebe.broker.network.commandApi.advertisedPort=40",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetFromNew() {
      assertThat(brokerCfg.getNetwork().getCommandApi())
          .returns("hostNew", CommandApiCfg::getHost)
          .returns(10, CommandApiCfg::getPort)
          .returns("advertisedHostNew", CommandApiCfg::getAdvertisedHost)
          .returns(30, CommandApiCfg::getAdvertisedPort);
    }
  }
}
