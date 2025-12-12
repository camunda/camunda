/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.Either;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class ProcessInstanceCreationHelper {
  private static final String ERROR_MESSAGE_NO_NONE_START_EVENT =
      "Expected to create instance of process with none start event, but there is no such event";
  private static final Set<BpmnElementType> UNSUPPORTED_ELEMENT_TYPES =
      Set.of(
          BpmnElementType.START_EVENT,
          BpmnElementType.SEQUENCE_FLOW,
          BpmnElementType.BOUNDARY_EVENT,
          BpmnElementType.UNSPECIFIED);
  private static final Either<Rejection, Object> VALID = Either.right(null);

  public ProcessInstanceCreationHelper() {}

  public Either<Rejection, DeployedProcess> validateCommand(
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

  public record Rejection(RejectionType type, String reason) {}

  private record ElementIdAndType(String elementId, BpmnElementType elementType) {}
}
