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
import java.time.Duration;
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
public class ClusterMetadataTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.metadata.sync-delay=30s",
        "camunda.cluster.metadata.sync-request-timeout=15s",
        "camunda.cluster.metadata.gossip-fanout=10",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetSyncDelay() {
      assertThat(brokerCfg.getCluster().getConfigManager().gossip().syncDelay())
          .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldSetSyncRequestTimeout() {
      assertThat(brokerCfg.getCluster().getConfigManager().gossip().syncRequestTimeout())
          .isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void shouldSetGossipFanout() {
      assertThat(brokerCfg.getCluster().getConfigManager().gossip().gossipFanout()).isEqualTo(10);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.cluster.configManager.gossip.syncDelay=50s",
        "zeebe.broker.cluster.configManager.gossip.syncRequestTimeout=30s",
        "zeebe.broker.cluster.configManager.gossip.gossipFanout=200"
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetSyncDelay() {
      assertThat(brokerCfg.getCluster().getConfigManager().gossip().syncDelay())
          .isEqualTo(Duration.ofSeconds(50));
    }

    @Test
    void shouldSetSyncRequestTimeout() {
      assertThat(brokerCfg.getCluster().getConfigManager().gossip().syncRequestTimeout())
          .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldSetGossipFanout() {
      assertThat(brokerCfg.getCluster().getConfigManager().gossip().gossipFanout()).isEqualTo(200);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.cluster.metadata.sync-delay=30s",
        "camunda.cluster.metadata.sync-request-timeout=15s",
        "camunda.cluster.metadata.gossip-fanout=10",
        // legacy
        "zeebe.broker.cluster.configManager.gossip.syncDelay=5m",
        "zeebe.broker.cluster.configManager.gossip.syncRequestTimeout=1m",
        "zeebe.broker.cluster.configManager.gossip.gossipFanout=300"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetSyncDelayFromNew() {
      assertThat(brokerCfg.getCluster().getConfigManager().gossip().syncDelay())
          .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldSetSyncRequestTimeoutFromNew() {
      assertThat(brokerCfg.getCluster().getConfigManager().gossip().syncRequestTimeout())
          .isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void shouldSetGossipFanoutFromNew() {
      assertThat(brokerCfg.getCluster().getConfigManager().gossip().gossipFanout()).isEqualTo(10);
    }
  }
}
