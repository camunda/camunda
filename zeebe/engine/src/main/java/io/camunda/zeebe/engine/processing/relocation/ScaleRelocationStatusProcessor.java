/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.relocation;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.RelocationState;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class ScaleRelocationStatusProcessor implements TypedRecordProcessor<ScaleRecord> {

  private final RelocationState relocationState;
  private final TypedResponseWriter responseWriter;
  private final StateWriter stateWriter;

  public ScaleRelocationStatusProcessor(
      final RelocationState relocationState, final Writers writers) {
    this.relocationState = relocationState;
    responseWriter = writers.response();
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> record) {
    if (relocationState.getRoutingInfo().newPartitionCount()
        == relocationState.getRoutingInfo().completedPartitions().size()) {
      final var response = new ScaleRecord();
      // TODO: Instead of sending boolean response, it can return the current detailed routing info
      response.setCompleted();
      responseWriter.writeEventOnCommand(
          record.getKey(), ScaleIntent.RELOCATION_STATUS_RESPONSE, response, record);
      // We have to write an empty followup event to mark the command as processed.
      stateWriter.appendFollowUpEvent(-1, ScaleIntent.RELOCATION_COMPLETED, response);
    } else {
      final var response = new ScaleRecord();
      responseWriter.writeEventOnCommand(
          record.getKey(), ScaleIntent.RELOCATION_STATUS_RESPONSE, response, record);
      stateWriter.appendFollowUpEvent(-1, ScaleIntent.RELOCATION_COMPLETED, response);
    }
  }
}
