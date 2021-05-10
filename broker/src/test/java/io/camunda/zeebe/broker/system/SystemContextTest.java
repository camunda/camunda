/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.util.sched.clock.ControlledActorClock;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

public final class SystemContextTest {

  @Rule public final ExpectedException expectedException = ExpectedException.none();

  @Test
  public void shouldThrowExceptionIfNodeIdIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setNodeId(-1);

    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Node id -1 needs to be non negative and smaller then cluster size 1.");

    initSystemContext(brokerCfg);
  }

  @Test
  public void shouldThrowExceptionIfNodeIdIsLargerThenClusterSize() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setNodeId(2);

    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Node id 2 needs to be non negative and smaller then cluster size 1.");

    initSystemContext(brokerCfg);
  }

  @Test
  public void shouldThrowExceptionIfReplicationFactorIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setReplicationFactor(-1);

    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Replication factor -1 needs to be larger then zero and not larger then cluster size 1.");

    initSystemContext(brokerCfg);
  }

  @Test
  public void shouldThrowExceptionIfReplicationFactorIsLargerThenClusterSize() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setReplicationFactor(2);

    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Replication factor 2 needs to be larger then zero and not larger then cluster size 1.");

    initSystemContext(brokerCfg);
  }

  @Test
  public void shouldThrowExceptionIfPartitionsCountIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setPartitionsCount(-1);

    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Partition count must not be smaller then 1.");

    initSystemContext(brokerCfg);
  }

  @Test
  public void shouldThrowExceptionIfSnapshotPeriodIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofMinutes(-1));

    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Snapshot period PT-1M needs to be larger then or equals to one minute.");

    initSystemContext(brokerCfg);
  }

  @Test
  public void shouldThrowExceptionIfSnapshotPeriodIsTooSmall() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofSeconds(1));

    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Snapshot period PT1S needs to be larger then or equals to one minute.");

    initSystemContext(brokerCfg);
  }

  @Test
  public void shouldThrowExceptionIfBatchSizeIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().setMaxAppendBatchSize(DataSize.of(-1, DataUnit.BYTES));

    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Expected to have an append batch size maximum which is non negative and smaller then '2147483647', but was '-1B'.");

    initSystemContext(brokerCfg);
  }

  @Test
  public void shouldThrowExceptionIfBatchSizeIsTooLarge() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().setMaxAppendBatchSize(DataSize.of(3, DataUnit.GIGABYTES));

    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Expected to have an append batch size maximum which is non negative and smaller then '2147483647', but was '3221225472B'.");

    initSystemContext(brokerCfg);
  }

  @Test
  public void shouldNotThrowExceptionIfSnapshotPeriodIsEqualToOneMinute() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofMinutes(1));

    // when
    final var systemContext = initSystemContext(brokerCfg);

    // then
    assertThat(systemContext.getBrokerConfiguration().getData().getSnapshotPeriod())
        .isEqualTo(Duration.ofMinutes(1));
  }

  private SystemContext initSystemContext(final BrokerCfg brokerCfg) {
    return new SystemContext(brokerCfg, "test", new ControlledActorClock());
  }
}
