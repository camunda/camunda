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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoutingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.scaling.RedistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.scaling.RedistributionIntent;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.PartitionUtil;
import java.util.HashSet;
import java.util.Optional;

public class ScaleUpProcessor implements TypedRecordProcessor<ScaleRecord> {
  private final KeyGenerator keyGenerator;
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final RoutingState routingState;

  public ScaleUpProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ProcessingState processingState) {
    this.keyGenerator = keyGenerator;
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    stateWriter = writers.state();
    routingState = processingState.getRoutingState();
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> command) {
    final var scaleUp = command.getValue();

    final var optionalRejection = validateCommand(command);
    if (optionalRejection.isPresent()) {
      final var rejection = optionalRejection.get();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var scalingKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(scalingKey, ScaleIntent.SCALING_UP, scaleUp);
    responseWriter.writeEventOnCommand(scalingKey, ScaleIntent.SCALING_UP, scaleUp, command);
    commandWriter.appendFollowUpCommand(
        scalingKey, RedistributionIntent.START, new RedistributionRecord());
  }

  private Optional<Rejection> validateCommand(final TypedRecord<ScaleRecord> command) {
    if (!routingState.isInitialized()) {
      return Optional.of(
          new Rejection(
              RejectionType.INVALID_STATE,
              "Routing state is not initialized, partition scaling is probably disabled."));
    }

    final var requestedPartitionCount = command.getValue().getDesiredPartitionCount();
    final var currentPartitionsInRoutingState = routingState.currentPartitions();
    final var desiredPartitionsInRoutingState = routingState.desiredPartitions();

    final var allPartitionsInRoutingState = new HashSet<>();
    allPartitionsInRoutingState.addAll(currentPartitionsInRoutingState);
    allPartitionsInRoutingState.addAll(desiredPartitionsInRoutingState);

    if (requestedPartitionCount < Protocol.START_PARTITION_ID) {
      return Optional.of(
          new Rejection(RejectionType.INVALID_ARGUMENT, "Partition count must be at least 1"));
    }

    if (requestedPartitionCount > Protocol.MAXIMUM_PARTITIONS) {
      return Optional.of(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Partition count must be at most " + Protocol.MAXIMUM_PARTITIONS));
    }

    final var requestedPartitions = PartitionUtil.allPartitions(requestedPartitionCount);

    if (allPartitionsInRoutingState.equals(requestedPartitions)) {
      return Optional.of(
          new Rejection(
              RejectionType.ALREADY_EXISTS, "The desired partition count was already requested"));
    }

    if (!desiredPartitionsInRoutingState.isEmpty()) {
      return Optional.of(
          new Rejection(
              RejectionType.INVALID_STATE,
              "The desired partition count conflicts with the current state. This should not happen, is there a concurrent scaling operation?"));
    }

    if (!requestedPartitions.containsAll(currentPartitionsInRoutingState)) {
      return Optional.of(
          new Rejection(
              RejectionType.INVALID_STATE,
              "The desired partition count is smaller than the currently active partitions"));
    }

    return Optional.empty();
  }

  private record Rejection(RejectionType type, String reason) {}
}
