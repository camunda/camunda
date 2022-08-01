/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ExperimentalCfgTest {

  final Map<String, String> environment = new HashMap<>();

  @Test
  void shouldSetRaftRequestTimeoutFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var raft = cfg.getExperimental().getRaft();

    // then
    assertThat(raft.getRequestTimeout()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  void shouldSetRaftRequestTimeoutFromEnv() {
    // given
    environment.put("zeebe.broker.experimental.raft.requestTimeout", "15s");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var raft = cfg.getExperimental().getRaft();

    // then
    assertThat(raft.getRequestTimeout()).isEqualTo(Duration.ofSeconds(15));
  }

  @Test
  void shouldSetRaftMaxQuorumResponseTimeoutFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var raft = cfg.getExperimental().getRaft();

    // then
    assertThat(raft.getMaxQuorumResponseTimeout()).isEqualTo(Duration.ofSeconds(8));
  }

  @Test
  void shouldSetRaftMaxQuorumResponseTimeoutFromEnv() {
    // given
    environment.put("zeebe.broker.experimental.raft.maxQuorumResponseTimeout", "15s");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var raft = cfg.getExperimental().getRaft();

    // then
    assertThat(raft.getMaxQuorumResponseTimeout()).isEqualTo(Duration.ofSeconds(15));
  }

  @Test
  void shouldSetRaftMinStepDownFailureCountFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var raft = cfg.getExperimental().getRaft();

    // then
    assertThat(raft.getMinStepDownFailureCount()).isEqualTo(5);
  }

  @Test
  void shouldSetRaftMinStepDownFailureCountFromEnv() {
    // given
    environment.put("zeebe.broker.experimental.raft.minStepDownFailureCount", "10");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var raft = cfg.getExperimental().getRaft();

    // then
    assertThat(raft.getMinStepDownFailureCount()).isEqualTo(10);
  }

  @Test
  void shouldSetPreferSnapshotReplicationThresholdFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var raft = cfg.getExperimental().getRaft();

    // then
    assertThat(raft.getPreferSnapshotReplicationThreshold()).isEqualTo(500);
  }

  @Test
  void shouldSetPreferSnapshotReplicationThresholdFromEnv() {
    // given
    environment.put("zeebe.broker.experimental.raft.preferSnapshotReplicationThreshold", "10");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var raft = cfg.getExperimental().getRaft();

    // then
    assertThat(raft.getPreferSnapshotReplicationThreshold()).isEqualTo(10);
  }

  @Test
  void shouldSetEnablePreconditionsFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var consistencyChecks = cfg.getExperimental().getConsistencyChecks();

    // then
    assertThat(consistencyChecks.isEnablePreconditions()).isTrue();
  }

  @Test
  void shouldSetEnablePreconditionsFromEnv() {
    // given
    environment.put("zeebe.broker.experimental.consistencyChecks.enablePreconditions", "false");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var consistencyChecks = cfg.getExperimental().getConsistencyChecks();

    // then
    assertThat(consistencyChecks.isEnablePreconditions()).isFalse();
  }

  @Test
  void shouldSetEnableForeignKeyChecksFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var consistencyChecks = cfg.getExperimental().getConsistencyChecks();

    // then
    assertThat(consistencyChecks.isEnableForeignKeyChecks()).isTrue();
  }

  @Test
  void shouldSetEnableForeignKeyChecksFromEnv() {
    // given
    environment.put("zeebe.broker.experimental.consistencyChecks.enableForeignKeyChecks", "false");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var consistencyChecks = cfg.getExperimental().getConsistencyChecks();

    // then
    assertThat(consistencyChecks.isEnableForeignKeyChecks()).isFalse();
  }

  @Test
  void shouldSetPreallocateSegmentFilesFromEnv() {
    // given
    environment.put("zeebe.broker.experimental.raft.preallocateSegmentFiles", "false");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var raftCfg = cfg.getExperimental().getRaft();

    // then
    assertThat(raftCfg.isPreallocateSegmentFiles()).isFalse();
  }

  @Test
  void shouldSetPreallocateSegmentFilesFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var raftCfg = cfg.getExperimental().getRaft();

    // then
    assertThat(raftCfg.isPreallocateSegmentFiles()).isTrue();
  }
}
