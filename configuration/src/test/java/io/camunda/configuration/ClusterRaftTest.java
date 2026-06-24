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
import io.camunda.zeebe.broker.system.configuration.ExperimentalCfg;
import io.camunda.zeebe.broker.system.configuration.ExperimentalRaftCfg;
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
public class ClusterRaftTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.raft.heartbeat-interval=1s",
        "camunda.cluster.raft.election-timeout=10s",
        "camunda.cluster.raft.priority-election-enabled=false",
        "camunda.cluster.raft.flush-enabled=false",
        "camunda.cluster.raft.flush-delay=5s",
        "camunda.cluster.raft.max-appends-per-follower=7",
        "camunda.cluster.raft.max-append-batch-size=64",
        "camunda.cluster.raft.request-timeout=5s",
        "camunda.cluster.raft.snapshot-request-timeout=5s",
        "camunda.cluster.raft.snapshot-chunk-size=2GB",
        "camunda.cluster.raft.configuration-change-timeout=20s",
        "camunda.cluster.raft.max-quorum-response-timeout=10s",
        "camunda.cluster.raft.min-step-down-failure-count=5",
        "camunda.cluster.raft.prefer-snapshot-replication-threshold=110",
        "camunda.cluster.raft.preallocate-segment-files=false"
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

    @Test
    void shouldSetExperimental() {
      assertThat(brokerCfg.getExperimental())
          .returns(7, ExperimentalCfg::getMaxAppendsPerFollower)
          .returns(DataSize.ofBytes(64), ExperimentalCfg::getMaxAppendBatchSize);
    }

    @Test
    void shouldSetExperimentalRaft() {
      assertThat(brokerCfg.getExperimental().getRaft())
          .returns(Duration.ofSeconds(5), ExperimentalRaftCfg::getRequestTimeout)
          .returns(Duration.ofSeconds(5), ExperimentalRaftCfg::getSnapshotRequestTimeout)
          .returns(DataSize.ofGigabytes(2), ExperimentalRaftCfg::getSnapshotChunkSize)
          .returns(Duration.ofSeconds(20), ExperimentalRaftCfg::getConfigurationChangeTimeout)
          .returns(Duration.ofSeconds(10), ExperimentalRaftCfg::getMaxQuorumResponseTimeout)
          .returns(5, ExperimentalRaftCfg::getMinStepDownFailureCount)
          .returns(110, ExperimentalRaftCfg::getPreferSnapshotReplicationThreshold)
          .returns(false, ExperimentalRaftCfg::isPreallocateSegmentFiles);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.cluster.heartbeatInterval=2s",
        "zeebe.broker.cluster.electionTimeout=20s",
        "zeebe.broker.cluster.raft.enablePriorityElection=false",
        "zeebe.broker.cluster.raft.flush.enabled=false",
        "zeebe.broker.cluster.raft.flush.delay=10s",
        "zeebe.broker.experimental.maxAppendsPerFollower=8",
        "zeebe.broker.experimental.maxAppendBatchSize=96",
        "zeebe.broker.experimental.raft.requestTimeout=10s",
        "zeebe.broker.experimental.raft.snapshotRequestTimeout=10s",
        "zeebe.broker.experimental.raft.snapshotChunkSize=3GB",
        "zeebe.broker.experimental.raft.configurationChangeTimeout=25s",
        "zeebe.broker.experimental.raft.maxQuorumResponseTimeout=20s",
        "zeebe.broker.experimental.raft.minStepDownFailureCount=10",
        "zeebe.broker.experimental.raft.preferSnapshotReplicationThreshold=120",
        "zeebe.broker.experimental.raft.preallocateSegmentFiles=false"
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

    @Test
    void shouldSetExperimentalFromLegacy() {
      assertThat(brokerCfg.getExperimental())
          .returns(8, ExperimentalCfg::getMaxAppendsPerFollower)
          .returns(DataSize.ofBytes(96), ExperimentalCfg::getMaxAppendBatchSize);
    }

    @Test
    void shouldSetExperimentalRaft() {
      assertThat(brokerCfg.getExperimental().getRaft())
          .returns(Duration.ofSeconds(10), ExperimentalRaftCfg::getRequestTimeout)
          .returns(Duration.ofSeconds(10), ExperimentalRaftCfg::getSnapshotRequestTimeout)
          .returns(DataSize.ofGigabytes(3), ExperimentalRaftCfg::getSnapshotChunkSize)
          .returns(Duration.ofSeconds(25), ExperimentalRaftCfg::getConfigurationChangeTimeout)
          .returns(Duration.ofSeconds(20), ExperimentalRaftCfg::getMaxQuorumResponseTimeout)
          .returns(10, ExperimentalRaftCfg::getMinStepDownFailureCount)
          .returns(120, ExperimentalRaftCfg::getPreferSnapshotReplicationThreshold)
          .returns(false, ExperimentalRaftCfg::isPreallocateSegmentFiles);
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
        "camunda.cluster.raft.max-appends-per-follower=7",
        "camunda.cluster.raft.max-append-batch-size=64",
        "camunda.cluster.raft.request-timeout=5s",
        "camunda.cluster.raft.snapshot-request-timeout=5s",
        "camunda.cluster.raft.snapshot-chunk-size=2GB",
        "camunda.cluster.raft.configuration-change-timeout=20s",
        "camunda.cluster.raft.max-quorum-response-timeout=10s",
        "camunda.cluster.raft.min-step-down-failure-count=5",
        "camunda.cluster.raft.prefer-snapshot-replication-threshold=110",
        "camunda.cluster.raft.preallocate-segment-files=false",
        // legacy
        "zeebe.broker.cluster.heartbeatInterval=99s",
        "zeebe.broker.cluster.electionTimeout=99s",
        "zeebe.broker.cluster.raft.enablePriorityElection=false",
        "zeebe.broker.cluster.raft.flush.enabled=false",
        "zeebe.broker.cluster.raft.flush.delay=99s",
        "zeebe.broker.experimental.maxAppendsPerFollower=8",
        "zeebe.broker.experimental.maxAppendBatchSize=96",
        "zeebe.broker.experimental.raft.requestTimeout=10s",
        "zeebe.broker.experimental.raft.snapshotRequestTimeout=10s",
        "zeebe.broker.experimental.raft.snapshotChunkSize=3GB",
        "zeebe.broker.experimental.raft.configurationChangeTimeout=25s",
        "zeebe.broker.experimental.raft.maxQuorumResponseTimeout=20s",
        "zeebe.broker.experimental.raft.minStepDownFailureCount=10",
        "zeebe.broker.experimental.raft.preferSnapshotReplicationThreshold=120",
        "zeebe.broker.experimental.raft.preallocateSegmentFiles=true"
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

    @Test
    void shouldSetExperimentalFromNew() {
      assertThat(brokerCfg.getExperimental())
          .returns(7, ExperimentalCfg::getMaxAppendsPerFollower)
          .returns(DataSize.ofBytes(64), ExperimentalCfg::getMaxAppendBatchSize);
    }

    @Test
    void shouldSetExperimentalRaft() {
      assertThat(brokerCfg.getExperimental().getRaft())
          .returns(Duration.ofSeconds(5), ExperimentalRaftCfg::getRequestTimeout)
          .returns(Duration.ofSeconds(5), ExperimentalRaftCfg::getSnapshotRequestTimeout)
          .returns(DataSize.ofGigabytes(2), ExperimentalRaftCfg::getSnapshotChunkSize)
          .returns(Duration.ofSeconds(20), ExperimentalRaftCfg::getConfigurationChangeTimeout)
          .returns(Duration.ofSeconds(10), ExperimentalRaftCfg::getMaxQuorumResponseTimeout)
          .returns(5, ExperimentalRaftCfg::getMinStepDownFailureCount)
          .returns(110, ExperimentalRaftCfg::getPreferSnapshotReplicationThreshold)
          .returns(false, ExperimentalRaftCfg::isPreallocateSegmentFiles);
    }
  }
}
