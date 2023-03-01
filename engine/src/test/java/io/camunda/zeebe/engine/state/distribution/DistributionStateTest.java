/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.distribution;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableDistributionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public final class DistributionStateTest {
  private MutableProcessingState processingState;
  private MutableDistributionState distributionState;

  @BeforeEach
  public void setUp() {
    distributionState = processingState.getDistributionState();
  }

  @Test
  public void shouldReturnFalseOnEmptyStateForHasPendingCheck() {
    // when
    final var hasPending = distributionState.hasPendingDistribution(10L);

    // then
    assertThat(hasPending).isFalse();
  }

  @Test
  public void shouldAddPendingDistribution() {
    // given
    final var distributionKey = 10L;
    final var partition = 1;
    distributionState.addCommandDistribution(distributionKey, createCommandDistributionRecord());

    // when
    distributionState.addPendingDistribution(distributionKey, partition);

    // then
    assertThat(distributionState.hasPendingDistribution(distributionKey)).isTrue();
  }

  @Test
  public void shouldRemovePendingDistribution() {
    // given
    final var distributionKey = 10L;
    final var partition = 1;
    distributionState.addCommandDistribution(distributionKey, createCommandDistributionRecord());
    distributionState.addPendingDistribution(distributionKey, partition);

    // when
    distributionState.removePendingDistribution(distributionKey, partition);

    // then
    assertThat(distributionState.hasPendingDistribution(distributionKey)).isFalse();
  }

  @Test
  public void shouldReturnNullOnRequestingStoredDistributionWhenNothingStored() {
    // when
    final var distributionRecord = distributionState.getCommandDistributionRecord(1, 1);

    // then
    assertThat(distributionRecord).isNull();
  }

  @Test
  public void shouldStoreDistributionInState() {
    // given
    final var distributionRecord = createCommandDistributionRecord();

    // when
    distributionState.addCommandDistribution(1, distributionRecord);

    // then
    final var storedDistribution =
        distributionState.getCommandDistributionRecord(1, distributionRecord.getPartitionId());

    assertThat(storedDistribution).isNotNull().isEqualTo(distributionRecord);
  }

  @Test
  public void shouldRemoveDistribution() {
    // given
    final var distributionRecord = createCommandDistributionRecord();
    distributionState.addCommandDistribution(1, distributionRecord);

    // when
    distributionState.removeCommandDistribution(1);

    // then
    final var storedDistribution =
        distributionState.getCommandDistributionRecord(1, distributionRecord.getPartitionId());
    assertThat(storedDistribution).isNull();
  }

  @Test
  public void shouldRemoveDifferentDistributions() {
    // given
    final var distributionRecord = createCommandDistributionRecord();
    distributionState.addCommandDistribution(1, distributionRecord);
    distributionState.addCommandDistribution(2, distributionRecord);

    // when
    distributionState.removeCommandDistribution(1);

    // then
    var storedDistribution =
        distributionState.getCommandDistributionRecord(1, distributionRecord.getPartitionId());
    assertThat(storedDistribution).isNull();
    storedDistribution =
        distributionState.getCommandDistributionRecord(2, distributionRecord.getPartitionId());
    assertThat(storedDistribution).isNotNull().isEqualTo(distributionRecord);
  }

  @Test
  public void shouldRemoveDistributionIdempotent() {
    // when
    distributionState.removeCommandDistribution(1);

    // then
    final var storedDistributionRecord = distributionState.getCommandDistributionRecord(1, 1);
    assertThat(storedDistributionRecord).isNull();
  }

  private CommandDistributionRecord createCommandDistributionRecord() {
    return new CommandDistributionRecord()
        .setPartitionId(1)
        .setValueType(ValueType.DEPLOYMENT)
        .setRecordValue(createDeploymentRecord());
  }

  private DeploymentRecord createDeploymentRecord() {
    final var modelInstance =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
    final var deploymentRecord = new DeploymentRecord();

    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString("resource"))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)));

    deploymentRecord
        .processesMetadata()
        .add()
        .setChecksum(wrapString("checksum"))
        .setBpmnProcessId("process")
        .setKey(1)
        .setVersion(1)
        .setResourceName(wrapString("resource"));

    return deploymentRecord;
  }
}
