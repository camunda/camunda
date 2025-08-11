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
public class ClusterRaftTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.raft.heartbeat-interval=1s",
        "camunda.cluster.raft.election-timeout=10s",
        "camunda.cluster.raft.priority-election-enabled=false",
        "camunda.cluster.raft.flush-enabled=false",
        "camunda.cluster.raft.flush-delay=5s"
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetHeartbeatInterval() {
      assertThat(brokerCfg.getCluster().getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void shouldSetElectionTimeout() {
      assertThat(brokerCfg.getCluster().getElectionTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldSetPriorityElectionEnabled() {
      assertThat(brokerCfg.getCluster().getRaft().isEnablePriorityElection()).isFalse();
    }

    @Test
    void shouldSetFlushEnabled() {
      assertThat(brokerCfg.getCluster().getRaft().getFlush().enabled()).isFalse();
    }

    @Test
    void shouldSetFlushDelay() {
      assertThat(brokerCfg.getCluster().getRaft().getFlush().delayTime())
          .isEqualTo(Duration.ofSeconds(5));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.cluster.heartbeatInterval=2s",
        "zeebe.broker.cluster.electionTimeout=20s",
        "zeebe.broker.cluster.raft.enablePriorityElection=false",
        "zeebe.broker.cluster.raft.flush.enabled=false",
        "zeebe.broker.cluster.raft.flush.delay=10s"
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetHeartbeatIntervalFromLegacy() {
      assertThat(brokerCfg.getCluster().getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void shouldSetElectionTimeoutFromLegacy() {
      assertThat(brokerCfg.getCluster().getElectionTimeout()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void shouldSetPriorityElectionEnabledFromLegacy() {
      assertThat(brokerCfg.getCluster().getRaft().isEnablePriorityElection()).isFalse();
    }

    @Test
    void shouldSetFlushEnabledFromLegacy() {
      assertThat(brokerCfg.getCluster().getRaft().getFlush().enabled()).isFalse();
    }

    @Test
    void shouldSetFlushDelayFromLegacy() {
      assertThat(brokerCfg.getCluster().getRaft().getFlush().delayTime())
          .isEqualTo(Duration.ofSeconds(10));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.cluster.raft.heartbeat-interval=3s",
        "camunda.cluster.raft.election-timeout=30s",
        "camunda.cluster.raft.priority-election-enabled=true",
        "camunda.cluster.raft.flush-enabled=true",
        "camunda.cluster.raft.flush-delay=15s",
        // legacy
        "zeebe.broker.cluster.heartbeatInterval=99s",
        "zeebe.broker.cluster.electionTimeout=99s",
        "zeebe.broker.cluster.raft.enablePriorityElection=false",
        "zeebe.broker.cluster.raft.flush.enabled=false",
        "zeebe.broker.cluster.raft.flush.delay=99s"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetHeartbeatIntervalFromNew() {
      assertThat(brokerCfg.getCluster().getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void shouldSetElectionTimeoutFromNew() {
      assertThat(brokerCfg.getCluster().getElectionTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldSetPriorityElectionEnabledFromNew() {
      assertThat(brokerCfg.getCluster().getRaft().isEnablePriorityElection()).isTrue();
    }

    @Test
    void shouldSetFlushEnabledFromNew() {
      assertThat(brokerCfg.getCluster().getRaft().getFlush().enabled()).isTrue();
    }

    @Test
    void shouldSetFlushDelayFromNew() {
      assertThat(brokerCfg.getCluster().getRaft().getFlush().delayTime())
          .isEqualTo(Duration.ofSeconds(15));
    }
  }
}
