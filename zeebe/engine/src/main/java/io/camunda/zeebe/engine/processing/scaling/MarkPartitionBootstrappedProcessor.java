/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scaling;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.deployment.StartEventSubscriptionManager;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.resource.StartEventSubscriptions;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoutingState;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.Either.Left;
import io.camunda.zeebe.util.Either.Right;
import io.camunda.zeebe.util.collection.Tuple;

@ExcludeAuthorizationCheck
public class MarkPartitionBootstrappedProcessor
    implements DistributedTypedRecordProcessor<ScaleRecord> {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final RoutingState routingState;
  private final CommandDistributionBehavior distributionBehavior;
  private final ProcessState processState;
  private final StartEventSubscriptions startEventSubscriptions;

  public MarkPartitionBootstrappedProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ProcessingState processingState,
      final CommandDistributionBehavior distributionBehavior,
      final BpmnBehaviors bpmnBehaviors) {
    this.keyGenerator = keyGenerator;
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    stateWriter = writers.state();
    routingState = processingState.getRoutingState();
    this.distributionBehavior = distributionBehavior;
    processState = processingState.getProcessState();
    final var startEventSubscriptionManager =
        new StartEventSubscriptionManager(processingState, keyGenerator, stateWriter);
    startEventSubscriptions =
        new StartEventSubscriptions(
            bpmnBehaviors.expressionBehavior(),
            bpmnBehaviors.catchEventBehavior(),
            startEventSubscriptionManager);
  }

  @Override
  public void processNewCommand(final TypedRecord<ScaleRecord> command) {
    final var scaleUp = command.getValue();

    switch (validate(command)) {
      case Left(final var tuple) -> {
        rejectWith(command, tuple.getLeft(), tuple.getRight());
      }
      case Right(final var bootstrappedPartition) -> {
        final var scalingKey = keyGenerator.nextKey();
        final var wasAlreadyBootstrapped = areAllPartitionsBootstrapped();
        stateWriter.appendFollowUpEvent(scalingKey, ScaleIntent.PARTITION_BOOTSTRAPPED, scaleUp);
        responseWriter.writeEventOnCommand(
            scalingKey, ScaleIntent.PARTITION_BOOTSTRAPPED, scaleUp, command);

        // now the PARTITION_BOOTSTRAPPED event has been applied to the state, let's check if
        // it was the last partition missing.
        if (!wasAlreadyBootstrapped && areAllPartitionsBootstrapped()) {
          stateWriter.appendFollowUpEvent(scalingKey, ScaleIntent.SCALED_UP, scaleUp);
        }
        distributionBehavior
            .withKey(scalingKey)
            .inQueue(DistributionQueue.SCALING)
            .distribute(command);
      }
    }
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ScaleRecord> command) {
    final var scaleUp = command.getValue();
    final var scalingKey = command.getKey();
    final var wasAlreadyBootstrapped = areAllPartitionsBootstrapped();
    stateWriter.appendFollowUpEvent(scalingKey, ScaleIntent.PARTITION_BOOTSTRAPPED, scaleUp);
    // if the partition that has completed bootstrapping is the current one and the command was
    // not already processed, resubscribe to all message start events and signals
    if (scaleUp.getRedistributedPartitions().contains(command.getPartitionId())
        && !routingState.currentPartitions().contains(command.getPartitionId())) {
      subscribeToStartEventsAndSignals();
    }
    if (!wasAlreadyBootstrapped && areAllPartitionsBootstrapped()) {
      stateWriter.appendFollowUpEvent(scalingKey, ScaleIntent.SCALED_UP, scaleUp);
    }
    distributionBehavior.acknowledgeCommand(command);
  }

  private void subscribeToStartEventsAndSignals() {
    processState.forEachProcessWithLatestVersion(
        persistedProcess -> {
          final var deployed =
              processState.getLatestProcessVersionByProcessId(
                  persistedProcess.getBpmnProcessId(), persistedProcess.getTenantId());
          startEventSubscriptions.resubscribeToStartEvents(deployed);
          return true;
        });
  }

  private Either<Tuple<RejectionType, String>, Integer> validate(
      final TypedRecord<ScaleRecord> command) {
    final var scaleUp = command.getValue();
    if (scaleUp.getRedistributedPartitions().size() != 1) {
      final var reason =
          "Only one partition can be marked as bootstrapped at a time. The redistributed partitions are %s."
              .formatted(scaleUp.getRedistributedPartitions());
      return Either.left(new Tuple<>(RejectionType.INVALID_ARGUMENT, reason));
    }
    final var partition = scaleUp.getRedistributedPartitions().getFirst();
    if (partition > routingState.desiredPartitions().size()) {
      final var reason =
          "The redistributed partitions do not match the desired partitions. The redistributed partition is %s, the desired partitions are %s."
              .formatted(partition, routingState.desiredPartitions());
      return Either.left(new Tuple<>(RejectionType.INVALID_STATE, reason));
    }
    if (!routingState.desiredPartitions().contains(partition)
        && !routingState.currentPartitions().contains(partition)) {
      final var reason = "Partition %d is not a valid partition.".formatted(partition);
      return Either.left(new Tuple<>(RejectionType.INVALID_ARGUMENT, reason));
    }

    return Either.right(scaleUp.getRedistributedPartitions().getFirst());
  }

  private boolean areAllPartitionsBootstrapped() {
    return routingState.desiredPartitions().equals(routingState.currentPartitions());
  }

  private void rejectWith(
      final TypedRecord<ScaleRecord> command, final RejectionType type, final String reason) {
    rejectionWriter.appendRejection(command, type, reason);
    responseWriter.writeRejectionOnCommand(command, type, reason);
  }
}
