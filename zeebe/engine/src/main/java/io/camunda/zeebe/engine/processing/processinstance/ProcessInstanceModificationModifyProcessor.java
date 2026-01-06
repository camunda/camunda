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
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
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
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationVariableInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationActivateInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationMoveInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationTerminateInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationVariableInstructionValue;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
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
  private static final String ERROR_MESSAGE_TERMINATE_NO_DEFINITIONS =
      "Expected to modify instance of process '%s' but it contains one or more terminate instructions"
          + " with neither an element instance key nor element id: '%s'";
  private static final String ERROR_MESSAGE_TERMINATE_MULTIPLE_DEFINITIONS =
      "Expected to modify instance of process '%s' but it contains one or more terminate instructions"
          + " with both element instance key and element id, but only one of them is allowed: '%s'";
  private static final String ERROR_MESSAGE_MOVE_NO_DEFINITIONS =
      "Expected to modify instance of process '%s' but it contains one or more move instructions"
          + " with either or both the source or target element id missing: '%s'";
  private static final String ERROR_MESSAGE_MOVE_DUPLICATE_DEFINITIONS =
      "Expected to modify instance of process '%s' but it contains multiple move instructions"
          + " with identical source element ids: '%s'";
  private static final String ERROR_MESSAGE_MOVE_MULTIPLE_DEFINITIONS =
      "Expected to modify instance of process '%s' but it contains one or more move instructions"
          + " with both source element instance key and source element id, but only one of them is allowed: '%s'";
  private static final String ERROR_MESSAGE_MOVE_SOURCE_ELEMENT_INSTANCE_NOT_FOUND =
      "Expected to modify instance of process '%s' but it contains one or more move instructions"
          + " with a source element instance that could not be found: '%s'";
  private static final String ERROR_MESSAGE_MOVE_SOURCE_ELEMENT_INSTANCE_WRONG_PROCESS =
      "Expected to modify instance of process '%s' but it contains one or more move instructions"
          + " with a source element instance that does not belong to the modified process instance: '%s'";
  private static final String ERROR_MESSAGE_MOVE_AMBIGUOUS_ANCESTOR_SCOPE =
      "Expected to modify instance of process '%s' but it contains one or more move instructions"
          + " with multiple ancestor scope options set. Only one of the following can be specified: "
          + "ancestorScopeKey, inferAncestorScopeFromSourceHierarchy, or useSourceParentKeyAsAncestorScopeKey: '%s'";
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
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.MODIFY_PROCESS_INSTANCE)
            .tenantId(processInstance.getValue().getTenantId())
            .addResourceId(processInstance.getValue().getBpmnProcessId())
            .build();
    final var isAuthorized = authCheckBehavior.isAuthorizedOrInternalCommand(authRequest);
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

    final List<ProcessInstanceModificationMoveInstructionValue> moveInstructions =
        value.getMoveInstructions();
    final List<ProcessInstanceModificationTerminateInstructionValue> terminateInstructionsInput =
        value.getTerminateInstructions();
    final var commandValidationResult =
        validateCommand(terminateInstructionsInput, moveInstructions, process);
    if (commandValidationResult.isLeft()) {
      final var rejection = commandValidationResult.getLeft();
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      return;
    }

    final var activateInstructions = new ArrayList<>(value.getActivateInstructions());
    final var terminateInstructions =
        new ArrayList<ProcessInstanceModificationTerminateInstructionValue>();

    // Handle move instructions by source element instance key directly
    final var moveInstructionsByInstanceKey =
        moveInstructions.stream()
            .filter(instruction -> instruction.getSourceElementInstanceKey() > 0)
            .toList();
    final var moveByKeyValidationResult =
        validateMoveSourceElementInstanceExists(
            moveInstructionsByInstanceKey,
            process.getBpmnProcessId(),
            value.getProcessInstanceKey());
    if (moveByKeyValidationResult.isLeft()) {
      final var rejection = moveByKeyValidationResult.getLeft();
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      return;
    }
    mapMoveInstructionsByInstanceKey(
        moveInstructionsByInstanceKey, activateInstructions, terminateInstructions, process);

    // collect move instructions by source element id
    final var moveInstructionsByElementId =
        moveInstructions.stream()
            .filter(instruction -> !instruction.getSourceElementId().isBlank())
            .collect(
                Collectors.toMap(
                    ProcessInstanceModificationMoveInstructionValue::getSourceElementId,
                    Function.identity()));
    mapIdInstructions(
        value.getProcessInstanceKey(),
        moveInstructionsByElementId,
        terminateInstructionsInput,
        activateInstructions,
        terminateInstructions,
        process);

    final var instructionsValidationResult =
        validateInstructions(
            activateInstructions, terminateInstructions, process, value.getProcessInstanceKey());
    if (instructionsValidationResult.isLeft()) {
      final var rejection = instructionsValidationResult.getLeft();
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      return;
    }

    final var extendedRecord = new ProcessInstanceModificationRecord();
    extendedRecord
        .setProcessInstanceKey(value.getProcessInstanceKey())
        .setTenantId(processInstance.getValue().getTenantId());

    final var requiredKeysForActivation =
        activateInstructions.stream()
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

    terminateInstructions.forEach(
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

  private void mapIdInstructions(
      final long processInstanceKey,
      final Map<String, ProcessInstanceModificationMoveInstructionValue> moveInstructions,
      final List<ProcessInstanceModificationTerminateInstructionValue> terminateInstructionsInput,
      final List<ProcessInstanceModificationActivateInstructionValue> finalActivateInstructions,
      final List<ProcessInstanceModificationTerminateInstructionValue> finalTerminateInstructions,
      final DeployedProcess process) {
    if (moveInstructions.isEmpty() && terminateInstructionsInput.isEmpty()) {
      return;
    }

    // collect terminate-by-id instructions, add key instructions to final list
    final var terminateInstructionIds = new HashSet<String>();
    final var terminateInstanceKeys = new HashSet<Long>();
    terminateInstructionsInput.forEach(
        instruction -> {
          if (instruction.getElementInstanceKey() > 0) {
            finalTerminateInstructions.add(instruction);
            terminateInstanceKeys.add(instruction.getElementInstanceKey());
          } else {
            terminateInstructionIds.add(instruction.getElementId());
          }
        });

    // iterate over all active element instances, including child instances
    // use a queue to iterate over the descendants, recursion might lead to StackOverflow
    if (!moveInstructions.isEmpty() || !terminateInstructionIds.isEmpty()) {
      final var elementInstances =
          new ArrayDeque<>(elementInstanceState.getChildren(processInstanceKey));
      while (!elementInstances.isEmpty()) {
        final var elementInstance = elementInstances.poll();
        final var elementId = elementInstance.getValue().getElementId();
        var elementTerminated = false;
        // move element instance
        if (moveInstructions.containsKey(elementId)) {
          final var moveInstruction = moveInstructions.get(elementId);
          final var ancestorScopeKey =
              determineAncestorScopeKey(moveInstruction, elementInstance.getParentKey(), process);
          final var activateInstruction =
              new ProcessInstanceModificationActivateInstruction()
                  .setElementId(moveInstruction.getTargetElementId())
                  .setAncestorScopeKey(ancestorScopeKey);
          moveInstruction
              .getVariableInstructions()
              .forEach(
                  vi ->
                      activateInstruction.addVariableInstruction(
                          (ProcessInstanceModificationVariableInstruction) vi));
          finalActivateInstructions.add(activateInstruction);
          // terminate source element instance
          finalTerminateInstructions.add(
              new ProcessInstanceModificationTerminateInstruction()
                  .setElementInstanceKey(elementInstance.getKey()));
          elementTerminated = true;
        }
        // terminate element instance
        if (terminateInstructionIds.contains(elementId) && !elementTerminated) {
          finalTerminateInstructions.add(
              new ProcessInstanceModificationTerminateInstruction()
                  .setElementInstanceKey(elementInstance.getKey()));
          elementTerminated = true;
        }
        /*
         * Don't handle children explicitly anymore when the parent is terminated. The child
         * elements are terminated by the stream processor automatically, so we don't need to handle
         * them any further.
         */
        if (!elementTerminated && !terminateInstanceKeys.contains(elementInstance.getKey())) {
          elementInstances.addAll(elementInstanceState.getChildren(elementInstance.getKey()));
        }
      }
    }
  }

  private void mapMoveInstructionsByInstanceKey(
      final List<ProcessInstanceModificationMoveInstructionValue> moveInstructions,
      final List<ProcessInstanceModificationActivateInstructionValue> finalActivateInstructions,
      final List<ProcessInstanceModificationTerminateInstructionValue> finalTerminateInstructions,
      final DeployedProcess process) {
    for (final var moveInstruction : moveInstructions) {
      final var elementInstance =
          elementInstanceState.getInstance(moveInstruction.getSourceElementInstanceKey());

      final var ancestorScopeKey =
          determineAncestorScopeKey(moveInstruction, elementInstance.getParentKey(), process);

      final var activateInstruction =
          new ProcessInstanceModificationActivateInstruction()
              .setElementId(moveInstruction.getTargetElementId())
              .setAncestorScopeKey(ancestorScopeKey);
      moveInstruction
          .getVariableInstructions()
          .forEach(
              vi ->
                  activateInstruction.addVariableInstruction(
                      (ProcessInstanceModificationVariableInstruction) vi));
      finalActivateInstructions.add(activateInstruction);
      // terminate source element instance
      finalTerminateInstructions.add(
          new ProcessInstanceModificationTerminateInstruction()
              .setElementId(elementInstance.getValue().getElementId())
              .setElementInstanceKey(moveInstruction.getSourceElementInstanceKey()));
    }
  }

  /**
   * Determines the ancestor scope key for a move instruction based on its configuration.
   *
   * @param moveInstruction the move instruction with ancestor scope configuration
   * @param sourceParentKey the parent key of the source element instance
   * @param process the deployed process containing the elements
   * @return the determined ancestor scope key
   */
  private long determineAncestorScopeKey(
      final ProcessInstanceModificationMoveInstructionValue moveInstruction,
      final long sourceParentKey,
      final DeployedProcess process) {
    if (moveInstruction.isUseSourceParentKeyAsAncestorScopeKey()) {
      return sourceParentKey;
    } else if (moveInstruction.isInferAncestorScopeFromSourceHierarchy()) {
      return findProperAncestorScopeKeyForTarget(
          sourceParentKey, moveInstruction.getTargetElementId(), process);
    } else {
      return moveInstruction.getAncestorScopeKey();
    }
  }

  /**
   * Finds the proper ancestor scope key for activating the target element when moving from a source
   * element instance.
   *
   * <p>When moving from a deeply nested element (e.g., task1 inside subprocess C which is inside
   * subprocess B) to a less nested element (e.g., task2 directly inside subprocess B), we cannot
   * simply use the source's parent key as the ancestor scope. The source's parent would be
   * subprocess C's instance, but the target element's flow scope is subprocess B.
   *
   * <p>This method traverses up the source element instance's ancestry to find an instance whose
   * element ID matches one of the target element's ancestor flow scopes (or the target's direct
   * flow scope).
   *
   * @param sourceParentKey the parent key of the source element instance
   * @param targetElementId the ID of the target element to activate
   * @param process the deployed process containing both elements
   * @return the proper ancestor scope key, or -1 if no matching ancestor is found (will be handled
   *     by the activation logic)
   */
  private long findProperAncestorScopeKeyForTarget(
      final long sourceParentKey, final String targetElementId, final DeployedProcess process) {

    final var targetElement = process.getProcess().getElementById(targetElementId);
    if (targetElement == null) {
      // Target element not found - let validation handle this
      return sourceParentKey;
    }

    final var targetDirectFlowScope = targetElement.getFlowScope();
    if (targetDirectFlowScope == null) {
      // Target is at root level (process) - no ancestor scope needed
      return ElementActivationBehavior.NO_ANCESTOR_SCOPE_KEY;
    }

    // Fast path: check if source parent is already the target's direct flow scope
    // This is the common case when moving between sibling elements
    final var sourceParentInstance = elementInstanceState.getInstance(sourceParentKey);
    if (sourceParentInstance != null) {
      final var sourceParentElementId = sourceParentInstance.getValue().getElementId();
      final var targetDirectFlowScopeId = BufferUtil.bufferAsString(targetDirectFlowScope.getId());
      if (sourceParentElementId.equals(targetDirectFlowScopeId)) {
        return sourceParentKey;
      }
    }

    // Slow path: collect all flow scope element IDs for the target element
    final var targetFlowScopeIds = new HashSet<String>();
    var currentFlowScope = targetDirectFlowScope;
    while (currentFlowScope != null) {
      targetFlowScopeIds.add(BufferUtil.bufferAsString(currentFlowScope.getId()));
      currentFlowScope = currentFlowScope.getFlowScope();
    }

    // Traverse up the source element instance's ancestry to find a matching scope
    // Start from the parent of sourceParent since we already checked sourceParent above
    var currentAncestorKey =
        sourceParentInstance != null ? sourceParentInstance.getParentKey() : sourceParentKey;
    while (currentAncestorKey > 0) {
      final var ancestorInstance = elementInstanceState.getInstance(currentAncestorKey);
      if (ancestorInstance == null) {
        break;
      }

      final var ancestorElementId = ancestorInstance.getValue().getElementId();
      if (targetFlowScopeIds.contains(ancestorElementId)) {
        // Found an ancestor instance whose element is in the target's flow scope hierarchy
        return currentAncestorKey;
      }

      // Move up to the next ancestor
      currentAncestorKey = ancestorInstance.getParentKey();
    }

    // No matching ancestor found - return -1 to let the activation logic handle it
    // (it will create new flow scope instances as needed)
    return ElementActivationBehavior.NO_ANCESTOR_SCOPE_KEY;
  }

  private Either<Rejection, ?> validateCommand(
      final List<ProcessInstanceModificationTerminateInstructionValue> terminateInstructions,
      final List<ProcessInstanceModificationMoveInstructionValue> moveInstructions,
      final DeployedProcess process) {
    return validateTerminateInstructionsInput(terminateInstructions, process)
        .flatMap(valid -> validateMoveInstructionsInput(moveInstructions, process))
        .map(valid -> VALID);
  }

  private Either<Rejection, ?> validateTerminateInstructionsInput(
      final List<ProcessInstanceModificationTerminateInstructionValue> terminateInstructions,
      final DeployedProcess process) {
    return validateHasTerminateInstructions(terminateInstructions, process)
        .flatMap(valid -> validateNoDuplicatedTerminateInstructions(terminateInstructions, process))
        .map(valid -> VALID);
  }

  private Either<Rejection, ?> validateHasTerminateInstructions(
      final List<ProcessInstanceModificationTerminateInstructionValue> terminateInstructions,
      final DeployedProcess process) {
    final var noDefinitions =
        terminateInstructions.stream()
            .filter(
                terminateInstruction ->
                    terminateInstruction.getElementInstanceKey() <= 0
                        && terminateInstruction.getElementId().isBlank())
            .map(
                terminateInstruction ->
                    "(%s, %s)"
                        .formatted(
                            terminateInstruction.getElementInstanceKey(),
                            terminateInstruction.getElementId()))
            .toList();

    if (noDefinitions.isEmpty()) {
      return VALID;
    }

    final String reason =
        String.format(
            ERROR_MESSAGE_TERMINATE_NO_DEFINITIONS,
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            String.join("', '", noDefinitions));
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private Either<Rejection, ?> validateNoDuplicatedTerminateInstructions(
      final List<ProcessInstanceModificationTerminateInstructionValue> terminateInstructions,
      final DeployedProcess process) {
    final var duplicateDefinitions =
        terminateInstructions.stream()
            .filter(
                terminateInstruction ->
                    terminateInstruction.getElementInstanceKey() > 0
                        && !terminateInstruction.getElementId().isBlank())
            .map(
                terminateInstruction ->
                    "(%s, %s)"
                        .formatted(
                            terminateInstruction.getElementInstanceKey(),
                            terminateInstruction.getElementId()))
            .toList();

    if (duplicateDefinitions.isEmpty()) {
      return VALID;
    }

    final String reason =
        String.format(
            ERROR_MESSAGE_TERMINATE_MULTIPLE_DEFINITIONS,
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            String.join("', '", duplicateDefinitions));
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private Either<Rejection, ?> validateMoveInstructionsInput(
      final List<ProcessInstanceModificationMoveInstructionValue> moveInstructions,
      final DeployedProcess process) {
    return validateHasMoveInstructions(moveInstructions, process)
        .flatMap(valid -> validateNoDuplicatedMoveInstructions(moveInstructions, process))
        .flatMap(valid -> validateNoMoveSourceWithBothIdAndInstanceKey(moveInstructions, process))
        .flatMap(valid -> validateNoAmbiguousAncestorScopeOptions(moveInstructions, process))
        .map(valid -> VALID);
  }

  private Either<Rejection, ?> validateHasMoveInstructions(
      final List<ProcessInstanceModificationMoveInstructionValue> moveInstructions,
      final DeployedProcess process) {
    final var noDefinitions =
        moveInstructions.stream()
            .filter(
                moveInstruction ->
                    (moveInstruction.getSourceElementInstanceKey() <= 0
                            && moveInstruction.getSourceElementId().isBlank())
                        || moveInstruction.getTargetElementId().isBlank())
            .map(
                moveInstruction ->
                    "(%s, %s)"
                        .formatted(
                            moveInstruction.getSourceElementId().isBlank()
                                    && moveInstruction.getSourceElementInstanceKey() > 0
                                ? moveInstruction.getSourceElementInstanceKey()
                                : moveInstruction.getSourceElementId(),
                            moveInstruction.getTargetElementId()))
            .toList();

    if (noDefinitions.isEmpty()) {
      return VALID;
    }

    final String reason =
        String.format(
            ERROR_MESSAGE_MOVE_NO_DEFINITIONS,
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            String.join("', '", noDefinitions));
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private Either<Rejection, ?> validateNoDuplicatedMoveInstructions(
      final List<ProcessInstanceModificationMoveInstructionValue> moveInstructions,
      final DeployedProcess process) {
    final var duplicateSourceIds =
        moveInstructions.stream()
            .map(ProcessInstanceModificationMoveInstructionValue::getSourceElementId)
            .filter(sourceId -> !sourceId.isBlank())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Map.Entry::getKey)
            .toList();

    if (duplicateSourceIds.isEmpty()) {
      return VALID;
    }

    final String reason =
        String.format(
            ERROR_MESSAGE_MOVE_DUPLICATE_DEFINITIONS,
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            String.join("', '", duplicateSourceIds));
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private Either<Rejection, Object> validateNoMoveSourceWithBothIdAndInstanceKey(
      final List<ProcessInstanceModificationMoveInstructionValue> moveInstructions,
      final DeployedProcess process) {
    final var duplicateDefinitions =
        moveInstructions.stream()
            .filter(
                moveInstruction ->
                    moveInstruction.getSourceElementInstanceKey() > 0
                        && !moveInstruction.getSourceElementId().isBlank())
            .map(
                moveInstruction ->
                    "(%s/%d, %s)"
                        .formatted(
                            moveInstruction.getSourceElementId(),
                            moveInstruction.getSourceElementInstanceKey(),
                            moveInstruction.getTargetElementId()))
            .toList();

    if (duplicateDefinitions.isEmpty()) {
      return VALID;
    }

    final String reason =
        String.format(
            ERROR_MESSAGE_MOVE_MULTIPLE_DEFINITIONS,
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            String.join("', '", duplicateDefinitions));
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private Either<Rejection, Object> validateNoAmbiguousAncestorScopeOptions(
      final List<ProcessInstanceModificationMoveInstructionValue> moveInstructions,
      final DeployedProcess process) {
    final var ambiguousInstructions =
        moveInstructions.stream()
            .filter(this::hasMultipleAncestorScopeOptionsSet)
            .map(
                moveInstruction ->
                    "(%s, %s)"
                        .formatted(
                            moveInstruction.getSourceElementId().isBlank()
                                ? moveInstruction.getSourceElementInstanceKey()
                                : moveInstruction.getSourceElementId(),
                            moveInstruction.getTargetElementId()))
            .toList();

    if (ambiguousInstructions.isEmpty()) {
      return VALID;
    }

    final String reason =
        String.format(
            ERROR_MESSAGE_MOVE_AMBIGUOUS_ANCESTOR_SCOPE,
            BufferUtil.bufferAsString(process.getBpmnProcessId()),
            String.join("', '", ambiguousInstructions));
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }

  private boolean hasMultipleAncestorScopeOptionsSet(
      final ProcessInstanceModificationMoveInstructionValue moveInstruction) {
    final long ancestorScopeOptionsCount =
        Stream.of(
                moveInstruction.getAncestorScopeKey() > 0,
                moveInstruction.isInferAncestorScopeFromSourceHierarchy(),
                moveInstruction.isUseSourceParentKeyAsAncestorScopeKey())
            .filter(b -> b)
            .count();
    return ancestorScopeOptionsCount > 1;
  }

  private Either<Rejection, ?> validateInstructions(
      final List<ProcessInstanceModificationActivateInstructionValue> activateInstructions,
      final List<ProcessInstanceModificationTerminateInstructionValue> terminateInstructions,
      final DeployedProcess process,
      final long processInstanceKey) {

    return validateElementExists(process, activateInstructions)
        .flatMap(valid -> validateElementSupported(process, activateInstructions))
        .flatMap(valid -> validateElementInstanceExists(process, terminateInstructions))
        .flatMap(valid -> validateVariableScopeExists(process, activateInstructions))
        .flatMap(valid -> validateVariableScopeIsFlowScope(process, activateInstructions))
        .flatMap(valid -> validateAncestorKeys(process, activateInstructions, processInstanceKey))
        .flatMap(
            valid ->
                validateElementIsNotActivatedForTerminatedFlowScope(
                    process, activateInstructions, terminateInstructions))
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
      final DeployedProcess process,
      final List<ProcessInstanceModificationActivateInstructionValue> activateInstructions,
      final long processInstanceKey) {
    final Map<Long, Optional<ElementInstance>> ancestorInstances =
        activateInstructions.stream()
            .map(ProcessInstanceModificationActivateInstructionValue::getAncestorScopeKey)
            .filter(ancestorKey -> ancestorKey > 0)
            .distinct()
            .collect(
                Collectors.toMap(
                    ancestorKey -> ancestorKey,
                    ancestorKey ->
                        Optional.ofNullable(elementInstanceState.getInstance(ancestorKey))));

    return validateAncestorExistsAndIsActive(process, activateInstructions, ancestorInstances)
        .flatMap(
            valid ->
                validateAncestorBelongsToProcessInstance(
                    process, processInstanceKey, ancestorInstances))
        .flatMap(
            valid ->
                validateAncestorIsFlowScopeOfElement(
                    process, activateInstructions, ancestorInstances))
        .map(valid -> VALID);
  }

  private Either<Rejection, ?> validateAncestorExistsAndIsActive(
      final DeployedProcess process,
      final List<ProcessInstanceModificationActivateInstructionValue> activateInstructions,
      final Map<Long, Optional<ElementInstance>> ancestorInstances) {
    final Set<String> invalidAncestorKeys =
        activateInstructions.stream()
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
      final long processInstanceKey,
      final Map<Long, Optional<ElementInstance>> ancestorInstances) {
    final Set<String> rejectedAncestorKeys =
        ancestorInstances.values().stream()
            .flatMap(Optional::stream)
            .filter(
                ancestorInstance ->
                    ancestorInstance.getValue().getProcessInstanceKey() != processInstanceKey)
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
      final List<ProcessInstanceModificationActivateInstructionValue> activateInstructions,
      final Map<Long, Optional<ElementInstance>> ancestorInstances) {
    record InstructionDetails(long ancestorScopeKey, String ancestorId, String elementId) {}

    final String invalidInstructionMessages =
        activateInstructions.stream()
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

  private Either<Rejection, ?> validateMoveSourceElementInstanceExists(
      final List<ProcessInstanceModificationMoveInstructionValue> moveInstructions,
      final DirectBuffer bpmnProcessId,
      final long processInstanceKey) {

    if (moveInstructions.isEmpty()) {
      return VALID;
    }

    // Check for source element instances that don't exist
    final List<Long> notFoundInstanceKeys =
        moveInstructions.stream()
            .map(ProcessInstanceModificationMoveInstructionValue::getSourceElementInstanceKey)
            .distinct()
            .filter(instanceKey -> elementInstanceState.getInstance(instanceKey) == null)
            .toList();

    if (!notFoundInstanceKeys.isEmpty()) {
      final String reason =
          String.format(
              ERROR_MESSAGE_MOVE_SOURCE_ELEMENT_INSTANCE_NOT_FOUND,
              BufferUtil.bufferAsString(bpmnProcessId),
              notFoundInstanceKeys.stream()
                  .map(Objects::toString)
                  .collect(Collectors.joining("', '")));
      return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
    }

    // Check for source element instances that don't belong to the process instance
    final List<Long> wrongProcessInstanceKeys =
        moveInstructions.stream()
            .map(ProcessInstanceModificationMoveInstructionValue::getSourceElementInstanceKey)
            .distinct()
            .filter(
                instanceKey -> {
                  final var instance = elementInstanceState.getInstance(instanceKey);
                  return instance != null
                      && instance.getValue().getProcessInstanceKey() != processInstanceKey;
                })
            .toList();

    if (!wrongProcessInstanceKeys.isEmpty()) {
      final String reason =
          String.format(
              ERROR_MESSAGE_MOVE_SOURCE_ELEMENT_INSTANCE_WRONG_PROCESS,
              BufferUtil.bufferAsString(bpmnProcessId),
              wrongProcessInstanceKeys.stream()
                  .map(Objects::toString)
                  .collect(Collectors.joining("', '")));
      return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
    }

    return VALID;
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

  private Either<Rejection, ?> validateElementIsNotActivatedForTerminatedFlowScope(
      final DeployedProcess process,
      final List<ProcessInstanceModificationActivateInstructionValue> activateInstructions,
      final List<ProcessInstanceModificationTerminateInstructionValue> terminateInstructions) {
    // Collect all terminate instance keys
    final Set<Long> terminatedInstanceKeys =
        terminateInstructions.stream()
            .map(ProcessInstanceModificationTerminateInstructionValue::getElementInstanceKey)
            .collect(Collectors.toSet());

    final List<String> conflictingActivations = new ArrayList<>();
    for (final var terminatedInstanceKey : terminatedInstanceKeys) {
      final var terminatedInstance = elementInstanceState.getInstance(terminatedInstanceKey);
      if (terminatedInstance == null) {
        // this should be already validated in validateElementInstanceExists
        continue;
      }

      final var terminatedElement =
          process.getProcess().getElementById(terminatedInstance.getValue().getElementId());
      // Find all elements that have the terminated element as flow scope
      // consider nested flow scopes as well as activate instructions that has ancestor scope key
      // set
      final var conflictingElements =
          activateInstructions.stream()
              .filter(
                  activationInstruction -> {
                    final var elementToActivate =
                        process.getProcess().getElementById(activationInstruction.getElementId());

                    if (activationInstruction.getAncestorScopeKey() > 0) {
                      // if ancestor scope key is set, check if it matches the terminated instance
                      // key
                      return activationInstruction.getAncestorScopeKey() == terminatedInstanceKey;
                    }

                    final String terminatedElementId =
                        BufferUtil.bufferAsString(terminatedElement.getId());

                    return isFlowScopeOfElement(elementToActivate, terminatedElementId);
                  })
              .toList();

      for (final var conflictingElement : conflictingElements) {
        conflictingActivations.add(
            String.format(
                ERROR_MESSAGE_ACTIVATION_FLOW_SCOPE_CONFLICT,
                conflictingElement.getElementId(),
                terminatedInstanceKey));
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
