/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.ElementActivationBehavior;
import io.camunda.zeebe.engine.processing.common.EventSubscriptionException;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor.ProcessingError;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public final class CreateProcessInstanceProcessor
    implements CommandProcessor<ProcessInstanceCreationRecord> {

  private static final String ERROR_MESSAGE_NO_IDENTIFIER_SPECIFIED =
      "Expected at least a bpmnProcessId or a key greater than -1, but none given";
  private static final String ERROR_MESSAGE_NOT_FOUND_BY_PROCESS =
      "Expected to find process definition with process ID '%s', but none found";
  private static final String ERROR_MESSAGE_NOT_FOUND_BY_PROCESS_AND_VERSION =
      "Expected to find process definition with process ID '%s' and version '%d', but none found";
  private static final String ERROR_MESSAGE_NOT_FOUND_BY_KEY =
      "Expected to find process definition with key '%d', but none found";
  private static final String ERROR_MESSAGE_NO_NONE_START_EVENT =
      "Expected to create instance of process with none start event, but there is no such event";

  private static final Either<Rejection, Object> VALID = Either.right(null);

  private static final Set<BpmnElementType> UNSUPPORTED_ELEMENT_TYPES =
      Set.of(
          BpmnElementType.START_EVENT,
          BpmnElementType.SEQUENCE_FLOW,
          BpmnElementType.BOUNDARY_EVENT,
          BpmnElementType.UNSPECIFIED);

  private final ProcessInstanceRecord newProcessInstance = new ProcessInstanceRecord();

  private final ProcessState processState;
  private final VariableBehavior variableBehavior;

  private final KeyGenerator keyGenerator;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  private final ProcessEngineMetrics metrics;

  private final ElementActivationBehavior elementActivationBehavior;

  public CreateProcessInstanceProcessor(
      final ProcessState processState,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final BpmnBehaviors bpmnBehaviors,
      final ProcessEngineMetrics metrics) {
    this.processState = processState;
    variableBehavior = bpmnBehaviors.variableBehavior();
    this.keyGenerator = keyGenerator;
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.metrics = metrics;
    elementActivationBehavior = bpmnBehaviors.elementActivationBehavior();
  }

  @Override
  public boolean onCommand(
      final TypedRecord<ProcessInstanceCreationRecord> command,
      final CommandControl<ProcessInstanceCreationRecord> controller) {

    final ProcessInstanceCreationRecord record = command.getValue();

    getProcess(record)
        .flatMap(process -> validateCommand(command.getValue(), process))
        .ifRightOrLeft(
            process -> createProcessInstance(controller, record, process),
            rejection -> controller.reject(rejection.type, rejection.reason));

    return true;
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ProcessInstanceCreationRecord> typedCommand, final Throwable error) {
    if (error instanceof EventSubscriptionException exception) {
      // This exception is only thrown for ProcessInstanceCreationRecord with start instructions
      rejectionWriter.appendRejection(
          typedCommand, RejectionType.INVALID_ARGUMENT, exception.getMessage());
      responseWriter.writeRejectionOnCommand(
          typedCommand, RejectionType.INVALID_ARGUMENT, exception.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    }
    return ProcessingError.UNEXPECTED_ERROR;
  }

  private void createProcessInstance(
      final CommandControl<ProcessInstanceCreationRecord> controller,
      final ProcessInstanceCreationRecord record,
      final DeployedProcess process) {
    final long processInstanceKey = keyGenerator.nextKey();

    setVariablesFromDocument(
        record, process.getKey(), processInstanceKey, process.getBpmnProcessId());

    final var processInstance = initProcessInstanceRecord(process, processInstanceKey);
    if (record.startInstructions().isEmpty()) {
      commandWriter.appendFollowUpCommand(
          processInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, processInstance);
    } else {
      activateElementsForStartInstructions(record.startInstructions(), process, processInstance);
    }

    record
        .setProcessInstanceKey(processInstanceKey)
        .setBpmnProcessId(process.getBpmnProcessId())
        .setVersion(process.getVersion())
        .setProcessDefinitionKey(process.getKey());
    controller.accept(ProcessInstanceCreationIntent.CREATED, record);

    metrics.processInstanceCreated(record);
  }

  private Either<Rejection, DeployedProcess> validateCommand(
      final ProcessInstanceCreationRecord command, final DeployedProcess deployedProcess) {
    final var process = deployedProcess.getProcess();
    final var startInstructions = command.startInstructions();

    return validateHasNoneStartEventOrStartInstructions(process, startInstructions)
        .flatMap(valid -> validateElementsExist(process, startInstructions))
        .flatMap(valid -> validateElementsNotInsideMultiInstance(process, startInstructions))
        .flatMap(valid -> validateTargetsSupportedElementType(process, startInstructions))
        .flatMap(
            valid -> validateElementNotBelongingToEventBasedGateway(process, startInstructions))
        .map(valid -> deployedProcess);
  }

  private Either<Rejection, ?> validateHasNoneStartEventOrStartInstructions(
      final ExecutableProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {

    if (process.getNoneStartEvent() != null || !startInstructions.isEmpty()) {
      return VALID;
    } else {
      return Either.left(
          new Rejection(RejectionType.INVALID_STATE, ERROR_MESSAGE_NO_NONE_START_EVENT));
    }
  }

  private Either<Rejection, ?> validateElementsExist(
      final ExecutableProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {

    return startInstructions.stream()
        .map(ProcessInstanceCreationStartInstruction::getElementId)
        .filter(elementId -> !isElementOfProcess(process, elementId))
        .findAny()
        .map(
            elementId ->
                Either.left(
                    new Rejection(
                        RejectionType.INVALID_ARGUMENT,
                        "Expected to create instance of process with start instructions but no element found with id '%s'."
                            .formatted(elementId))))
        .orElse(VALID);
  }

  private boolean isElementOfProcess(final ExecutableProcess process, final String elementId) {
    return process.getElementById(wrapString(elementId)) != null;
  }

  private Either<Rejection, ?> validateElementsNotInsideMultiInstance(
      final ExecutableProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {

    return startInstructions.stream()
        .map(ProcessInstanceCreationStartInstruction::getElementId)
        .filter(elementId -> isElementInsideMultiInstance(process, elementId))
        .findAny()
        .map(
            elementId ->
                Either.left(
                    new Rejection(
                        RejectionType.INVALID_ARGUMENT,
                        "Expected to create instance of process with start instructions but the element with id '%s' is inside a multi-instance subprocess. The creation of elements inside a multi-instance subprocess is not supported."
                            .formatted(elementId))))
        .orElse(VALID);
  }

  private boolean isElementInsideMultiInstance(
      final ExecutableProcess process, final String elementId) {
    final var element = process.getElementById(wrapString(elementId));
    return element != null && hasMultiInstanceScope(element);
  }

  private boolean hasMultiInstanceScope(final ExecutableFlowElement flowElement) {
    final var flowScope = flowElement.getFlowScope();
    if (flowScope == null) {
      return false;
    }

    if (flowScope.getElementType() == BpmnElementType.MULTI_INSTANCE_BODY) {
      return true;
    } else {
      return hasMultiInstanceScope(flowScope);
    }
  }

  private Either<Rejection, ?> validateTargetsSupportedElementType(
      final ExecutableProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {

    return startInstructions.stream()
        .map(
            instruction ->
                new ElementIdAndType(
                    instruction.getElementId(),
                    process.getElementById(instruction.getElementIdBuffer()).getElementType()))
        .filter(
            elementIdAndType -> UNSUPPORTED_ELEMENT_TYPES.contains(elementIdAndType.elementType))
        .findAny()
        .map(
            elementIdAndType ->
                Either.left(
                    new Rejection(
                        RejectionType.INVALID_ARGUMENT,
                        ("Expected to create instance of process with start instructions but the element with id '%s' targets unsupported element type '%s'. "
                                + "Supported element types are: %s")
                            .formatted(
                                elementIdAndType.elementId,
                                elementIdAndType.elementType,
                                Arrays.stream(BpmnElementType.values())
                                    .filter(
                                        elementType ->
                                            !UNSUPPORTED_ELEMENT_TYPES.contains(elementType))
                                    .collect(Collectors.toSet())))))
        .orElse(VALID);
  }

  private Either<Rejection, ?> validateElementNotBelongingToEventBasedGateway(
      final ExecutableProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {

    return startInstructions.stream()
        .map(ProcessInstanceCreationStartInstruction::getElementId)
        .filter(elementId -> doesElementBelongToAnEventBasedGateway(process, elementId))
        .findAny()
        .map(
            elementId ->
                Either.left(
                    new Rejection(
                        RejectionType.INVALID_ARGUMENT,
                        "Expected to create instance of process with start instructions but the element with id '%s' belongs to an event-based gateway. The creation of elements belonging to an event-based gateway is not supported."
                            .formatted(elementId))))
        .orElse(VALID);
  }

  private boolean doesElementBelongToAnEventBasedGateway(
      final ExecutableProcess process, final String elementId) {
    final ExecutableFlowNode element = process.getElementById(elementId, ExecutableFlowNode.class);
    return element.getIncoming().stream()
        .map(ExecutableSequenceFlow::getSource)
        .anyMatch(
            flowNode -> flowNode.getElementType().equals(BpmnElementType.EVENT_BASED_GATEWAY));
  }

  private void setVariablesFromDocument(
      final ProcessInstanceCreationRecord record,
      final long processDefinitionKey,
      final long processInstanceKey,
      final DirectBuffer bpmnProcessId) {

    variableBehavior.initLocalDocument(
        processInstanceKey,
        processDefinitionKey,
        processInstanceKey,
        bpmnProcessId,
        record.getVariablesBuffer());
  }

  private ProcessInstanceRecord initProcessInstanceRecord(
      final DeployedProcess process, final long processInstanceKey) {
    newProcessInstance.reset();
    newProcessInstance.setBpmnProcessId(process.getBpmnProcessId());
    newProcessInstance.setVersion(process.getVersion());
    newProcessInstance.setProcessDefinitionKey(process.getKey());
    newProcessInstance.setProcessInstanceKey(processInstanceKey);
    newProcessInstance.setBpmnElementType(BpmnElementType.PROCESS);
    newProcessInstance.setElementId(process.getProcess().getId());
    newProcessInstance.setFlowScopeKey(-1);
    return newProcessInstance;
  }

  private Either<Rejection, DeployedProcess> getProcess(
      final ProcessInstanceCreationRecord record) {
    final DirectBuffer bpmnProcessId = record.getBpmnProcessIdBuffer();

    if (bpmnProcessId.capacity() > 0) {
      if (record.getVersion() >= 0) {
        return getProcess(bpmnProcessId, record.getVersion());
      } else {
        return getProcess(bpmnProcessId);
      }
    } else if (record.getProcessDefinitionKey() >= 0) {
      return getProcess(record.getProcessDefinitionKey());
    } else {
      return Either.left(
          new Rejection(RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_NO_IDENTIFIER_SPECIFIED));
    }
  }

  private Either<Rejection, DeployedProcess> getProcess(final DirectBuffer bpmnProcessId) {
    final DeployedProcess process = processState.getLatestProcessVersionByProcessId(bpmnProcessId);
    if (process != null) {
      return Either.right(process);
    } else {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND,
              String.format(ERROR_MESSAGE_NOT_FOUND_BY_PROCESS, bufferAsString(bpmnProcessId))));
    }
  }

  private Either<Rejection, DeployedProcess> getProcess(
      final DirectBuffer bpmnProcessId, final int version) {
    final DeployedProcess process =
        processState.getProcessByProcessIdAndVersion(bpmnProcessId, version);
    if (process != null) {
      return Either.right(process);
    } else {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND,
              String.format(
                  ERROR_MESSAGE_NOT_FOUND_BY_PROCESS_AND_VERSION,
                  bufferAsString(bpmnProcessId),
                  version)));
    }
  }

  private Either<Rejection, DeployedProcess> getProcess(final long key) {
    final DeployedProcess process = processState.getProcessByKey(key);
    if (process != null) {
      return Either.right(process);
    } else {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND, String.format(ERROR_MESSAGE_NOT_FOUND_BY_KEY, key)));
    }
  }

  private void activateElementsForStartInstructions(
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions,
      final DeployedProcess process,
      final ProcessInstanceRecord processInstance) {

    startInstructions.forEach(
        instruction -> {
          final var element = process.getProcess().getElementById(instruction.getElementId());
          elementActivationBehavior.activateElement(processInstance, element);
        });
  }

  private record Rejection(RejectionType type, String reason) {}

  private record ElementIdAndType(String elementId, BpmnElementType elementType) {}
}
