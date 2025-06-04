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

public class MarkPartitionsBootstrappedProcessor implements TypedRecordProcessor<ScaleRecord> {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final RoutingState routingState;

  public MarkPartitionsBootstrappedProcessor(
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
    if (!(routingState.desiredPartitions().size() == scaleUp.getDesiredPartitionCount())) {
      final var reason =
          String.format(
              "The redistributed partitions do not match the desired partitions. "
                  + "The redistributed partitions are %s, the desired partitions are %s.",
              scaleUp.getRedistributedPartitions(), routingState.desiredPartitions());
      rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, reason);
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_ARGUMENT, reason);
    }
    final var scalingKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(scalingKey, ScaleIntent.PARTITIONS_BOOTSTRAPPED, scaleUp);
    // TODO remove when relocation is needed
    responseWriter.writeEventOnCommand(
        scalingKey, ScaleIntent.MARK_PARTITIONS_BOOTSTRAPPED, scaleUp, command);
    stateWriter.appendFollowUpEvent(scalingKey, ScaleIntent.SCALED_UP, scaleUp);
  }
}
