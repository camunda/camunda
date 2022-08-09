/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationActivateInstructionValue;
import java.util.Collection;
import java.util.List;
import org.agrona.DirectBuffer;

public final class ProcessInstanceModificationProcessor
    implements TypedRecordProcessor<ProcessInstanceModificationRecord> {

  private static final String ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND =
      "Expected to modify process instance but no process instance found with key '%d'";

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final BpmnJobBehavior jobBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final TypedRejectionWriter rejectionWriter;

  public ProcessInstanceModificationProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ElementInstanceState elementInstanceState,
      final ProcessState processState,
      final BpmnJobBehavior jobBehavior,
      final BpmnIncidentBehavior incidentBehavior) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
    this.jobBehavior = jobBehavior;
    this.incidentBehavior = incidentBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceModificationRecord> command) {
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

              activateElement(processInstanceRecord, instruction, elementToActivate);
            });

    value
        .getTerminateInstructions()
        .forEach(instruction -> terminateElement(instruction.getElementInstanceKey()));

    stateWriter.appendFollowUpEvent(eventKey, ProcessInstanceModificationIntent.MODIFIED, value);

    responseWriter.writeEventOnCommand(
        eventKey, ProcessInstanceModificationIntent.MODIFIED, value, command);
  }

  private void activateElement(
      final ProcessInstanceRecord processInstance,
      final ProcessInstanceModificationActivateInstructionValue instruction,
      final AbstractFlowElement elementToActivate) {
    final var elementInstanceRecord = new ProcessInstanceRecord();
    elementInstanceRecord.wrap(processInstance);
    // todo: deal with non-existing flow scope (#9643)
    // todo: deal with multiple flow scopes found without ancestor selection (#10008)
    final List<Long> flowScopeKey = findFlowScopeKey(processInstance, elementToActivate);
    commandWriter.appendFollowUpCommand(
        keyGenerator.nextKey(),
        ProcessInstanceIntent.ACTIVATE_ELEMENT,
        elementInstanceRecord
            .setFlowScopeKey(flowScopeKey.get(0))
            .setBpmnElementType(elementToActivate.getElementType())
            .setElementId(instruction.getElementId())
            .setParentProcessInstanceKey(-1)
            .setParentElementInstanceKey(-1));
  }

  private List<Long> findFlowScopeKey(
      final ProcessInstanceRecord processInstance, final AbstractFlowElement elementToActivate) {
    final var flowScope = elementToActivate.getFlowScope();
    if (flowScope.getId().equals(processInstance.getElementIdBuffer())) {
      return List.of(processInstance.getProcessInstanceKey());
    } else {
      return findFlowScopeKey(processInstance.getProcessInstanceKey(), flowScope.getId());
    }
  }

  private List<Long> findFlowScopeKey(final long ancestorKey, final DirectBuffer targetElementId) {
    final List<ElementInstance> children = elementInstanceState.getChildren(ancestorKey);
    final List<Long> matches =
        children.stream()
            .filter(child -> child.getValue().getElementIdBuffer().equals(targetElementId))
            .map(ElementInstance::getKey)
            .toList();

    if (!matches.isEmpty()) {
      return matches;
    }

    return children.stream()
        .map(child -> findFlowScopeKey(child.getKey(), targetElementId))
        .flatMap(Collection::stream)
        .toList();
  }

  private void terminateElement(final long elementInstanceKey) {
    // todo: deal with non-existing element instance (#9983)
    // todo: delete event subscriptions

    final ElementInstance elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    final var elementInstanceRecord = elementInstance.getValue();

    jobBehavior.cancelJob(elementInstance);
    incidentBehavior.resolveIncidents(elementInstanceKey);

    stateWriter.appendFollowUpEvent(
        elementInstanceKey, ProcessInstanceIntent.ELEMENT_TERMINATING, elementInstanceRecord);
    stateWriter.appendFollowUpEvent(
        elementInstanceKey, ProcessInstanceIntent.ELEMENT_TERMINATED, elementInstanceRecord);
  }
}
