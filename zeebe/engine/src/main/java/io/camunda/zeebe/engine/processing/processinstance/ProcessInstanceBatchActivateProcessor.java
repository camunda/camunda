/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

@ExcludeAuthorizationCheck
public final class ProcessInstanceBatchActivateProcessor
    implements TypedRecordProcessor<ProcessInstanceBatchRecord> {
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;

  public ProcessInstanceBatchActivateProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ElementInstanceState elementInstanceState,
      final ProcessState processState) {
    commandWriter = writers.command();
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceBatchRecord> record) {
    final var recordValue = record.getValue();

    final ProcessInstanceRecord childInstanceRecord = createChildInstanceRecord(recordValue);
    var amountOfChildInstancesToActivate = recordValue.getIndex();

    if (amountOfChildInstancesToActivate > 0) {
      // loop the next
      while (amountOfChildInstancesToActivate > 0) {
        if (canWriteCommands(record, childInstanceRecord)) {
          final long childInstanceKey = keyGenerator.nextKey();
          commandWriter.appendFollowUpCommand(
              childInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, childInstanceRecord);
          amountOfChildInstancesToActivate--;
        } else {
          writeFollowupBatchCommand(recordValue, amountOfChildInstancesToActivate);
          break;
        }
      }
    } else {
      // write activated even
      stateWriter.appendFollowUpEvent(
          record.getKey(), ProcessInstanceBatchIntent.ACTIVATED, recordValue);
    }
  }

  private ProcessInstanceRecord createChildInstanceRecord(
      final ProcessInstanceBatchRecord recordValue) {
    final var parentElementInstance =
        elementInstanceState.getInstance(recordValue.getBatchElementInstanceKey());
    final var processDefinition =
        processState
            .getProcessByKeyAndTenant(
                parentElementInstance.getValue().getProcessDefinitionKey(),
                parentElementInstance.getValue().getTenantId())
            .getProcess();

    final var parentElement =
        processDefinition.getElementById(parentElementInstance.getValue().getElementId());
    final var childElement = ((ExecutableMultiInstanceBody) parentElement).getInnerActivity();

    final var childInstanceRecord = new ProcessInstanceRecord();
    childInstanceRecord.wrap(parentElementInstance.getValue());
    childInstanceRecord
        .setFlowScopeKey(parentElementInstance.getKey())
        .setElementId(childElement.getId())
        .setBpmnElementType(childElement.getElementType())
        .setBpmnEventType(childElement.getEventType());
    return childInstanceRecord;
  }

  private void writeFollowupBatchCommand(
      final ProcessInstanceBatchRecord recordValue, final long index) {
    final var nextBatchRecord =
        new ProcessInstanceBatchRecord()
            .setProcessInstanceKey(recordValue.getProcessInstanceKey())
            .setBatchElementInstanceKey(recordValue.getBatchElementInstanceKey())
            .setIndex(index);
    final long key = keyGenerator.nextKey();
    commandWriter.appendFollowUpCommand(key, ProcessInstanceBatchIntent.ACTIVATE, nextBatchRecord);
  }

  private boolean canWriteCommands(
      final TypedRecord<ProcessInstanceBatchRecord> record,
      final ProcessInstanceRecord childInstanceRecord) {
    // We must have space in the batch to write both the ACTIVATE command as the potential
    // follow-up batch command. An excessive 8Kb is added to account for metadata. This is way
    // more than will be necessary.
    final var expectedCommandLength =
        record.getLength()
            + childInstanceRecord.getLength()
            + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER;
    return commandWriter.canWriteCommandOfLength(expectedCommandLength);
  }
}
