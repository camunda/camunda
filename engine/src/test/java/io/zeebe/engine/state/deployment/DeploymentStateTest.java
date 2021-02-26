/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.deployment;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.state.mutable.MutableDeploymentState;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.value.deployment.ResourceType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DeploymentStateTest {

  @Rule public final ZeebeStateRule stateRule = new ZeebeStateRule();

  private MutableDeploymentState deploymentState;

  @Before
  public void setUp() {
    final var zeebeState = stateRule.getZeebeState();
    deploymentState = zeebeState.getDeploymentState();
  }

  @Test
  public void shouldReturnFalseOnEmptyStateForHasPendingCheck() {
    // given

    // when
    final var hasPending = deploymentState.hasPendingDeploymentDistribution(10L);

    // then
    assertThat(hasPending).isFalse();
  }

  @Test
  public void shouldAddPendingDeployment() {
    // given
    final var deploymentKey = 10L;
    final var partition = 1;

    // when
    deploymentState.addPendingDeploymentDistribution(deploymentKey, partition);

    // then
    assertThat(deploymentState.hasPendingDeploymentDistribution(deploymentKey)).isTrue();
  }

  @Test
  public void shouldReturnFalseForDifferentPendingDeploymentOnHasPendingCheck() {
    // given
    final var deploymentKey = 10L;
    final var partition = 1;
    deploymentState.addPendingDeploymentDistribution(deploymentKey, partition);

    // when
    final var hasPending = deploymentState.hasPendingDeploymentDistribution(12L);

    // then
    assertThat(hasPending).isFalse();
  }

  @Test
  public void shouldRemovePendingDeployment() {
    // given
    final var deploymentKey = 10L;
    final var partition = 1;
    deploymentState.addPendingDeploymentDistribution(deploymentKey, partition);

    // when
    deploymentState.removePendingDeploymentDistribution(deploymentKey, partition);

    // then
    assertThat(deploymentState.hasPendingDeploymentDistribution(deploymentKey)).isFalse();
  }

  @Test
  public void shouldRemovePendingDeploymentIdempotent() {
    // given
    final var deploymentKey = 10L;
    final var partition = 1;
    deploymentState.addPendingDeploymentDistribution(deploymentKey, partition);
    deploymentState.removePendingDeploymentDistribution(deploymentKey, partition);

    // when
    deploymentState.removePendingDeploymentDistribution(deploymentKey, partition);

    // then
    assertThat(deploymentState.hasPendingDeploymentDistribution(deploymentKey)).isFalse();
  }

  @Test
  public void shouldReturnTrueForDifferentPendingDeploymentOnHasPendingCheck() {
    // given
    final var deploymentKey = 10L;
    final var partition = 1;
    deploymentState.addPendingDeploymentDistribution(deploymentKey, partition);
    deploymentState.addPendingDeploymentDistribution(12L, partition);
    deploymentState.removePendingDeploymentDistribution(deploymentKey, partition);

    // when
    final var hasPending = deploymentState.hasPendingDeploymentDistribution(12L);

    // then
    assertThat(hasPending).isTrue();
  }

  @Test
  public void shouldReturnNullOnRequestingStoredDeploymentWhenNothingStored() {
    // given

    // when
    final var storedDeploymentRecord = deploymentState.getStoredDeploymentRecord(1);

    // then
    assertThat(storedDeploymentRecord).isNull();
  }

  @Test
  public void shouldRemoveDeploymentIdempotent() {
    // given

    // when
    deploymentState.removeDeploymentRecord(1);

    // then
    final var storedDeploymentRecord = deploymentState.getStoredDeploymentRecord(1);
    assertThat(storedDeploymentRecord).isNull();
  }

  @Test
  public void shouldStoreDeploymentInState() {
    // given
    final var deployment = createDeployment();

    // when
    deploymentState.storeDeploymentRecord(1, deployment);

    // then
    final var storedDeploymentRecord = deploymentState.getStoredDeploymentRecord(1);

    assertThat(storedDeploymentRecord).isNotNull().isEqualTo(deployment);
  }

  @Test
  public void shouldRemoveStoredDeployment() {
    // given
    final var deployment = createDeployment();
    deploymentState.storeDeploymentRecord(1, deployment);

    // when
    deploymentState.removeDeploymentRecord(1);

    // then
    final var storedDeploymentRecord = deploymentState.getStoredDeploymentRecord(1);
    assertThat(storedDeploymentRecord).isNull();
  }

  @Test
  public void shouldRemoveDifferentDeployment() {
    // given
    final var deployment = createDeployment();
    deploymentState.storeDeploymentRecord(1, deployment);
    deploymentState.storeDeploymentRecord(2, deployment);

    // when
    deploymentState.removeDeploymentRecord(2);

    // then
    var storedDeploymentRecord = deploymentState.getStoredDeploymentRecord(2);
    assertThat(storedDeploymentRecord).isNull();

    storedDeploymentRecord = deploymentState.getStoredDeploymentRecord(1);
    assertThat(storedDeploymentRecord).isNotNull().isEqualTo(deployment);
  }

  private DeploymentRecord createDeployment() {
    final var modelInstance =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
    final var deploymentRecord = new DeploymentRecord();

    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString("resource"))
        .setResourceType(ResourceType.BPMN_XML)
        .setResource(wrapString(Bpmn.convertToString(modelInstance)));

    deploymentRecord
        .workflows()
        .add()
        .setChecksum(wrapString("checksum"))
        .setBpmnProcessId("process")
        .setKey(1)
        .setVersion(1)
        .setResourceName(wrapString("resource"))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)));

    return deploymentRecord;
  }
}
