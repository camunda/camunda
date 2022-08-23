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
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationVariableInstruction;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationActivateInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.agrona.Strings;

public final class ProcessInstanceModificationProcessor
    implements TypedRecordProcessor<ProcessInstanceModificationRecord> {

  private static final String ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND =
      "Expected to modify process instance but no process instance found with key '%d'";
  private static final String ERROR_MESSAGE_TARGET_ELEMENT_NOT_FOUND =
      "Expected to activate element but no element found in process '%s' for element id(s): '%s'";

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final BpmnJobBehavior jobBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final CatchEventBehavior catchEventBehavior;
  private final ElementActivationBehavior elementActivationBehavior;
  private final VariableBehavior variableBehavior;

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
    variableBehavior = bpmnBehaviors.variableBehavior();
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

    final var optRejection = validateCommand(command, process);
    if (optRejection.isPresent()) {
      final var rejection = optRejection.get();
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      return;
    }

    value
        .getActivateInstructions()
        .forEach(
            instruction -> {
              final var elementToActivate =
                  process.getProcess().getElementById(instruction.getElementId());

              executeGlobalVariableInstructions(processInstance, process, instruction);
              // todo(#9663): execute local variable instructions

              elementActivationBehavior.activateElement(processInstanceRecord, elementToActivate);
            });

    value
        .getTerminateInstructions()
        .forEach(instruction -> terminateElement(instruction.getElementInstanceKey(), sideEffect));

    stateWriter.appendFollowUpEvent(eventKey, ProcessInstanceModificationIntent.MODIFIED, value);

    responseWriter.writeEventOnCommand(
        eventKey, ProcessInstanceModificationIntent.MODIFIED, value, command);
  }

  private Optional<Rejection> validateCommand(
      final TypedRecord<ProcessInstanceModificationRecord> command, final DeployedProcess process) {
    final var value = command.getValue();

    final Set<String> unknownElementIds =
        value.getActivateInstructions().stream()
            .map(ProcessInstanceModificationActivateInstructionValue::getElementId)
            .filter(targetElementId -> process.getProcess().getElementById(targetElementId) == null)
            .collect(Collectors.toSet());
    if (!unknownElementIds.isEmpty()) {
      final String reason =
          String.format(
              ERROR_MESSAGE_TARGET_ELEMENT_NOT_FOUND,
              BufferUtil.bufferAsString(process.getBpmnProcessId()),
              String.join("', '", unknownElementIds));
      return Optional.of(new Rejection(RejectionType.NOT_FOUND, reason));
    }

    return Optional.empty();
  }

  private void executeGlobalVariableInstructions(
      final ElementInstance processInstance,
      final DeployedProcess process,
      final ProcessInstanceModificationActivateInstructionValue activate) {
    final var scopeKey = processInstance.getKey();
    activate.getVariableInstructions().stream()
        .filter(v -> Strings.isEmpty(v.getElementId()))
        .map(
            instruction -> {
              if (instruction instanceof ProcessInstanceModificationVariableInstruction vi) {
                return vi.getVariablesBuffer();
              }
              throw new UnsupportedOperationException(
                  "Expected variable instruction of type %s, but was %s"
                      .formatted(
                          ProcessInstanceModificationActivateInstructionValue.class.getName(),
                          instruction.getClass().getName()));
            })
        .forEach(
            variableDocument ->
                variableBehavior.mergeLocalDocument(
                    scopeKey,
                    process.getKey(),
                    processInstance.getKey(),
                    process.getBpmnProcessId(),
                    variableDocument));
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

  private record Rejection(RejectionType type, String reason) {}
}
