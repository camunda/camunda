/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

@ExcludeAuthorizationCheck
public final class ProcessInstanceBatchActivateProcessor
    implements TypedRecordProcessor<ProcessInstanceBatchRecord> {

  public static final String PARENT_NOT_FOUND_ERROR_MESSAGE =
      "Expected to activate child for batch element instance, but no parent element instance found for key '%s'. The parent was likely terminated before processing this batch activation.";
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final TypedRejectionWriter rejectionWriter;

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
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceBatchRecord> record) {
    final var recordValue = record.getValue();
    final var remainingChildrenToActivate = recordValue.getIndex();

    final var batchElementInstanceKey = record.getValue().getBatchElementInstanceKey();
    final var parentElementInstance = elementInstanceState.getInstance(batchElementInstanceKey);
    if (parentElementInstance == null) {
      rejectionWriter.appendRejection(
          record,
          RejectionType.INVALID_STATE,
          PARENT_NOT_FOUND_ERROR_MESSAGE.formatted(batchElementInstanceKey));
      return;
    }

    if (remainingChildrenToActivate > 0) {
      writeActivateChildCommand(parentElementInstance);
    }

    writeNextBatchCommand(remainingChildrenToActivate - 1, record);
  }

  private void writeActivateChildCommand(final ElementInstance parentElementInstance) {
    final ProcessInstanceRecord childInstanceRecord =
        createChildInstanceRecord(parentElementInstance);

    commandWriter.appendFollowUpCommand(
        keyGenerator.nextKey(), ProcessInstanceIntent.ACTIVATE_ELEMENT, childInstanceRecord);
  }

  private void writeNextBatchCommand(
      final long remainingChildrenToActivate,
      final TypedRecord<ProcessInstanceBatchRecord> record) {
    final var recordValue = record.getValue();
    // schedule the next batch command or finish the batch
    if (remainingChildrenToActivate > 0) {
      writeFollowupBatchCommand(recordValue, remainingChildrenToActivate);
    } else {
      stateWriter.appendFollowUpEvent(
          record.getKey(), ProcessInstanceBatchIntent.ACTIVATED, recordValue);
    }
  }

  private void writeFollowupBatchCommand(
      final ProcessInstanceBatchRecord recordValue, final long remainingChildrenToActivate) {

    final var nextBatchRecord =
        new ProcessInstanceBatchRecord()
            .setProcessInstanceKey(recordValue.getProcessInstanceKey())
            .setBatchElementInstanceKey(recordValue.getBatchElementInstanceKey())
            .setIndex(remainingChildrenToActivate)
            .setProcessDefinitionKey(recordValue.getProcessDefinitionKey());

    commandWriter.appendFollowUpCommand(
        keyGenerator.nextKey(), ProcessInstanceBatchIntent.ACTIVATE, nextBatchRecord);
  }

  private ProcessInstanceRecord createChildInstanceRecord(
      final ElementInstance parentElementInstance) {
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
}
