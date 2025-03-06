/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProcessInstanceBatchTerminateProcessor
    implements TypedRecordProcessor<ProcessInstanceBatchRecord> {

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;
  private final ElementInstanceState elementInstanceState;

  public ProcessInstanceBatchTerminateProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ElementInstanceState elementInstanceState) {
    commandWriter = writers.command();
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceBatchRecord> record) {
    final var recordValue = record.getValue();

    final AtomicBoolean hasChildren = new AtomicBoolean(false);
    elementInstanceState.forEachChild(
        recordValue.getBatchElementInstanceKey(),
        recordValue.getIndex(),
        (childKey, childInstance) -> {
          hasChildren.set(true);
          if (canWriteCommand(record, childInstance)) {
            terminateChildInstance(childInstance);
            return true;
          } else {
            final var nextBatchRecord =
                new ProcessInstanceBatchRecord()
                    .setProcessInstanceKey(recordValue.getProcessInstanceKey())
                    .setBatchElementInstanceKey(recordValue.getBatchElementInstanceKey())
                    .setIndex(childKey);
            final long key = keyGenerator.nextKey();
            commandWriter.appendFollowUpCommand(
                key, ProcessInstanceBatchIntent.TERMINATE, nextBatchRecord);
            return false;
          }
        });

    if (!hasChildren.get()) {
      stateWriter.appendFollowUpEvent(
          record.getKey(), ProcessInstanceBatchIntent.TERMINATED, recordValue);
    }
  }

  private boolean canWriteCommand(
      final TypedRecord<ProcessInstanceBatchRecord> record, final ElementInstance childInstance) {
    // We must have space in the batch to write both the TERMINATE command as the potential
    // follow-up batch command. An excessive 8Kb is added to account for metadata. This is way
    // more than will be necessary.
    final var expectedCommandLength =
        childInstance.getValue().getLength()
            + record.getLength()
            + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER;
    return commandWriter.canWriteCommandOfLength(expectedCommandLength);
  }

  private void terminateChildInstance(final ElementInstance childInstance) {
    if (childInstance.canTerminate()) {
      commandWriter.appendFollowUpCommand(
          childInstance.getKey(),
          ProcessInstanceIntent.TERMINATE_ELEMENT,
          childInstance.getValue());
    }
  }
}
