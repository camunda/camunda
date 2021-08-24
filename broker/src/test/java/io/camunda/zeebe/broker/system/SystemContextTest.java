/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.util.sched.clock.ControlledActorClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

final class SystemContextTest {

  @Test
  void shouldThrowExceptionIfNodeIdIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setNodeId(-1);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Node id -1 needs to be non negative and smaller then cluster size 1.");
  }

  @Test
  void shouldThrowExceptionIfNodeIdIsLargerThenClusterSize() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setNodeId(2);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Node id 2 needs to be non negative and smaller then cluster size 1.");
  }

  @Test
  void shouldThrowExceptionIfReplicationFactorIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setReplicationFactor(-1);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Replication factor -1 needs to be larger then zero and not larger then cluster size 1.");
  }

  @Test
  void shouldThrowExceptionIfReplicationFactorIsLargerThenClusterSize() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setReplicationFactor(2);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Replication factor 2 needs to be larger then zero and not larger then cluster size 1.");
  }

  @Test
  void shouldThrowExceptionIfPartitionsCountIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setPartitionsCount(-1);

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Partition count must not be smaller then 1.");
  }

  @Test
  void shouldThrowExceptionIfSnapshotPeriodIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofMinutes(-1));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Snapshot period PT-1M needs to be larger then or equals to one minute.");
  }

  @Test
  void shouldThrowExceptionIfSnapshotPeriodIsTooSmall() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofSeconds(1));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Snapshot period PT1S needs to be larger then or equals to one minute.");
  }

  @Test
  void shouldThrowExceptionIfBatchSizeIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().setMaxAppendBatchSize(DataSize.of(-1, DataUnit.BYTES));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to have an append batch size maximum which is non negative and smaller then '2147483647', but was '-1B'.");
  }

  @Test
  void shouldThrowExceptionIfBatchSizeIsTooLarge() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().setMaxAppendBatchSize(DataSize.of(3, DataUnit.GIGABYTES));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to have an append batch size maximum which is non negative and smaller then '2147483647', but was '3221225472B'.");
  }

  @Test
  void shouldNotThrowExceptionIfSnapshotPeriodIsEqualToOneMinute() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofMinutes(1));

    // when
    final var systemContext = initSystemContext(brokerCfg);

    // then
    assertThat(systemContext.getBrokerConfiguration().getData().getSnapshotPeriod())
        .isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void shouldThrowExceptionIfHeartbeatIntervalIsSmallerThanOneMs() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setHeartbeatInterval(Duration.ofMillis(0));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("heartbeatInterval PT0S must be at least 1ms");
  }

  @Test
  void shouldThrowExceptionIfElectionTimeoutIsSmallerThanOneMs() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setElectionTimeout(Duration.ofMillis(0));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("electionTimeout PT0S must be at least 1ms");
  }

  @Test
  void shouldThrowExceptionIfElectionTimeoutIsSmallerThanHeartbeatInterval() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setElectionTimeout(Duration.ofSeconds(1));
    brokerCfg.getCluster().setHeartbeatInterval(Duration.ofSeconds(2));

    // when - then
    assertThatCode(() -> initSystemContext(brokerCfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("electionTimeout PT1S must be greater than heartbeatInterval PT2S");
  }

  private SystemContext initSystemContext(final BrokerCfg brokerCfg) {
    return new SystemContext(brokerCfg, "test", new ControlledActorClock());
  }
}
