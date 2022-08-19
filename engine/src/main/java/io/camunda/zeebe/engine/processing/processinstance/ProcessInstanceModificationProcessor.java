/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.ElementActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectQueue;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import java.util.function.Consumer;

public final class ProcessInstanceModificationProcessor
    implements TypedRecordProcessor<ProcessInstanceModificationRecord> {

  private static final String ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND =
      "Expected to modify process instance but no process instance found with key '%d'";

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final BpmnJobBehavior jobBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final CatchEventBehavior catchEventBehavior;
  private final ElementActivationBehavior elementActivationBehavior;

  public ProcessInstanceModificationProcessor(
      final Writers writers,
      final ElementInstanceState elementInstanceState,
      final ProcessState processState,
      final BpmnBehaviors bpmnBehaviors) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
    jobBehavior = bpmnBehaviors.jobBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    catchEventBehavior = bpmnBehaviors.catchEventBehavior();
    elementActivationBehavior = bpmnBehaviors.elementActivationBehavior();
  }

  @Override
  public void processRecord(
      final TypedRecord<ProcessInstanceModificationRecord> command,
      final Consumer<SideEffectProducer> sideEffect) {
    final long commandKey = command.getKey();
    final var value = command.getValue();

    // if set, the command's key should take precedence over the processInstanceKey
    final long eventKey = commandKey > -1 ? commandKey : value.getProcessInstanceKey();

    final ElementInstance processInstance =
        elementInstanceState.getInstance(value.getProcessInstanceKey());

    if (processInstance == null) {
      final String reason = String.format(ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND, eventKey);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, reason);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
      return;
    }

    final var processInstanceRecord = processInstance.getValue();
    final var process =
        processState.getProcessByKey(processInstanceRecord.getProcessDefinitionKey());

    value
        .getActivateInstructions()
        .forEach(
            instruction -> {
              final var elementToActivate =
                  process.getProcess().getElementById(instruction.getElementId());

              // todo: reject if elementToActivate could not be found (#9976)
              elementActivationBehavior.activateElement(processInstanceRecord, elementToActivate);
            });

    value
        .getTerminateInstructions()
        .forEach(instruction -> terminateElement(instruction.getElementInstanceKey(), sideEffect));

    stateWriter.appendFollowUpEvent(eventKey, ProcessInstanceModificationIntent.MODIFIED, value);

    responseWriter.writeEventOnCommand(
        eventKey, ProcessInstanceModificationIntent.MODIFIED, value, command);
  }

  private void terminateElement(
      final long elementInstanceKey, final Consumer<SideEffectProducer> sideEffect) {
    // todo: deal with non-existing element instance (#9983)

    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    final var elementInstanceRecord = elementInstance.getValue();

    stateWriter.appendFollowUpEvent(
        elementInstanceKey, ProcessInstanceIntent.ELEMENT_TERMINATING, elementInstanceRecord);

    jobBehavior.cancelJob(elementInstance);
    incidentBehavior.resolveIncidents(elementInstanceKey);

    final var sideEffectQueue = new SideEffectQueue();
    catchEventBehavior.unsubscribeFromEvents(elementInstanceKey, sideEffectQueue);
    sideEffect.accept(sideEffectQueue);

    stateWriter.appendFollowUpEvent(
        elementInstanceKey, ProcessInstanceIntent.ELEMENT_TERMINATED, elementInstanceRecord);
  }
}
