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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.state.mutable.MutableDistributionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.agrona.collections.Long2ObjectHashMap;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public final class DistributionStateTest {
  private final StateHelper stateHelper = new StateHelper();
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
  public void shouldReturnFalseOnEmptyStateForHasPendingForPartitionCheck() {
    // when
    final var hasPending = distributionState.hasPendingDistribution(10L, 10);

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
    assertThat(distributionState.hasPendingDistribution(distributionKey, partition)).isTrue();
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
    final var distributionRecord1 = createCommandDistributionRecord();
    final var distributionRecord2 = createCommandDistributionRecord();
    distributionState.addCommandDistribution(1, distributionRecord1);
    distributionState.addCommandDistribution(2, distributionRecord2);

    // when
    distributionState.removeCommandDistribution(1);

    // then
    var storedDistribution =
        distributionState.getCommandDistributionRecord(1, distributionRecord1.getPartitionId());
    assertThat(storedDistribution).isNull();
    storedDistribution =
        distributionState.getCommandDistributionRecord(2, distributionRecord2.getPartitionId());
    assertThat(storedDistribution).isNotNull().isEqualTo(distributionRecord2);
  }

  @Test
  public void shouldRemoveDistributionIdempotent() {
    // when
    distributionState.removeCommandDistribution(1);

    // then
    final var storedDistributionRecord = distributionState.getCommandDistributionRecord(1, 1);
    assertThat(storedDistributionRecord).isNull();
  }

  @Test
  public void shouldFailToAddPendingDistributionIfNoCommandDistributionExists() {
    // given
    final long distributionKey = 1L;
    final int partition = 1;
    final ThrowingCallable addPending =
        () -> distributionState.addPendingDistribution(distributionKey, partition);

    // when then
    assertThat(distributionState.getCommandDistributionRecord(distributionKey, partition)).isNull();
    assertThatThrownBy(addPending)
        .hasStackTraceContaining(
            "Foreign key DbLong{1} does not exist in COMMAND_DISTRIBUTION_RECORD");
  }

  @Test
  public void shouldIterateOverPendingDistributions() {
    // given
    final var pendingDistribution = new PendingDistribution(1L, createCommandDistributionRecord());
    final int partitionId3 = 3;
    final int partitionId2 = 2;
    stateHelper.addPendingDistributionForPartitions(
        pendingDistribution, partitionId2, partitionId3);

    // when
    final List<PendingDistribution> visits = new ArrayList<>();
    distributionState.foreachPendingDistribution(
        (key, commandDistributionRecord) ->
            visits.add(new PendingDistribution(key, commandDistributionRecord)));

    // then
    assertThat(visits)
        .allSatisfy(visited -> assertThat(visited.key()).isEqualTo(pendingDistribution.key))
        .allSatisfy(
            visited ->
                Assertions.assertThat(visited.record())
                    .hasIntent(pendingDistribution.record.getIntent())
                    .hasValueType(pendingDistribution.record.getValueType())
                    .hasCommandValue(pendingDistribution.record.getCommandValue()))
        .extracting(PendingDistribution::record)
        .extracting(CommandDistributionRecord::getPartitionId)
        .describedAs("Expect that pending distributions are visited for all other partitions")
        .containsExactly(partitionId2, partitionId3);
  }

  @Test
  public void shouldIterateOverMultiplePendingDeployments() {
    // given
    final var distributions = new Long2ObjectHashMap<CommandDistributionRecord>();
    final int partitionId2 = 2;
    final int partitionId3 = 3;
    for (int distributionKey = 1; distributionKey <= 5; distributionKey++) {
      final var pendingDistribution = createCommandDistributionRecord();
      distributions.put(distributionKey, pendingDistribution);
      stateHelper.addPendingDistributionForPartitions(
          new PendingDistribution(distributionKey, pendingDistribution),
          partitionId2,
          partitionId3);
    }

    // when
    final List<PendingDistribution> visits = new ArrayList<>();
    distributionState.foreachPendingDistribution(
        (key, commandDistributionRecord) ->
            visits.add(new PendingDistribution(key, commandDistributionRecord)));

    // then
    assertThat(visits)
        .extracting(PendingDistribution::key)
        .describedAs("Expect that all pending distribution are visited")
        .containsOnly(1L, 2L, 3L, 4L, 5L);
    assertThat(visits)
        .allSatisfy(
            visited ->
                Assertions.assertThat(visited.record())
                    .hasIntent(distributions.get(visited.key).getIntent())
                    .hasValueType(distributions.get(visited.key).getValueType())
                    .hasCommandValue(distributions.get(visited.key).getCommandValue()));
    assertThat(visits)
        .extracting(PendingDistribution::record)
        .extracting(CommandDistributionRecord::getPartitionId)
        .describedAs("Expect that pending distributions are visited for all other partitions")
        .containsOnly(partitionId2, partitionId3);
    assertThat(visits).hasSize(10);
  }

  @Test
  public void shouldNotFailOnMissingDeploymentInState() {
    // given
    final var pendingDistribution = new PendingDistribution(1L, createCommandDistributionRecord());
    final int partitionId2 = 2;
    final int partitionId3 = 3;
    stateHelper.addPendingDistributionForPartitions(
        pendingDistribution, partitionId2, partitionId3);
    distributionState.removeCommandDistribution(pendingDistribution.key);

    // when
    final List<PendingDistribution> visits = new ArrayList<>();
    distributionState.foreachPendingDistribution(
        (key, commandDistributionRecord) ->
            visits.add(new PendingDistribution(key, commandDistributionRecord)));

    // then
    assertThat(visits).isEmpty();
  }

  private CommandDistributionRecord createCommandDistributionRecord() {
    return new CommandDistributionRecord()
        .setPartitionId(1)
        .setValueType(ValueType.DEPLOYMENT)
        .setIntent(DeploymentIntent.CREATE)
        .setCommandValue(createDeploymentRecord());
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

  record PendingDistribution(long key, CommandDistributionRecord record) {}

  /** Little helper class, to simplify test setup of the State */
  final class StateHelper {
    private void addPendingDistributionForPartitions(
        final PendingDistribution pendingDistribution, final int... partitionIds) {
      distributionState.addCommandDistribution(pendingDistribution.key, pendingDistribution.record);
      Arrays.stream(partitionIds)
          .forEach(
              partitionId ->
                  distributionState.addPendingDistribution(pendingDistribution.key, partitionId));
    }
  }
}
