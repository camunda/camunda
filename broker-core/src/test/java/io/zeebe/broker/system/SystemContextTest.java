/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system;

import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.util.sched.clock.ControlledActorClock;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SystemContextTest {

  @Rule public ExpectedException expectedException = ExpectedException.none();

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

  private SystemContext initSystemContext(BrokerCfg brokerCfg) {
    return new SystemContext(brokerCfg, "test", new ControlledActorClock());
  }
}
