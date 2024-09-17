/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
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
  public void shouldReturnFalseOnEmptyStateForHasRetriableCheck() {
    // when
    final var hasRetriable = distributionState.hasRetriableDistribution(10L);

    // then
    assertThat(hasRetriable).isFalse();
  }

  @Test
  public void shouldReturnFalseOnEmptyStateForHasRetriableForPartitionCheck() {
    // when
    final var hasRetriable = distributionState.hasRetriableDistribution(10L, 10);

    // then
    assertThat(hasRetriable).isFalse();
  }

  @Test
  public void shouldAddRetriableDistribution() {
    // given
    final var distributionKey = 10L;
    final var partition = 1;
    distributionState.addCommandDistribution(distributionKey, createCommandDistributionRecord());

    // when
    distributionState.addRetriableDistribution(distributionKey, partition);

    // then
    assertThat(distributionState.hasRetriableDistribution(distributionKey)).isTrue();
    assertThat(distributionState.hasRetriableDistribution(distributionKey, partition)).isTrue();
  }

  @Test
  public void shouldRemoveRetriableDistribution() {
    // given
    final var distributionKey = 10L;
    final var partition = 1;
    distributionState.addCommandDistribution(distributionKey, createCommandDistributionRecord());
    distributionState.addRetriableDistribution(distributionKey, partition);

    // when
    distributionState.removeRetriableDistribution(distributionKey, partition);

    // then
    assertThat(distributionState.hasRetriableDistribution(distributionKey)).isFalse();
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
    final var hasPendingDistribution = distributionState.hasPendingDistribution(10L, 10);

    // then
    assertThat(hasPendingDistribution).isFalse();
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
  public void shouldFailToAddRetriableDistributionIfNoCommandDistributionExists() {
    // given
    final long distributionKey = 1L;
    final int partition = 1;
    final ThrowingCallable addRetriable =
        () -> distributionState.addRetriableDistribution(distributionKey, partition);

    // when then
    assertThat(distributionState.getCommandDistributionRecord(distributionKey, partition)).isNull();
    assertThatThrownBy(addRetriable)
        .hasStackTraceContaining(
            "Foreign key DbLong{1} does not exist in COMMAND_DISTRIBUTION_RECORD");
  }

  @Test
  public void shouldIterateOverRetriableDistributions() {
    // given
    final var retriableDistribution =
        new RetriableDistribution(1L, createCommandDistributionRecord());
    final int partitionId3 = 3;
    final int partitionId2 = 2;
    stateHelper.addRetriableDistributionForPartitions(
        retriableDistribution, partitionId2, partitionId3);

    // when
    final List<RetriableDistribution> visits = new ArrayList<>();
    distributionState.foreachRetriableDistribution(
        (key, commandDistributionRecord) ->
            visits.add(new RetriableDistribution(key, commandDistributionRecord)));

    // then
    assertThat(visits)
        .allSatisfy(visited -> assertThat(visited.key()).isEqualTo(retriableDistribution.key))
        .allSatisfy(
            visited ->
                Assertions.assertThat(visited.record())
                    .hasIntent(retriableDistribution.record.getIntent())
                    .hasValueType(retriableDistribution.record.getValueType())
                    .hasCommandValue(retriableDistribution.record.getCommandValue()))
        .extracting(RetriableDistribution::record)
        .extracting(CommandDistributionRecord::getPartitionId)
        .describedAs("Expect that retriable distributions are visited for all other partitions")
        .containsExactly(partitionId2, partitionId3);
  }

  @Test
  public void shouldIterateOverMultipleRetriableDeployments() {
    // given
    final var distributions = new Long2ObjectHashMap<CommandDistributionRecord>();
    final int partitionId2 = 2;
    final int partitionId3 = 3;
    for (int distributionKey = 1; distributionKey <= 5; distributionKey++) {
      final var retriableDistribution = createCommandDistributionRecord();
      distributions.put(distributionKey, retriableDistribution);
      stateHelper.addRetriableDistributionForPartitions(
          new RetriableDistribution(distributionKey, retriableDistribution),
          partitionId2,
          partitionId3);
    }

    // when
    final List<RetriableDistribution> visits = new ArrayList<>();
    distributionState.foreachRetriableDistribution(
        (key, commandDistributionRecord) ->
            visits.add(new RetriableDistribution(key, commandDistributionRecord)));

    // then
    assertThat(visits)
        .extracting(RetriableDistribution::key)
        .describedAs("Expect that all retriable distribution are visited")
        .containsOnly(1L, 2L, 3L, 4L, 5L);
    assertThat(visits)
        .allSatisfy(
            visited ->
                Assertions.assertThat(visited.record())
                    .hasIntent(distributions.get(visited.key).getIntent())
                    .hasValueType(distributions.get(visited.key).getValueType())
                    .hasCommandValue(distributions.get(visited.key).getCommandValue()));
    assertThat(visits)
        .extracting(RetriableDistribution::record)
        .extracting(CommandDistributionRecord::getPartitionId)
        .describedAs("Expect that retriable distributions are visited for all other partitions")
        .containsOnly(partitionId2, partitionId3);
    assertThat(visits).hasSize(10);
  }

  @Test
  public void shouldNotFailOnMissingDeploymentInState() {
    // given
    final var retriableDistribution =
        new RetriableDistribution(1L, createCommandDistributionRecord());
    final int partitionId2 = 2;
    final int partitionId3 = 3;
    stateHelper.addRetriableDistributionForPartitions(
        retriableDistribution, partitionId2, partitionId3);
    distributionState.removeCommandDistribution(retriableDistribution.key);

    // when
    final List<RetriableDistribution> visits = new ArrayList<>();
    distributionState.foreachRetriableDistribution(
        (key, commandDistributionRecord) ->
            visits.add(new RetriableDistribution(key, commandDistributionRecord)));

    // then
    assertThat(visits).isEmpty();
  }

  @Test
  public void shouldDetectEmptyQueue() {
    // when
    final var hasQueuedDistributions = distributionState.hasQueuedDistributions("empty-queue");

    // then
    assertThat(hasQueuedDistributions).isFalse();
  }

  @Test
  public void shouldDetectNonEmptyQueue() {
    // given
    final var queue = "test-queue";
    final var distributionKey = 1L;
    final var distributionRecord = createCommandDistributionRecord();
    distributionState.addCommandDistribution(distributionKey, distributionRecord);

    // when
    distributionState.enqueueCommandDistribution(queue, distributionKey, 2);

    // then
    assertThat(distributionState.hasQueuedDistributions(queue)).isTrue();
  }

  @Test
  public void shouldFindAllContinuationCommands() {
    // given
    final var queue = "test-queue";
    final var record1 = createContinuationCommand(queue, "continuation1");
    final var record2 = createContinuationCommand(queue, "continuation2");
    final var record3 = createContinuationCommand(queue, "continuation3");

    // when
    distributionState.addContinuationCommand(1L, record1);
    distributionState.addContinuationCommand(2L, record2);
    distributionState.addContinuationCommand(3L, record3);

    // then
    final var found = new LinkedList<Long>();
    distributionState.forEachContinuationCommand(queue, found::add);

    assertThat(found).containsExactly(1L, 2L, 3L);
  }

  @Test
  public void shouldFindContinuationCommandsForSpecificQueue() {
    // given
    final var queue1 = "test-queue-1";
    final var queue2 = "test-queue-2";
    final var record1 = createContinuationCommand(queue1, "continuation1");
    final var record2 = createContinuationCommand(queue2, "continuation2");
    final var record3 = createContinuationCommand(queue1, "continuation3");

    // when
    distributionState.addContinuationCommand(1L, record1);
    distributionState.addContinuationCommand(2L, record2);
    distributionState.addContinuationCommand(3L, record3);

    // then
    final var found = new LinkedList<Long>();
    distributionState.forEachContinuationCommand(queue1, found::add);

    assertThat(found).containsExactly(1L, 3L);
  }

  @Test
  public void shouldFindSingleContinuationCommand() {
    // given
    final var queue = "test-queue";
    final var record = createContinuationCommand(queue, "continuation");

    // when
    distributionState.addContinuationCommand(1L, record);

    // then
    assertThat(distributionState.getContinuationRecord(queue, 1L)).isNotNull();
  }

  @Test
  public void shouldRemoveContinuationCommand() {
    // given
    final var queue = "test-queue";
    final var record1 = createContinuationCommand(queue, "continuation1");
    final var record2 = createContinuationCommand(queue, "continuation2");
    final var record3 = createContinuationCommand(queue, "continuation3");

    distributionState.addContinuationCommand(1L, record1);
    distributionState.addContinuationCommand(2L, record2);
    distributionState.addContinuationCommand(3L, record3);

    // when
    distributionState.removeContinuationCommand(2L, queue);

    // then
    final var found = new LinkedList<Long>();
    distributionState.forEachContinuationCommand(queue, found::add);

    assertThat(found).containsExactly(1L, 3L);
  }

  private CommandDistributionRecord createContinuationCommand(
      final String queueName, final String id) {
    return new CommandDistributionRecord()
        .setPartitionId(1)
        .setQueueId(queueName)
        .setValueType(ValueType.SIGNAL)
        .setIntent(SignalIntent.BROADCAST)
        .setCommandValue(new SignalRecord().setSignalName(id));
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

  record ContinuationCommand(long key, String continuationId) {}

  record RetriableDistribution(long key, CommandDistributionRecord record) {}

  /** Little helper class, to simplify test setup of the State */
  final class StateHelper {
    private void addRetriableDistributionForPartitions(
        final RetriableDistribution retriableDistribution, final int... partitionIds) {
      distributionState.addCommandDistribution(
          retriableDistribution.key, retriableDistribution.record);
      Arrays.stream(partitionIds)
          .forEach(
              partitionId ->
                  distributionState.addRetriableDistribution(
                      retriableDistribution.key, partitionId));
    }
  }
}
