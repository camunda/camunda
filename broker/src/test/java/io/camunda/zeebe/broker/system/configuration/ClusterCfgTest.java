/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class ClusterCfgTest {

  @Test
  public void shouldSanitizeContactPoints() {
    // given
    final ClusterCfg sutClusterConfig = new ClusterCfg();
    final List<String> input = Arrays.asList("", "foo ", null, "   ", "bar");
    final List<String> expected = Arrays.asList("foo", "bar");

    // when
    sutClusterConfig.setInitialContactPoints(input);

    // then
    final List<String> actual = sutClusterConfig.getInitialContactPoints();

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void shouldGeneratePartitionIds() {
    // given
    final ClusterCfg clusterCfg = new ClusterCfg();
    clusterCfg.setPartitionsCount(8);

    // when
    clusterCfg.init(new BrokerCfg(), "");

    // then
    assertThat(clusterCfg.getPartitionIds()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
  }

  @Test
  void shouldThrowExceptionIfNodeIdIsNegative() {
    // given
    final var environment = Collections.singletonMap("zeebe.broker.cluster.nodeId", "-1");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Node id -1 needs to be non negative and smaller then cluster size 1.");
  }

  @Test
  void shouldThrowExceptionIfNodeIdIsLargerThenClusterSize() {
    // given
    final var environment = Collections.singletonMap("zeebe.broker.cluster.nodeId", "2");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Node id 2 needs to be non negative and smaller then cluster size 1.");
  }

  @Test
  void shouldThrowExceptionIfReplicationFactorIsNegative() {
    // given
    final var environment =
        Collections.singletonMap("zeebe.broker.cluster.replicationFactor", "-1");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Replication factor -1 needs to be larger then zero and not larger then cluster size 1.");
  }

  @Test
  void shouldThrowExceptionIfReplicationFactorIsLargerThenClusterSize() {
    // given
    final var environment = Collections.singletonMap("zeebe.broker.cluster.replicationFactor", "2");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Replication factor 2 needs to be larger then zero and not larger then cluster size 1.");
  }

  @Test
  void shouldThrowExceptionIfPartitionsCountIsNegative() {
    // given
    final var environment = Collections.singletonMap("zeebe.broker.cluster.partitionsCount", "-1");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Partition count must not be smaller then 1.");
  }

  @Test
  void shouldThrowExceptionIfElectionTimeoutIsSmallerThanOneMs() {
    // given
    final var environment = Collections.singletonMap("zeebe.broker.cluster.electionTimeout", "0ms");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("electionTimeout PT0S must be at least 1ms");
  }

  @Test
  void shouldThrowExceptionIfElectionTimeoutIsSmallerThanHeartbeatInterval() {
    // given
    final var environment = Collections.singletonMap("zeebe.broker.cluster.electionTimeout", "1ms");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("electionTimeout PT0.001S must be greater than heartbeatInterval PT0.25S");
  }

  @Test
  void shouldThrowExceptionIfHeartbeatIntervalIsSmallerThanOneMs() {
    // given
    final var environment =
        Collections.singletonMap("zeebe.broker.cluster.heartbeatInterval", "0ms");

    // when - then
    assertThatCode(() -> TestConfigReader.readConfig("default", environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("heartbeatInterval PT0S must be at least 1ms");
  }
}
