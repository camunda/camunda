/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scaling;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoutingState;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.Optional;

@ExcludeAuthorizationCheck
public class MarkPartitionBootstrappedProcessor
    implements DistributedTypedRecordProcessor<ScaleRecord> {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final RoutingState routingState;
  private final CommandDistributionBehavior distributionBehavior;

  public MarkPartitionBootstrappedProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ProcessingState processingState,
      final CommandDistributionBehavior distributionBehavior) {
    this.keyGenerator = keyGenerator;
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    stateWriter = writers.state();
    routingState = processingState.getRoutingState();
    this.distributionBehavior = distributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<ScaleRecord> command) {
    final var scaleUp = command.getValue();

    final var rejection = validate(command);
    if (rejection.isPresent()) {
      rejectWith(command, rejection.get().getLeft(), rejection.get().getRight());
      return;
    }
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
    distributionBehavior.withKey(scalingKey).inQueue(DistributionQueue.SCALING).distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ScaleRecord> command) {
    final var scaleUp = command.getValue();
    final var scalingKey = command.getKey();
    final var wasAlreadyBootstrapped = areAllPartitionsBootstrapped();
    stateWriter.appendFollowUpEvent(scalingKey, ScaleIntent.PARTITION_BOOTSTRAPPED, scaleUp);
    if (!wasAlreadyBootstrapped && areAllPartitionsBootstrapped()) {
      stateWriter.appendFollowUpEvent(scalingKey, ScaleIntent.SCALED_UP, scaleUp);
    }
    distributionBehavior.acknowledgeCommand(command);
  }

  private Optional<Tuple<RejectionType, String>> validate(final TypedRecord<ScaleRecord> command) {
    final var scaleUp = command.getValue();
    if (scaleUp.getRedistributedPartitions().size() != 1) {
      final var reason =
          "Only one partition can be marked as bootstrapped at a time. The redistributed partitions are %s."
              .formatted(scaleUp.getRedistributedPartitions());
      return Optional.of(new Tuple<>(RejectionType.INVALID_ARGUMENT, reason));
    }
    final var partition = scaleUp.getRedistributedPartitions().getFirst();
    if (partition > routingState.desiredPartitions().size()) {
      final var reason =
          "The redistributed partitions do not match the desired partitions. The redistributed partition is %s, the desired partitions are %s."
              .formatted(partition, routingState.desiredPartitions());
      return Optional.of(new Tuple<>(RejectionType.INVALID_STATE, reason));
    }
    if (!routingState.desiredPartitions().contains(partition)
        && !routingState.currentPartitions().contains(partition)) {
      final var reason = "Partition %d is not a valid partition.".formatted(partition);
      return Optional.of(new Tuple<>(RejectionType.INVALID_ARGUMENT, reason));
    }

    return Optional.empty();
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
