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
import io.camunda.zeebe.engine.state.immutable.RoutingState;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class ScaleUpProcessor implements TypedRecordProcessor<ScaleRecord> {
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final RoutingState routingState;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public ScaleUpProcessor(
      final KeyGenerator keyGenerator, final Writers writers, final RoutingState routingState) {
    this.keyGenerator = keyGenerator;
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    stateWriter = writers.state();
    this.routingState = routingState;
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> command) {
    final var scaleUp = command.getValue();

    if (!routingState.isInitialized()) {
      final var reason =
          "Routing state is not initialized, partition scaling is probably disabled.";
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, reason);
      return;
    }

    if (scaleUp.desiredPartitionCount() < scaleUp.currentPartitionCount()) {
      final var reason = "Desired partition count must be greater than current partition count";
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_ARGUMENT, reason);
      rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, reason);
      return;
    }

    final var scalingKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(scalingKey, ScaleIntent.SCALING_UP, scaleUp);
    responseWriter.writeEventOnCommand(scalingKey, ScaleIntent.SCALING_UP, scaleUp, command);

    stateWriter.appendFollowUpEvent(scalingKey, ScaleIntent.SCALED_UP, new ScaleRecord());
  }
}
