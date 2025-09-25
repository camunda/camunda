/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static java.util.function.Predicate.not;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnUserTaskBehavior;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.ElementActivationBehavior;
import io.camunda.zeebe.engine.processing.common.ElementActivationBehavior.ActivatedElementKeys;
import io.camunda.zeebe.engine.processing.common.EventSubscriptionException;
import io.camunda.zeebe.engine.processing.common.MultipleFlowScopeInstancesFoundException;
import io.camunda.zeebe.engine.processing.common.UnsupportedMultiInstanceBodyActivationException;
import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationVariableInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationActivateInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationTerminateInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationVariableInstructionValue;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;
import org.agrona.Strings;

public final class ProcessInstanceModificationModifyProcessor
    implements TypedRecordProcessor<ProcessInstanceModificationRecord> {

  private static final String ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND =
      "Expected to modify process instance but no process instance found with key '%d'";
  private static final String ERROR_MESSAGE_ACTIVATE_ELEMENT_NOT_FOUND =
      "Expected to modify instance of process '%s' but it contains one or more activate instructions"
          + " with an element that could not be found: '%s'";
  private static final String ERROR_MESSAGE_ACTIVATE_ELEMENT_UNSUPPORTED =
      "Expected to modify instance of process '%s' but it contains one or more activate instructions"
          + " for elements that are unsupported: '%s'. %s.";
  private static final String ERROR_MESSAGE_TERMINATE_ELEMENT_INSTANCE_NOT_FOUND =
      "Expected to modify instance of process '%s' but it contains one or more terminate instructions"
          + " with an element instance that could not be found: '%s'";
  private static final String ERROR_COMMAND_TOO_LARGE =
      "Unable to modify process instance with key '%d' as the size exceeds the maximum batch size."
          + " Please reduce the size by splitting the modification into multiple commands.";

  private static final String ERROR_MESSAGE_VARIABLE_SCOPE_NOT_FOUND =
      """
      Expected to modify instance of process '%s' but it contains one or more variable instructions \
      with a scope element id that could not be found: '%s'""";

  private static final String ERROR_MESSAGE_VARIABLE_SCOPE_NOT_FLOW_SCOPE =
      """
      Expected to modify instance of process '%s' but it contains one or more variable instructions \
      with a scope element that doesn't belong to the activating element's flow scope. \
      These variables should be set before or after the modification.""";

  private static final String ERROR_MESSAGE_MORE_THAN_ONE_FLOW_SCOPE_INSTANCE =
      """
      Expected to modify instance of process '%s' but it contains one or more activate instructions \
      for an element that has a flow scope with more than one active instance: '%s'. Can't decide \
      in which instance of the flow scope the element should be activated. Please specify an \
      ancestor element instance key for this activate instruction.""";

  private static final String ERROR_MESSAGE_CHILD_PROCESS_INSTANCE_TERMINATED =
      """
      Expected to modify instance of process '%s' but the given instructions would terminate \
      the instance. The instance was created by a call activity in the parent process. \
      To terminate this instance please modify the parent process instead.""";

  private static final String ERROR_MESSAGE_ANCESTOR_NOT_FOUND =
      """
      Expected to modify instance of process '%s' but it contains one or more activate instructions \
      with an ancestor scope key that does not exist, or is not in an active state: '%s'""";

  private static final String ERROR_MESSAGE_ATTEMPTED_TO_ACTIVATE_MULTI_INSTANCE =
      """
      Expected to modify instance of process '%s' but it contains one or more activate instructions \
      that would result in the activation of multi-instance element '%s', which is currently \
      unsupported.""";

  private static final String ERROR_MESSAGE_ANCESTOR_WRONG_PROCESS_INSTANCE =
      """
      Expected to modify instance of process '%s' but it contains one or more activate \
      instructions with an ancestor scope key that does not belong to the modified process \
      instance: '%s'""";

  private static final String ERROR_MESSAGE_SELECTED_ANCESTOR_IS_NOT_ANCESTOR_OF_ELEMENT =
      """
      Expected to modify instance of process '%s' but it contains one or more activate instructions \
      with an ancestor scope key that is not an ancestor of the element to activate:%s""";

  private static final String ERROR_MESSAGE_PROCESS_INSTANCE_SUSPENDED =
      """
      Expected to modify instance of process '%s' but it is currently suspended. \
      Suspended process instances cannot be modified.""";

  private static final String ERROR_MESSAGE_ACTIVATION_FLOW_SCOPE_TERMINATED =
      """
      Expected to modify instance of process '%s' but it contains one or more activate instructions \
      for elements whose required flow scope instance is also being terminated: %s. \
      Please provide a valid ancestor scope key for the activation or avoid terminating the required flow scope.""";

  private static final String ERROR_MESSAGE_ACTIVATION_FLOW_SCOPE_CONFLICT =
      "element '%s' requires flow scope instance '%d' which is being terminated";

  private static final EnumSet<BpmnElementType> UNSUPPORTED_ELEMENT_TYPES =
      EnumSet.of(
          BpmnElementType.UNSPECIFIED,
          BpmnElementType.START_EVENT,
          BpmnElementType.SEQUENCE_FLOW,
          BpmnElementType.BOUNDARY_EVENT);
  private static final EnumSet<BpmnElementType> SUPPORTED_ELEMENT_TYPES =
      EnumSet.complementOf(UNSUPPORTED_ELEMENT_TYPES);

  private static final Either<Rejection, Object> VALID = Either.right(null);

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
  private final BpmnUserTaskBehavior userTaskBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public ProcessInstanceModificationModifyProcessor(
      final Writers writers,
      final ElementInstanceState elementInstanceState,
      final ProcessState processState,
      final BpmnBehaviors bpmnBehaviors,
      final AuthorizationCheckBehavior authCheckBehavior) {
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
    userTaskBehavior = bpmnBehaviors.userTaskBehavior();
    this.authCheckBehavior = authCheckBehavior;
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

    final var authRequest =
        new AuthorizationRequest(
                command,
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.MODIFY_PROCESS_INSTANCE,
                processInstance.getValue().getTenantId())
            .addResourceId(processInstance.getValue().getBpmnProcessId());
    final var isAuthorized = authCheckBehavior.isAuthorized(authRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      final String errorMessage =
          RejectionType.NOT_FOUND.equals(rejection.type())
              ? AuthorizationCheckBehavior.NOT_FOUND_ERROR_MESSAGE.formatted(
                  "modify a process instance",
                  processInstance.getValue().getProcessInstanceKey(),
                  "such process instance")
              : rejection.reason();
      responseWriter.writeRejectionOnCommand(command, rejection.type(), errorMessage);
      rejectionWriter.appendRejection(command, rejection.type(), errorMessage);
      return;
    }

    final var processInstanceRecord = processInstance.getValue();
    final var process =
        processState.getProcessByKeyAndTenant(
            processInstanceRecord.getProcessDefinitionKey(), processInstanceRecord.getTenantId());

    final var validationResult = validateCommand(command, process, processInstance);
    if (validationResult.isLeft()) {
      final var rejection = validationResult.getLeft();
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      return;
    }

    final var extendedRecord = new ProcessInstanceModificationRecord();
    extendedRecord.setProcessInstanceKey(value.getProcessInstanceKey());

    final var requiredKeysForActivation =
        value.getActivateInstructions().stream()
            .flatMap(
                instruction -> {
                  final var elementToActivate =
                      process.getProcess().getElementById(instruction.getElementId());
                  final var ancestorScopeKey = instruction.getAncestorScopeKey();

                  final ActivatedElementKeys activatedElementKeys =
                      elementActivationBehavior.activateElement(
                          processInstanceRecord,
                          elementToActivate,
                          ancestorScopeKey,
                          (elementId, scopeKey) ->
                              executeVariableInstruction(
                                  BufferUtil.bufferAsString(elementId),
                                  scopeKey,
                                  processInstance,
                                  process,
                                  instruction));

                  extendedRecord.addActivateInstruction(
                      ((ProcessInstanceModificationActivateInstruction) instruction)
                          .addAncestorScopeKeys(activatedElementKeys.getFlowScopeKeys()));

                  return activatedElementKeys.getFlowScopeKeys().stream();
                })
            .collect(Collectors.toSet());

    value
        .getTerminateInstructions()
        .forEach(
            instruction -> {
              extendedRecord.addTerminateInstruction(instruction);
              final var elementInstance =
                  elementInstanceState.getInstance(instruction.getElementInstanceKey());
              if (elementInstance == null) {
                // at this point this element instance has already been terminated as a result of
                // one of the previous terminate instructions. As a result we no longer need to
                // terminate it.
                return;
              }
              final var flowScopeKey = elementInstance.getValue().getFlowScopeKey();

              terminateElement(elementInstance);
              terminateFlowScopes(flowScopeKey, requiredKeysForActivation);
            });

    stateWriter.appendFollowUpEvent(
        eventKey, ProcessInstanceModificationIntent.MODIFIED, extendedRecord);

    responseWriter.writeEventOnCommand(
        eventKey, ProcessInstanceModificationIntent.MODIFIED, extendedRecord, command);
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ProcessInstanceModificationRecord> typedCommand, final Throwable error) {
    if (error instanceof final EventSubscriptionException exception) {
      rejectionWriter.appendRejection(
          typedCommand, RejectionType.INVALID_ARGUMENT, exception.getMessage());
      responseWriter.writeRejectionOnCommand(
          typedCommand, RejectionType.INVALID_ARGUMENT, exception.getMessage());
      return ProcessingError.EXPECTED_ERROR;

    } else if (error instanceof final MultipleFlowScopeInstancesFoundException exception) {
      final var rejectionReason =
          ERROR_MESSAGE_MORE_THAN_ONE_FLOW_SCOPE_INSTANCE.formatted(
              exception.getBpmnProcessId(), exception.getFlowScopeId());
      rejectionWriter.appendRejection(
          typedCommand, RejectionType.INVALID_ARGUMENT, rejectionReason);
      responseWriter.writeRejectionOnCommand(
          typedCommand, RejectionType.INVALID_ARGUMENT, rejectionReason);
      return ProcessingError.EXPECTED_ERROR;

    } else if (error instanceof ExceededBatchRecordSizeException) {
      final var message =
          ERROR_COMMAND_TOO_LARGE.formatted(typedCommand.getValue().getProcessInstanceKey());
      rejectionWriter.appendRejection(typedCommand, RejectionType.INVALID_ARGUMENT, message);
      responseWriter.writeRejectionOnCommand(typedCommand, RejectionType.INVALID_ARGUMENT, message);
      return ProcessingError.EXPECTED_ERROR;

    } else if (error instanceof final TerminatedChildProcessException exception) {
      rejectionWriter.appendRejection(
          typedCommand, RejectionType.INVALID_ARGUMENT, exception.getMessage());
      responseWriter.writeRejectionOnCommand(
          typedCommand, RejectionType.INVALID_ARGUMENT, exception.getMessage());
      return ProcessingError.EXPECTED_ERROR;

    } else if (error instanceof final UnsupportedMultiInstanceBodyActivationException exception) {
      final var message =
          ERROR_MESSAGE_ATTEMPTED_TO_ACTIVATE_MULTI_INSTANCE.formatted(
              exception.getBpmnProcessId(), exception.getMultiInstanceId());
      rejectionWriter.appendRejection(typedCommand, RejectionType.INVALID_ARGUMENT, message);
      responseWriter.writeRejectionOnCommand(typedCommand, RejectionType.INVALID_ARGUMENT, message);
      return ProcessingError.EXPECTED_ERROR;
    }

    return ProcessingError.UNEXPECTED_ERROR;
  }

  private Either<Rejection, ?> validateCommand(
      final TypedRecord<ProcessInstanceModificationRecord> command,
      final DeployedProcess process,
      final ElementInstance processInstance) {
    final var value = command.getValue();
    final var activateInstructions = value.getActivateInstructions();
    final var terminateInstructions = value.getTerminateInstructions();

    return validateElementExists(process, activateInstructions)
        .flatMap(valid -> validateElementSupported(process, activateInstructions))
        .flatMap(valid -> validateElementInstanceExists(process, terminateInstructions))
        .flatMap(valid -> validateVariableScopeExists(process, activateInstructions))
        .flatMap(valid -> validateVariableScopeIsFlowScope(process, activateInstructions))
        .flatMap(valid -> validateAncestorKeys(process, value))
        .flatMap(
            valid ->
                validateFlowScopeNotTerminatedForActivation(
                    processInstance, process, activateInstructions, terminateInstructions))
        .map(valid -> VALID);
  }

  private Either<Rejection, ?> validateElementExists(
      final DeployedProcess process,
      final List<ProcessInstanceModificationActivateInstructionValue> activateInstructions) {
    final Set<String> unknownElementIds =
        activateInstructions.stream()
            .map(ProcessInstanceModificationActivateInstructionValue::getElementId)
            .filter(targetElementId -> process.getProcess().getElementById(targetElementId) == null)
            .collect(Collectors.toSet());

    if (unknownElementIds.isEmpty()) {
      return VALID;
    }

    final String reason =
        String.format(
            ERROR_MESSAGE_ACTIVATE_ELEMENT_NOT_FOUND,
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            String.join("', '", unknownElementIds));
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private Either<Rejection, ?> validateElementSupported(
      final DeployedProcess process,
      final List<ProcessInstanceModificationActivateInstructionValue> activateInstructions) {
    return validateElementsDoNotBelongToEventBasedGateway(process, activateInstructions)
        .flatMap(valid -> validateElementsHaveSupportedType(process, activateInstructions))
        .map(valid -> VALID);
  }

  private static Either<Rejection, ?> validateElementsDoNotBelongToEventBasedGateway(
      final DeployedProcess process,
      final List<ProcessInstanceModificationActivateInstructionValue> activateInstructions) {
    final List<String> elementIdsConnectedToEventBasedGateway =
        activateInstructions.stream()
            .map(ProcessInstanceModificationActivateInstructionValue::getElementId)
            .distinct()
            .filter(
                elementId -> {
                  final var element = process.getProcess().getElementById(elementId);
                  return element instanceof final ExecutableCatchEventElement event
                      && event.isConnectedToEventBasedGateway();
                })
            .toList();

    if (elementIdsConnectedToEventBasedGateway.isEmpty()) {
      return VALID;
    }

    final var reason =
        ERROR_MESSAGE_ACTIVATE_ELEMENT_UNSUPPORTED.formatted(
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            String.join("', '", elementIdsConnectedToEventBasedGateway),
            "The activation of events belonging to an event-based gateway is not supported");
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private Either<Rejection, ?> validateElementsHaveSupportedType(
      final DeployedProcess process,
      final List<ProcessInstanceModificationActivateInstructionValue> activateInstructions) {

    final List<AbstractFlowElement> elementsWithUnsupportedElementType =
        activateInstructions.stream()
            .map(ProcessInstanceModificationActivateInstructionValue::getElementId)
            .distinct()
            .map(elementId -> process.getProcess().getElementById(elementId))
            .filter(element -> UNSUPPORTED_ELEMENT_TYPES.contains(element.getElementType()))
            .toList();

    if (elementsWithUnsupportedElementType.isEmpty()) {
      return VALID;
    }

    final String usedUnsupportedElementIds =
        elementsWithUnsupportedElementType.stream()
            .map(AbstractFlowElement::getId)
            .map(BufferUtil::bufferAsString)
            .collect(Collectors.joining("', '"));
    final String usedUnsupportedElementTypes =
        elementsWithUnsupportedElementType.stream()
            .map(AbstractFlowElement::getElementType)
            .map(Objects::toString)
            .distinct()
            .collect(Collectors.joining("', '"));
    final var reason =
        ERROR_MESSAGE_ACTIVATE_ELEMENT_UNSUPPORTED.formatted(
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            usedUnsupportedElementIds,
            "The activation of elements with type '%s' is not supported. Supported element types are: %s"
                .formatted(usedUnsupportedElementTypes, SUPPORTED_ELEMENT_TYPES));
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private Either<Rejection, ?> validateAncestorKeys(
      final DeployedProcess process, final ProcessInstanceModificationRecord record) {
    final Map<Long, Optional<ElementInstance>> ancestorInstances =
        record.getActivateInstructions().stream()
            .map(ProcessInstanceModificationActivateInstructionValue::getAncestorScopeKey)
            .filter(ancestorKey -> ancestorKey > 0)
            .distinct()
            .collect(
                Collectors.toMap(
                    ancestorKey -> ancestorKey,
                    ancestorKey ->
                        Optional.ofNullable(elementInstanceState.getInstance(ancestorKey))));

    return validateAncestorExistsAndIsActive(process, record, ancestorInstances)
        .flatMap(
            valid -> validateAncestorBelongsToProcessInstance(process, record, ancestorInstances))
        .flatMap(valid -> validateAncestorIsFlowScopeOfElement(process, record, ancestorInstances))
        .map(valid -> VALID);
  }

  private Either<Rejection, ?> validateAncestorExistsAndIsActive(
      final DeployedProcess process,
      final ProcessInstanceModificationRecord record,
      final Map<Long, Optional<ElementInstance>> ancestorInstances) {
    final Set<String> invalidAncestorKeys =
        record.getActivateInstructions().stream()
            .map(ProcessInstanceModificationActivateInstructionValue::getAncestorScopeKey)
            .distinct()
            .filter(ancestorKey -> ancestorKey > 0)
            .filter(
                ancestorKey -> {
                  final var elementInstanceOptional = ancestorInstances.get(ancestorKey);
                  return elementInstanceOptional.isEmpty()
                      || !elementInstanceOptional.get().isActive();
                })
            .map(String::valueOf)
            .collect(Collectors.toSet());

    if (invalidAncestorKeys.isEmpty()) {
      return VALID;
    }

    final String reason =
        String.format(
            ERROR_MESSAGE_ANCESTOR_NOT_FOUND,
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            String.join("', '", invalidAncestorKeys));
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private Either<Rejection, ?> validateAncestorBelongsToProcessInstance(
      final DeployedProcess process,
      final ProcessInstanceModificationRecord record,
      final Map<Long, Optional<ElementInstance>> ancestorInstances) {
    final Set<String> rejectedAncestorKeys =
        ancestorInstances.values().stream()
            .flatMap(Optional::stream)
            .filter(
                ancestorInstance ->
                    ancestorInstance.getValue().getProcessInstanceKey()
                        != record.getProcessInstanceKey())
            .map(ancestorInstance -> String.valueOf(ancestorInstance.getKey()))
            .collect(Collectors.toSet());

    if (rejectedAncestorKeys.isEmpty()) {
      return VALID;
    }

    final String reason =
        String.format(
            ERROR_MESSAGE_ANCESTOR_WRONG_PROCESS_INSTANCE,
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            String.join("', '", rejectedAncestorKeys));
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private Either<Rejection, ?> validateAncestorIsFlowScopeOfElement(
      final DeployedProcess process,
      final ProcessInstanceModificationRecord record,
      final Map<Long, Optional<ElementInstance>> ancestorInstances) {
    record InstructionDetails(long ancestorScopeKey, String ancestorId, String elementId) {}

    final String invalidInstructionMessages =
        record.getActivateInstructions().stream()
            .filter(instruction -> instruction.getAncestorScopeKey() > 0)
            .map(
                instruction -> {
                  final var ancestorId =
                      ancestorInstances
                          .get(instruction.getAncestorScopeKey())
                          .map(ElementInstance::getValue)
                          .map(ProcessInstanceRecord::getElementId)
                          .orElse(null);
                  return new InstructionDetails(
                      instruction.getAncestorScopeKey(), ancestorId, instruction.getElementId());
                })
            // skip missing ancestors (already checked in validateAncestorExistsAndIsActive)
            .filter(details -> details.ancestorId != null)
            .filter(details -> !isAncestorOfElement(process, details.ancestorId, details.elementId))
            .map(
                details ->
                    "%n- instance '%s' of element '%s' is not an ancestor of element '%s'"
                        .formatted(details.ancestorScopeKey, details.ancestorId, details.elementId))
            .collect(Collectors.joining());

    if (invalidInstructionMessages.isEmpty()) {
      return VALID;
    }

    final String reason =
        String.format(
            ERROR_MESSAGE_SELECTED_ANCESTOR_IS_NOT_ANCESTOR_OF_ELEMENT,
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            invalidInstructionMessages);
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private boolean isAncestorOfElement(
      final DeployedProcess process, final String ancestorId, final String elementId) {
    final var potentialDescendant = process.getProcess().getElementById(elementId);
    if (potentialDescendant.getFlowScope() == null) {
      return false;
    }

    final var potentialDescendantsFlowScopeId =
        BufferUtil.bufferAsString(potentialDescendant.getFlowScope().getId());
    if (Objects.equals(ancestorId, potentialDescendantsFlowScopeId)) {
      return true;
    }

    return isAncestorOfElement(process, ancestorId, potentialDescendantsFlowScopeId);
  }

  private Either<Rejection, ?> validateElementInstanceExists(
      final DeployedProcess process,
      final List<ProcessInstanceModificationTerminateInstructionValue> terminateInstructions) {

    final List<Long> unknownElementInstanceKeys =
        terminateInstructions.stream()
            .map(ProcessInstanceModificationTerminateInstructionValue::getElementInstanceKey)
            .distinct()
            .filter(instanceKey -> elementInstanceState.getInstance(instanceKey) == null)
            .toList();

    if (unknownElementInstanceKeys.isEmpty()) {
      return VALID;
    }

    final String reason =
        String.format(
            ERROR_MESSAGE_TERMINATE_ELEMENT_INSTANCE_NOT_FOUND,
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            unknownElementInstanceKeys.stream()
                .map(Objects::toString)
                .collect(Collectors.joining("', '")));
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private Either<Rejection, ?> validateVariableScopeExists(
      final DeployedProcess process,
      final List<ProcessInstanceModificationActivateInstructionValue> activateInstructions) {

    final var unknownScopeElementIds =
        activateInstructions.stream()
            .flatMap(instruction -> instruction.getVariableInstructions().stream())
            .map(ProcessInstanceModificationVariableInstructionValue::getElementId)
            // ignore instructions of global variables (i.e. empty scope id)
            .filter(not(String::isEmpty))
            // filter scope ids that doesn't exist in the process
            .filter(scopeElementId -> process.getProcess().getElementById(scopeElementId) == null)
            .collect(Collectors.toSet());

    if (unknownScopeElementIds.isEmpty()) {
      return VALID;
    }

    final var reason =
        ERROR_MESSAGE_VARIABLE_SCOPE_NOT_FOUND.formatted(
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            String.join("', '", unknownScopeElementIds));
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private Either<Rejection, ?> validateVariableScopeIsFlowScope(
      final DeployedProcess process,
      final List<ProcessInstanceModificationActivateInstructionValue> activateInstructions) {

    final var nonFlowScopeIds =
        activateInstructions.stream()
            .flatMap(
                instruction -> {
                  final var elementId = instruction.getElementId();
                  final var elementToActivate = process.getProcess().getElementById(elementId);

                  return instruction.getVariableInstructions().stream()
                      .map(ProcessInstanceModificationVariableInstructionValue::getElementId)
                      // ignore instructions of global variables (i.e. empty scope id)
                      .filter(not(String::isEmpty))
                      // ignore instructions of the activation element
                      .filter(not(elementId::equals))
                      // filter element ids that are not a flow scope of the element
                      .filter(
                          scopeElementId ->
                              !isFlowScopeOfElement(elementToActivate, scopeElementId));
                })
            .collect(Collectors.toSet());

    if (nonFlowScopeIds.isEmpty()) {
      return VALID;
    }

    final var reason =
        ERROR_MESSAGE_VARIABLE_SCOPE_NOT_FLOW_SCOPE.formatted(
            BufferUtil.bufferAsString(process.getBpmnProcessId()));
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private boolean isFlowScopeOfElement(
      final ExecutableFlowElement element, final String targetElementId) {
    // iterate over the flow scopes of the element until reaching the given element id
    var flowScope = element.getFlowScope();
    while (flowScope != null) {
      final String flowScopeId = BufferUtil.bufferAsString(flowScope.getId());
      if (flowScopeId.equals(targetElementId)) {
        return true;
      }
      flowScope = flowScope.getFlowScope();
    }

    return false;
  }

  private Either<Rejection, ?> validateFlowScopeNotTerminatedForActivation(
      final ElementInstance processInstance,
      final DeployedProcess process,
      final List<ProcessInstanceModificationActivateInstructionValue> activateInstructions,
      final List<ProcessInstanceModificationTerminateInstructionValue> terminateInstructions) {
    // Collect all terminate instance keys
    final Set<Long> terminatedInstanceKeys =
        terminateInstructions.stream()
            .map(ProcessInstanceModificationTerminateInstructionValue::getElementInstanceKey)
            .collect(Collectors.toSet());

    // For each activation, determine required flow scope instance key
    final List<String> conflictingActivations = new ArrayList<>();
    for (final var instruction : activateInstructions) {
      final var elementToActivate = process.getProcess().getElementById(instruction.getElementId());
      if (elementToActivate == null) {
        continue; // Already validated elsewhere
      }
      final long ancestorScopeKey = instruction.getAncestorScopeKey();
      final long requiredFlowScopeKey;
      if (ancestorScopeKey > 0) {
        requiredFlowScopeKey = ancestorScopeKey;
      } else {
        // Find the active instance of the flow scope for this element
        final var flowScope = elementToActivate.getFlowScope();
        if (flowScope == null) {
          continue; // No flow scope required
        }
        final var flowScopeInstance =
            elementInstanceState.getChildren(processInstance.getKey()).stream()
                .filter(
                    ei ->
                        BufferUtil.bufferAsString(flowScope.getId())
                            .equals(ei.getValue().getElementId()))
                .findFirst()
                .orElse(null);
        if (flowScopeInstance == null) {
          continue; // No active flow scope instance found
        }
        requiredFlowScopeKey = flowScopeInstance.getKey();
      }
      if (terminatedInstanceKeys.contains(requiredFlowScopeKey)) {
        conflictingActivations.add(
            String.format(
                ERROR_MESSAGE_ACTIVATION_FLOW_SCOPE_CONFLICT,
                instruction.getElementId(),
                requiredFlowScopeKey));
      }
    }
    if (conflictingActivations.isEmpty()) {
      return VALID;
    }
    final String reason =
        String.format(
            ERROR_MESSAGE_ACTIVATION_FLOW_SCOPE_TERMINATED,
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            String.join(", ", conflictingActivations));
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  public void executeVariableInstruction(
      final String elementId,
      final Long scopeKey,
      final ElementInstance processInstance,
      final DeployedProcess process,
      final ProcessInstanceModificationActivateInstructionValue activate) {
    activate.getVariableInstructions().stream()
        .filter(
            instruction ->
                instruction.getElementId().equals(elementId)
                    || (Strings.isEmpty(instruction.getElementId())
                        && elementId.equals(processInstance.getValue().getBpmnProcessId())))
        .map(
            instruction -> {
              if (instruction instanceof final ProcessInstanceModificationVariableInstruction vi) {
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
                    process.getTenantId(),
                    variableDocument));
  }

  private void terminateElement(final ElementInstance elementInstance) {
    final var elementsTerminating = startTerminatingElementAndChildren(elementInstance);
    terminateElements(elementsTerminating);
  }

  private Stack<ElementInstance> startTerminatingElementAndChildren(
      final ElementInstance elementInstance) {
    final var elementInstancesToTerminate = new Stack<ElementInstance>();
    final var elementInstancesTerminating = new Stack<ElementInstance>();
    elementInstancesTerminating.push(elementInstance);

    while (!elementInstancesTerminating.isEmpty()) {
      final var currentElement = elementInstancesTerminating.pop();
      final var elementInstanceKey = currentElement.getKey();
      final var elementInstanceRecord = currentElement.getValue();
      final BpmnElementType elementType = currentElement.getValue().getBpmnElementType();

      stateWriter.appendFollowUpEvent(
          elementInstanceKey, ProcessInstanceIntent.ELEMENT_TERMINATING, elementInstanceRecord);
      elementInstancesToTerminate.push(currentElement);

      jobBehavior.cancelJob(currentElement);
      userTaskBehavior.cancelUserTask(currentElement);
      incidentBehavior.resolveIncidents(elementInstanceKey);
      catchEventBehavior.unsubscribeFromEvents(elementInstanceKey);

      elementInstancesTerminating.addAll(
          getChildInstances(elementType, elementInstanceKey, currentElement));
    }
    return elementInstancesToTerminate;
  }

  private List<ElementInstance> getChildInstances(
      final BpmnElementType elementType,
      final long elementInstanceKey,
      final ElementInstance currentElement) {
    final var childInstances = new ArrayList<ElementInstance>();
    // terminate all child instances if the element is an event subprocess
    if (elementType == BpmnElementType.EVENT_SUB_PROCESS
        || elementType == BpmnElementType.SUB_PROCESS
        || elementType == BpmnElementType.PROCESS
        || elementType == BpmnElementType.MULTI_INSTANCE_BODY) {
      elementInstanceState.getChildren(elementInstanceKey).stream()
          .filter(ElementInstance::canTerminate)
          .forEach(childInstances::add);
    } else if (elementType == BpmnElementType.CALL_ACTIVITY) {
      final var calledActivityElementInstance =
          elementInstanceState.getInstance(currentElement.getCalledChildInstanceKey());
      if (calledActivityElementInstance != null && calledActivityElementInstance.canTerminate()) {
        childInstances.add(calledActivityElementInstance);
      }
    }
    return childInstances;
  }

  private void terminateElements(final Stack<ElementInstance> elementsTerminating) {
    while (!elementsTerminating.isEmpty()) {
      final var currentElement = elementsTerminating.pop();
      stateWriter.appendFollowUpEvent(
          currentElement.getKey(),
          ProcessInstanceIntent.ELEMENT_TERMINATED,
          currentElement.getValue());
    }
  }

  private void terminateFlowScopes(
      final long elementInstanceKey, final Set<Long> requiredKeysForActivation) {
    var currentElementInstance = elementInstanceState.getInstance(elementInstanceKey);

    while (canTerminateElementInstance(currentElementInstance, requiredKeysForActivation)) {

      // Reject the command by throwing an exception if the process is being terminated, but it was
      // started by a call activity.
      final var currentElementInstanceRecord = currentElementInstance.getValue();
      if (currentElementInstanceRecord.getBpmnElementType() == BpmnElementType.PROCESS
          && currentElementInstanceRecord.hasParentProcess()) {
        throw new TerminatedChildProcessException(
            ERROR_MESSAGE_CHILD_PROCESS_INSTANCE_TERMINATED.formatted(
                currentElementInstanceRecord.getBpmnProcessId()));
      }

      final var flowScopeKey = currentElementInstance.getValue().getFlowScopeKey();

      terminateElement(currentElementInstance);

      currentElementInstance = elementInstanceState.getInstance(flowScopeKey);
    }
  }

  private boolean canTerminateElementInstance(
      final ElementInstance elementInstance, final Set<Long> requiredKeysForActivation) {
    return elementInstance != null
        // if it has no active element instances
        && elementInstance.getNumberOfActiveElementInstances() == 0
        // and no pending element activations (i.e. activate command is written but not processed)
        && elementInstance.getActiveSequenceFlows() == 0
        // no activate instruction requires this element instance
        && !requiredKeysForActivation.contains(elementInstance.getKey());
  }

  /**
   * Exception that can be thrown when child instance is being modified. If all active element
   * instances of this process are being terminated this exception is thrown. The reason for this is
   * that it's unclear what the expected behavior of the parent process would be in these cases.
   * Terminating the parent process could be unintended. Creating an incident is an alternative, but
   * there would be no way to resolve this incident.
   *
   * <p>In order to terminate the child process a modification should be performed on the parent
   * process instead.
   */
  private static class TerminatedChildProcessException extends RuntimeException {
    TerminatedChildProcessException(final String message) {
      super(message);
    }
  }
}
