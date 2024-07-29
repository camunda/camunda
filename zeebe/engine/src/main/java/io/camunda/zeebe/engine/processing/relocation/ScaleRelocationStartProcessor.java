/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.relocation;

import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class ScaleRelocationStartProcessor implements DistributedTypedRecordProcessor<ScaleRecord> {

  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;

  public ScaleRelocationStartProcessor(final Writers writers) {
    commandWriter = writers.command();
    stateWriter = writers.state();
  }

  @Override
  public void processNewCommand(final TypedRecord<ScaleRecord> command) {
    // TODO: Distribute to other partitions
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ScaleRecord> command) {
    stateWriter.appendFollowUpEvent(
        command.getKey(), ScaleIntent.RELOCATION_STARTED, command.getValue());
    commandWriter.appendFollowUpCommand(
        command.getKey(), ScaleIntent.RELOCATE_NEXT_CORRELATION_KEY, new ScaleRecord());
  }
}
