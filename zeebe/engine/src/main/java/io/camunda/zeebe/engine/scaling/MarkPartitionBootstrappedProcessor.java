/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoutingState;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class MarkPartitionBootstrappedProcessor implements TypedRecordProcessor<ScaleRecord> {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final RoutingState routingState;

  public MarkPartitionBootstrappedProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ProcessingState processingState) {
    this.keyGenerator = keyGenerator;
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    stateWriter = writers.state();
    routingState = processingState.getRoutingState();
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> command) {
    final var scaleUp = command.getValue();

    final var reason = validate(command);
    if (reason != null) {
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
  }

  private String validate(final TypedRecord<ScaleRecord> command) {
    final var scaleUp = command.getValue();
    if (!(routingState.desiredPartitions().size() == scaleUp.getDesiredPartitionCount())) {
      final var reason =
          String.format(
              "The redistributed partitions do not match the desired partitions. "
                  + "The redistributed partitions are %s, the desired partitions are %s.",
              scaleUp.getRedistributedPartitions(), routingState.desiredPartitions());
      rejectWith(command, RejectionType.INVALID_STATE, reason);
      return reason;
    }
    if (scaleUp.getRedistributedPartitions().size() != 1) {
      final var reason =
          String.format(
              "Only one partition can be marked as bootstrapped at a time. The redistributed partitions are %s.",
              scaleUp.getRedistributedPartitions());
      rejectWith(command, RejectionType.INVALID_ARGUMENT, reason);
      return reason;
    }
    final var partition = scaleUp.getRedistributedPartitions().getFirst();
    if (!routingState.desiredPartitions().contains(partition)
        && !routingState.currentPartitions().contains(partition)) {
      final var reason = String.format("Partition %d is not a valid partition.", partition);
      rejectWith(command, RejectionType.INVALID_ARGUMENT, reason);
      return reason;
    }

    return null;
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
