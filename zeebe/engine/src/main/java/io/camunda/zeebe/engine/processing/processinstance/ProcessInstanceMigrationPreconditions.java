/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.auth.impl.TenantAuthorizationCheckerImpl;
import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBoundaryEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventSupplier;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.DistributionState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.EventTrigger;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue.ProcessInstanceMigrationMappingInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public final class ProcessInstanceMigrationPreconditions {

  private static final EnumSet<BpmnElementType> SUPPORTED_ELEMENT_TYPES =
      EnumSet.of(
          BpmnElementType.PROCESS,
          BpmnElementType.SERVICE_TASK,
          BpmnElementType.USER_TASK,
          BpmnElementType.SUB_PROCESS,
          BpmnElementType.CALL_ACTIVITY,
          BpmnElementType.INTERMEDIATE_CATCH_EVENT,
          BpmnElementType.RECEIVE_TASK,
          BpmnElementType.EVENT_SUB_PROCESS,
          BpmnElementType.EXCLUSIVE_GATEWAY,
          BpmnElementType.EVENT_BASED_GATEWAY,
          BpmnElementType.BUSINESS_RULE_TASK,
          BpmnElementType.SCRIPT_TASK,
          BpmnElementType.SEND_TASK,
          BpmnElementType.MULTI_INSTANCE_BODY);
  private static final Set<BpmnElementType> UNSUPPORTED_ELEMENT_TYPES =
      EnumSet.complementOf(SUPPORTED_ELEMENT_TYPES);
  private static final Set<BpmnEventType> SUPPORTED_INTERMEDIATE_CATCH_EVENT_TYPES =
      EnumSet.of(BpmnEventType.MESSAGE, BpmnEventType.TIMER, BpmnEventType.SIGNAL);

  private static final String ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND =
      "Expected to migrate process instance but no process instance found with key '%d'";
  private static final String ERROR_MESSAGE_PROCESS_DEFINITION_NOT_FOUND =
      """
      Expected to migrate process instance to process definition \
      but no process definition found with key '%d'""";
  private static final String ERROR_MESSAGE_PROCESS_DEFINITION_HAS_START_EVENT_INSTANCE =
      """
      Expected to migrate process instance '%d' \
      but target process definition '%s' has an active instance triggered by a message start event with correlation key '%s'. \
      Only one instance per correlation key is allowed for message start events.""";
  private static final String ERROR_MESSAGE_DUPLICATE_SOURCE_ELEMENT_IDS =
      """
      Expected to migrate process instance '%s' \
      but the mapping instructions contain duplicate source element ids '%s'.""";
  private static final String ERROR_SOURCE_ELEMENT_ID_NOT_FOUND =
      """
      Expected to migrate process instance '%s' \
      but mapping instructions contain a non-existing source element id '%s'. \
      Elements provided in mapping instructions must exist \
      in the source process definition.""";
  private static final String ERROR_TARGET_ELEMENT_ID_NOT_FOUND =
      """
      Expected to migrate process instance '%s' \
      but mapping instructions contain a non-existing target element id '%s'. \
      Elements provided in mapping instructions must exist \
      in the target process definition.""";
  private static final String ERROR_MESSAGE_EVENT_SUBPROCESS_NOT_SUPPORTED_IN_PROCESS_INSTANCE =
      """
      Expected to migrate process instance '%s' \
      but active process with id '%s' has one or more event subprocesses with start events of types '%s'. \
      Migrating event subprocesses with start events of these types is not possible yet.""";
  private static final String ERROR_MESSAGE_EVENT_SUBPROCESS_NOT_SUPPORTED_IN_TARGET_PROCESS =
      """
      Expected to migrate process instance '%s' \
      but target process with id '%s' has one or more event subprocesses with start events of types '%s'. \
      Migrating event subprocesses with start events of these types is not possible yet.""";
  private static final String ERROR_UNSUPPORTED_ELEMENT_TYPE =
      """
      Expected to migrate process instance '%s' \
      but active element with id '%s' has an unsupported type. \
      The migration of a %s is not supported.""";
  private static final String ERROR_UNSUPPORTED_INTERMEDIATE_CATCH_EVENT_TYPE =
      """
      Expected to migrate process instance '%s' \
      but active element with id '%s' is intermediate catch event of type '%s'. \
      Migrating active intermediate catch event of this type is not possible yet.""";
  private static final String ERROR_UNSUPPORTED_ATTACHED_TO_EVENT_BASED_GATEWAY =
      """
      Expected to migrate process instance '%s' \
      but active element with id '%s' is an intermediate catch event \
      attached to an event-based gateway. \
      Migrating active events attached to an event-based gateway is not possible yet.""";
  private static final String ERROR_UNMAPPED_ACTIVE_ELEMENT =
      """
      Expected to migrate process instance '%s' \
      but no mapping instruction defined for active element with id '%s'. \
      Elements cannot be migrated without a mapping.""";
  private static final String ERROR_ELEMENT_TYPE_CHANGED =
      """
      Expected to migrate process instance '%s' \
      but active element with id '%s' and type '%s' is mapped to \
      an element with id '%s' and different type '%s'. \
      Elements must be mapped to elements of the same type.""";
  private static final String ERROR_USER_TASK_IMPLEMENTATION_CHANGED =
      """
      Expected to migrate process instance '%s' \
      but active user task with id '%s' and implementation '%s' is mapped to \
      an user task with id '%s' and different implementation '%s'. \
      Elements must be mapped to elements of the same implementation.""";
  private static final String ERROR_MESSAGE_ELEMENT_FLOW_SCOPE_CHANGED =
      """
      Expected to migrate process instance '%s' \
      but the flow scope of active element with id '%s' is changed. \
      The flow scope of the active element is expected to be '%s' but was '%s'. \
      The flow scope of an element cannot be changed during migration yet.""";
  private static final String ERROR_ACTIVE_ELEMENT_WITH_BOUNDARY_EVENT =
      """
      Expected to migrate process instance '%s' \
      but active element with id '%s' has one or more boundary events of types '%s'. \
      Migrating active elements with boundary events of these types is not possible yet.""";
  private static final String ERROR_TARGET_ELEMENT_WITH_BOUNDARY_EVENT =
      """
      Expected to migrate process instance '%s' \
      but target element with id '%s' has one or more boundary events of types '%s'. \
      Migrating target elements with boundary events of these types is not possible yet.""";
  private static final String ERROR_CATCH_EVENT_DETACHED_FROM_ELEMENT =
      """
      Expected to migrate process instance '%s' \
      but active element with id '%s' is mapped to an element with id '%s' and \
      has a catch event with id '%s' that is mapped to a catch event with id '%s'. \
      These mappings detach the catch event from the element in the target process. \
      Catch events must stay attached to the same element instance.""";
  private static final String ERROR_PENDING_DISTRIBUTION =
      """
      Expected to migrate process instance '%s' \
      but active element with id '%s' has a pending message subscription \
      migration distribution for event with id '%s'.""";
  private static final String ERROR_CONCURRENT_COMMAND =
      """
      Expected to migrate process instance '%s' \
      but a concurrent command was executed on the process instance. \
      Please retry the migration.""";

  private static final String ERROR_UPDATED_LOOP_CHARACTERISTICS =
      """
      Expected to migrate process instance '%s' \
      but active element with id '%s' has a different loop characteristics \
      than the target element with id '%s'. \
      Both elements must have either sequential or parallel loop characteristics.""";

  private static final String ZEEBE_USER_TASK_IMPLEMENTATION = "zeebe user task";
  private static final String JOB_WORKER_IMPLEMENTATION = "job worker";

  /**
   * Checks whether the given record exists. Throws exception if given process instance record is
   * null.
   *
   * @param record process instance record to do the null check
   * @param processInstanceKey process instance key to be logged
   */
  public static void requireNonNullProcessInstance(
      final ElementInstance record, final long processInstanceKey) {
    if (record == null) {
      final String reason =
          String.format(ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND, processInstanceKey);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.NOT_FOUND);
    }
  }

  /**
   * Checks whether given tenant is authorized for the process given instance.
   *
   * @param authorizations list of authorizations available
   * @param tenantId tenant id to be checked
   * @param processInstanceKey process instance key to be logged
   */
  public static void requireAuthorizedTenant(
      final Map<String, Object> authorizations,
      final String tenantId,
      final long processInstanceKey) {
    final boolean isTenantAuthorized =
        TenantAuthorizationCheckerImpl.fromAuthorizationMap(authorizations).isAuthorized(tenantId);
    if (!isTenantAuthorized) {
      final String reason =
          String.format(ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND, processInstanceKey);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.NOT_FOUND);
    }
  }

  /**
   * Checks whether the given target process definition exists. Throws exception if given target
   * process definition is null.
   *
   * @param targetProcessDefinition target process definition to do the null check
   * @param targetProcessDefinitionKey target process definition key to be logged
   */
  public static void requireNonNullTargetProcessDefinition(
      final DeployedProcess targetProcessDefinition, final long targetProcessDefinitionKey) {
    if (targetProcessDefinition == null) {
      final String reason =
          String.format(ERROR_MESSAGE_PROCESS_DEFINITION_NOT_FOUND, targetProcessDefinitionKey);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.NOT_FOUND);
    }
  }

  /**
   * Checks whether the given target process definition has an instance triggered by a message start
   * event with the same correlation key as the process instance. Throws exception if given target
   * process definition already has an instance triggered by a message start event with the same
   * correlation key as the process instance.
   *
   * @param processInstance process instance to do the check
   * @param targetProcessDefinition target process definition to do the check
   * @param messageState message state to check for existing message start event instance
   */
  public static void requireNoStartEventInstanceForTargetProcess(
      final ElementInstance processInstance,
      final DeployedProcess targetProcessDefinition,
      final MessageState messageState) {
    if (processInstance
        .getValue()
        .getBpmnProcessIdBuffer()
        .equals(targetProcessDefinition.getBpmnProcessId())) {
      // no need to check correlation key cardinality since bpmn process id will not change
      return;
    }

    if (!targetProcessDefinition.getProcess().hasMessageStartEvent()) {
      // no need to check since target process does not contain message start event
      return;
    }

    final DirectBuffer correlationKey =
        messageState.getProcessInstanceCorrelationKey(processInstance.getKey());
    if (correlationKey == null) {
      // no need to check since process instance is created without specifying correlation key
      return;
    }

    final boolean activeProcessInstanceExistsForTarget =
        messageState.existActiveProcessInstance(
            processInstance.getValue().getTenantId(),
            targetProcessDefinition.getBpmnProcessId(),
            correlationKey);

    if (activeProcessInstanceExistsForTarget) {
      final String reason =
          String.format(
              ERROR_MESSAGE_PROCESS_DEFINITION_HAS_START_EVENT_INSTANCE,
              processInstance.getKey(),
              targetProcessDefinition.getKey(),
              BufferUtil.bufferAsString(correlationKey));
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.INVALID_STATE);
    }
  }

  /**
   * Checks whether the given mapping instructions contain duplicate source element ids. Throws an
   * exception if duplicate source element ids are found.
   *
   * @param mappingInstructions mapping instructions to do the check
   * @param processInstanceKey process instance key to be logged
   */
  public static void requireNonDuplicateSourceElementIds(
      final List<ProcessInstanceMigrationMappingInstructionValue> mappingInstructions,
      final long processInstanceKey) {
    final Map<String, Long> countBySourceElementId =
        mappingInstructions.stream()
            .collect(
                Collectors.groupingBy(
                    ProcessInstanceMigrationMappingInstructionValue::getSourceElementId,
                    Collectors.counting()));
    final List<String> duplicateSourceElementIds =
        countBySourceElementId.entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Entry::getKey)
            .toList();

    if (!duplicateSourceElementIds.isEmpty()) {
      final String reason =
          String.format(
              ERROR_MESSAGE_DUPLICATE_SOURCE_ELEMENT_IDS,
              processInstanceKey,
              duplicateSourceElementIds);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.INVALID_ARGUMENT);
    }
  }

  /**
   * Checks whether the given mapping instructions refer to existing elements in the source and the
   * target process definition. Throws an exception if any of the mapping instructions refers to a
   * non-existing element.
   *
   * @param sourceProcessDefinition source process definition
   * @param targetProcessDefinition target process definition
   * @param mappingInstructions mapping instructions to do the check
   * @param processInstanceKey process instance key to be logged
   */
  public static void requireReferredElementsExist(
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition,
      final List<ProcessInstanceMigrationMappingInstructionValue> mappingInstructions,
      final long processInstanceKey) {

    mappingInstructions.forEach(
        instruction -> {
          final String sourceElementId = instruction.getSourceElementId();
          if (sourceProcessDefinition.getProcess().getElementById(sourceElementId) == null) {
            final String reason =
                String.format(
                    ERROR_SOURCE_ELEMENT_ID_NOT_FOUND, processInstanceKey, sourceElementId);
            throw new ProcessInstanceMigrationPreconditionFailedException(
                reason, RejectionType.INVALID_ARGUMENT);
          }

          final String targetElementId = instruction.getTargetElementId();
          if (targetProcessDefinition.getProcess().getElementById(targetElementId) == null) {
            final String reason =
                String.format(
                    ERROR_TARGET_ELEMENT_ID_NOT_FOUND, processInstanceKey, targetElementId);
            throw new ProcessInstanceMigrationPreconditionFailedException(
                reason, RejectionType.INVALID_ARGUMENT);
          }
        });
  }

  /**
   * Checks whether the given source process definition contains an event subprocess. Throws an
   * exception if the source process definition contains an event subprocess that is not allowed.
   *
   * @param sourceProcessDefinition source process definition to do the check
   * @param elementInstanceRecord element instance to be logged
   * @param allowedEventTypes allowed event types for the boundary event
   */
  public static void requireNoEventSubprocessInSource(
      final DeployedProcess sourceProcessDefinition,
      final ProcessInstanceRecord elementInstanceRecord,
      final EnumSet<BpmnEventType> allowedEventTypes) {
    requireNoEventSubprocess(
        sourceProcessDefinition,
        elementInstanceRecord,
        elementInstanceRecord.getElementId(),
        allowedEventTypes,
        ERROR_MESSAGE_EVENT_SUBPROCESS_NOT_SUPPORTED_IN_PROCESS_INSTANCE);
  }

  /**
   * Checks whether the given target process definition contains an event subprocess. Throws an
   * exception if the target process definition contains an event subprocess that is not allowed.
   *
   * @param targetProcessDefinition target process definition to do the check
   * @param targetElementId target element id to retrieve the target element
   * @param elementInstanceRecord element instance to be logged
   * @param allowedEventTypes allowed event types for the boundary event
   */
  public static void requireNoEventSubprocessInTarget(
      final DeployedProcess targetProcessDefinition,
      final String targetElementId,
      final ProcessInstanceRecord elementInstanceRecord,
      final EnumSet<BpmnEventType> allowedEventTypes) {
    requireNoEventSubprocess(
        targetProcessDefinition,
        elementInstanceRecord,
        targetElementId,
        allowedEventTypes,
        ERROR_MESSAGE_EVENT_SUBPROCESS_NOT_SUPPORTED_IN_TARGET_PROCESS);
  }

  private static void requireNoEventSubprocess(
      final DeployedProcess sourceProcessDefinition,
      final ProcessInstanceRecord elementInstanceRecord,
      final String elementId,
      final EnumSet<BpmnEventType> allowedEventTypes,
      final String errorTemplate) {
    final AbstractFlowElement sourceElement =
        sourceProcessDefinition.getProcess().getElementById(elementId);

    if (!(sourceElement instanceof final ExecutableActivity sourceActivity)) {
      // no event subprocess event check needed because the given element cannot contain an event
      // subprocess
      return;
    }

    final List<ExecutableStartEvent> rejectedEvents =
        sourceActivity.getEventSubprocesses().stream()
            .flatMap(sub -> sub.getStartEvents().stream())
            .filter(start -> !allowedEventTypes.contains(start.getEventType()))
            .toList();

    if (!rejectedEvents.isEmpty()) {
      final String rejectedEventTypes =
          rejectedEvents.stream()
              .map(ExecutableStartEvent::getEventType)
              .map(BpmnEventType::name)
              .collect(Collectors.joining(","));
      final String reason =
          errorTemplate.formatted(
              elementInstanceRecord.getProcessInstanceKey(), elementId, rejectedEventTypes);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.INVALID_STATE);
    }
  }

  /**
   * Checks whether the given element instance is of a supported type. Throws an exception if the
   * element instance is of an unsupported type.
   *
   * @param elementInstanceRecord element instance to do the check
   * @param processInstanceKey process instance key to be logged
   */
  public static void requireSupportedElementType(
      final ProcessInstanceRecord elementInstanceRecord,
      final long processInstanceKey,
      final DeployedProcess sourceProcessDefinition) {
    final var bpmnElementType = elementInstanceRecord.getBpmnElementType();
    if (UNSUPPORTED_ELEMENT_TYPES.contains(bpmnElementType)) {
      final String reason =
          String.format(
              ERROR_UNSUPPORTED_ELEMENT_TYPE,
              processInstanceKey,
              elementInstanceRecord.getElementId(),
              bpmnElementType);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.INVALID_STATE);
    }

    final var bpmnEventType = elementInstanceRecord.getBpmnEventType();
    if (bpmnElementType == BpmnElementType.INTERMEDIATE_CATCH_EVENT) {
      if (!SUPPORTED_INTERMEDIATE_CATCH_EVENT_TYPES.contains(bpmnEventType)) {
        final String reason =
            String.format(
                ERROR_UNSUPPORTED_INTERMEDIATE_CATCH_EVENT_TYPE,
                processInstanceKey,
                elementInstanceRecord.getElementId(),
                bpmnEventType);
        throw new ProcessInstanceMigrationPreconditionFailedException(
            reason, RejectionType.INVALID_STATE);
      }

      final var intermediateCatchEvent =
          sourceProcessDefinition
              .getProcess()
              .getElementById(
                  elementInstanceRecord.getElementIdBuffer(), ExecutableCatchEventElement.class);
      if (intermediateCatchEvent.isConnectedToEventBasedGateway()) {
        final var reason =
            String.format(
                ERROR_UNSUPPORTED_ATTACHED_TO_EVENT_BASED_GATEWAY,
                processInstanceKey,
                elementInstanceRecord.getElementId());
        throw new ProcessInstanceMigrationPreconditionFailedException(
            reason, RejectionType.INVALID_STATE);
      }
    }
  }

  /**
   * Checks whether the given target element id exists. Throws an exception if the target element id
   * is null.
   *
   * @param targetElementId target element id to do the null check
   * @param processInstanceKey process instance key to be logged
   * @param sourceElementId source element id to be logged
   */
  public static void requireNonNullTargetElementId(
      final String targetElementId, final long processInstanceKey, final String sourceElementId) {
    if (targetElementId == null) {
      final String reason =
          String.format(ERROR_UNMAPPED_ACTIVE_ELEMENT, processInstanceKey, sourceElementId);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.INVALID_STATE);
    }
  }

  /**
   * Checks whether the given element instance has the same element type as the target element.
   * Throws an exception if the element instance has a different type.
   *
   * @param targetProcessDefinition target process definition to retrieve the target element type
   * @param targetElementId target element id
   * @param elementInstance element instance to do the check
   * @param processInstanceKey process instance key to be logged
   */
  public static void requireSameElementType(
      final DeployedProcess targetProcessDefinition,
      final String targetElementId,
      final ElementInstance elementInstance,
      final long processInstanceKey) {
    final ProcessInstanceRecord elementInstanceRecord = elementInstance.getValue();
    BpmnElementType targetElementType =
        targetProcessDefinition.getProcess().getElementById(targetElementId).getElementType();

    if (elementInstanceRecord.getBpmnElementType() == targetElementType) {
      return;
    }

    // if target element is a multi instance body, we should check the inner activity element type
    // because the inner activity of the multi instance body can still match the source element's
    // type. Also, multi instance loop counter indicates that the element instance is inside a multi
    // instance body.
    if (elementInstance.getMultiInstanceLoopCounter() > 0
        && targetElementType == BpmnElementType.MULTI_INSTANCE_BODY) {
      final ExecutableMultiInstanceBody targetElement =
          targetProcessDefinition
              .getProcess()
              .getElementById(targetElementId, ExecutableMultiInstanceBody.class);

      targetElementType = targetElement.getInnerActivity().getElementType();
      if (elementInstanceRecord.getBpmnElementType() == targetElementType) {
        return;
      }
    }

    final String reason =
        String.format(
            ERROR_ELEMENT_TYPE_CHANGED,
            processInstanceKey,
            elementInstanceRecord.getElementId(),
            elementInstanceRecord.getBpmnElementType(),
            targetElementId,
            targetElementType);
    throw new ProcessInstanceMigrationPreconditionFailedException(
        reason, RejectionType.INVALID_STATE);
  }

  /**
   * Since we introduce zeebe user tasks and job worker tasks has the same bpmn element type, we
   * need to check whether the given element instance and target element has the same user task
   * type. Throws an exception if they have different types.
   *
   * @param targetProcessDefinition target process definition to retrieve the target element type
   * @param targetElementId target element id
   * @param elementInstance element instance to do the check
   * @param processInstanceKey process instance key to be logged
   */
  public static void requireSameUserTaskImplementation(
      final DeployedProcess targetProcessDefinition,
      final String targetElementId,
      final ElementInstance elementInstance,
      final long processInstanceKey) {
    final ProcessInstanceRecord elementInstanceRecord = elementInstance.getValue();
    if (elementInstanceRecord.getBpmnElementType() != BpmnElementType.USER_TASK) {
      return;
    }

    final AbstractFlowElement targetElement =
        targetProcessDefinition.getProcess().getElementById(targetElementId);
    final BpmnElementType targetElementType = targetElement.getElementType();
    if (targetElementType != BpmnElementType.USER_TASK) {
      return;
    }

    final ExecutableUserTask targetUserTask =
        targetProcessDefinition
            .getProcess()
            .getElementById(targetElementId, ExecutableUserTask.class);
    final String targetUserTaskType =
        targetUserTask.getUserTaskProperties() != null
            ? ZEEBE_USER_TASK_IMPLEMENTATION
            : JOB_WORKER_IMPLEMENTATION;
    final String sourceUserTaskType =
        elementInstance.getUserTaskKey() > 0
            ? ZEEBE_USER_TASK_IMPLEMENTATION
            : JOB_WORKER_IMPLEMENTATION;

    if (!targetUserTaskType.equals(sourceUserTaskType)) {
      final String reason =
          String.format(
              ERROR_USER_TASK_IMPLEMENTATION_CHANGED,
              processInstanceKey,
              elementInstanceRecord.getElementId(),
              sourceUserTaskType,
              targetElementId,
              targetUserTaskType);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.INVALID_STATE);
    }
  }

  /**
   * Checks whether the given element instance has the same flow scope id as the target element.
   * Throws an exception if the element instance has a different flow scope.
   *
   * @param elementInstanceState element instance state to retrieve the source flow scope element
   * @param elementInstanceRecord element instance to do the check
   * @param targetProcessDefinition target process definition to retrieve the target element
   * @param targetElementId target element id to retrieve the target flow scope
   */
  public static void requireUnchangedFlowScope(
      final ElementInstanceState elementInstanceState,
      final ProcessInstanceRecord elementInstanceRecord,
      final DeployedProcess targetProcessDefinition,
      final String targetElementId) {
    final ElementInstance sourceFlowScopeElement =
        elementInstanceState.getInstance(elementInstanceRecord.getFlowScopeKey());
    if (sourceFlowScopeElement != null) {
      final DirectBuffer expectedFlowScopeId =
          sourceFlowScopeElement.getValue().getElementIdBuffer();
      final AbstractFlowElement targetFlowElement =
          targetProcessDefinition.getProcess().getElementById(targetElementId);
      DirectBuffer actualFlowScopeId;

      // if target element is a multi instance body, we should check the inner activity flow scope
      // because the inner activity of the multi instance body can still match the source element's
      // flow scope
      if (targetFlowElement.getElementType() == BpmnElementType.MULTI_INSTANCE_BODY) {
        final ExecutableMultiInstanceBody targetElement =
            targetProcessDefinition
                .getProcess()
                .getElementById(targetElementId, ExecutableMultiInstanceBody.class);

        actualFlowScopeId = targetElement.getInnerActivity().getFlowScope().getId();
        if (expectedFlowScopeId.equals(actualFlowScopeId)) {
          return;
        }
      }

      actualFlowScopeId = targetFlowElement.getFlowScope().getId();
      if (expectedFlowScopeId.equals(actualFlowScopeId)) {
        return;
      }

      final String reason =
          String.format(
              ERROR_MESSAGE_ELEMENT_FLOW_SCOPE_CHANGED,
              elementInstanceRecord.getProcessInstanceKey(),
              elementInstanceRecord.getElementId(),
              BufferUtil.bufferAsString(expectedFlowScopeId),
              BufferUtil.bufferAsString(actualFlowScopeId));
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.INVALID_STATE);
    }
  }

  /**
   * Checks whether the given source process definition contains a boundary event. Throws an
   * exception if the source process definition contains a boundary event that is not allowed.
   *
   * @param sourceProcessDefinition source process definition to do the check
   * @param elementInstanceRecord element instance to be logged
   * @param allowedEventTypes allowed event types for the boundary event
   */
  public static void requireNoBoundaryEventInSource(
      final DeployedProcess sourceProcessDefinition,
      final ProcessInstanceRecord elementInstanceRecord,
      final EnumSet<BpmnEventType> allowedEventTypes) {
    requireNoBoundaryEvent(
        sourceProcessDefinition,
        elementInstanceRecord,
        elementInstanceRecord.getElementId(),
        allowedEventTypes,
        ERROR_ACTIVE_ELEMENT_WITH_BOUNDARY_EVENT);
  }

  /**
   * Checks whether the given target process definition contains a boundary event. Throws an
   * exception if the target process definition contains a boundary event.
   *
   * @param targetProcessDefinition target process definition to do the check
   * @param targetElementId target element id to retrieve the target element
   * @param elementInstanceRecord element instance to be logged
   * @param allowedEventTypes allowed event types for the boundary event
   */
  public static void requireNoBoundaryEventInTarget(
      final DeployedProcess targetProcessDefinition,
      final String targetElementId,
      final ProcessInstanceRecord elementInstanceRecord,
      final EnumSet<BpmnEventType> allowedEventTypes) {
    requireNoBoundaryEvent(
        targetProcessDefinition,
        elementInstanceRecord,
        targetElementId,
        allowedEventTypes,
        ERROR_TARGET_ELEMENT_WITH_BOUNDARY_EVENT);
  }

  private static void requireNoBoundaryEvent(
      final DeployedProcess sourceProcessDefinition,
      final ProcessInstanceRecord elementInstanceRecord,
      final String elementId,
      final EnumSet<BpmnEventType> allowedEventTypes,
      final String errorTemplate) {
    final AbstractFlowElement sourceElement =
        sourceProcessDefinition.getProcess().getElementById(elementId);

    if (!(sourceElement instanceof final ExecutableActivity sourceActivity)) {
      // // no boundary event check needed because the given element cannot contain a boundary event
      return;
    }

    final var rejectedBoundaryEvents =
        sourceActivity.getBoundaryEvents().stream()
            .filter(event -> !allowedEventTypes.contains(event.getEventType()))
            .toList();

    if (!rejectedBoundaryEvents.isEmpty()) {
      final String rejectedEventTypes =
          rejectedBoundaryEvents.stream()
              .map(ExecutableBoundaryEvent::getEventType)
              .map(BpmnEventType::name)
              .collect(Collectors.joining(","));
      final String reason =
          errorTemplate.formatted(
              elementInstanceRecord.getProcessInstanceKey(), elementId, rejectedEventTypes);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.INVALID_STATE);
    }
  }

  /**
   * It should not be possible for a mapped element's catch events to be moved to another element.
   * This would mean an element instance is subscribed to a catch event that does not belong to this
   * element. Triggering the catch event could lead to unexpected behavior.
   *
   * <p>To avoid this, we check each catch event of the source element and ensure that they are
   * mapped to a catch event on the target element. This check includes all catch events like
   * boundary events, intermediate catch events, and start events (e.g. from event-subprocesses).
   *
   * @param processInstanceKey process instance key to be logged
   * @param sourceProcessDefinition source process definition to check
   * @param targetProcessDefinition target process definition to check
   * @param sourceElementId source element id to check
   * @param targetElementId target element id to check
   * @param sourceElementIdToTargetElementId mapping instructions (source element id to target
   */
  public static void requireMappedCatchEventsToStayAttachedToSameElement(
      final long processInstanceKey,
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition,
      final String sourceElementId,
      final String targetElementId,
      final Map<String, String> sourceElementIdToTargetElementId) {
    final var sourceElement = sourceProcessDefinition.getProcess().getElementById(sourceElementId);
    if (!(sourceElement instanceof final ExecutableCatchEventSupplier sourceCatchEventSupplier)) {
      return;
    }
    for (final var eventIdBuffer :
        sourceCatchEventSupplier.getEvents().stream().map(ExecutableFlowElement::getId).toList()) {
      final String sourceCatchEventId = BufferUtil.bufferAsString(eventIdBuffer);
      if (!sourceElementIdToTargetElementId.containsKey(sourceCatchEventId)) {
        // only check mapped catch events
        continue;
      }

      final var targetCatchEventId = sourceElementIdToTargetElementId.get(sourceCatchEventId);
      final var targetElement =
          targetProcessDefinition
              .getProcess()
              .getElementById(targetElementId, ExecutableCatchEventSupplier.class);
      if (targetElement.getEvents().stream()
          .map(catchEvent -> BufferUtil.bufferAsString(catchEvent.getId()))
          .noneMatch(targetCatchEventId::equals)) {
        // catch event has become detached from element
        final var reason =
            String.format(
                ERROR_CATCH_EVENT_DETACHED_FROM_ELEMENT,
                processInstanceKey,
                sourceElementId,
                targetElementId,
                sourceCatchEventId,
                targetCatchEventId);
        throw new ProcessInstanceMigrationPreconditionFailedException(
            reason, RejectionType.INVALID_STATE);
      }
    }
  }

  /**
   * It should not be possible for a mapped element's catch events to be merged into a single catch
   * event. This would mean an element instance is subscribed multiple times to the same catch
   * event.
   *
   * <p>To avoid this, we check each catch event attached to the source element and ensure that they
   * are the target of a mapping instruction only once.
   *
   * @param processInstanceKey process instance key to be logged
   * @param sourceProcessDefinition source process definition to check
   * @param sourceElementId source element id to check
   * @param mappingInstructions mapping instructions (source element id to target element id)
   */
  public static void requireNoDuplicateTargetsInCatchEventMappings(
      final long processInstanceKey,
      final DeployedProcess sourceProcessDefinition,
      final String sourceElementId,
      final Map<String, String> mappingInstructions) {
    final var sourceElement = sourceProcessDefinition.getProcess().getElementById(sourceElementId);
    if (!(sourceElement instanceof final ExecutableCatchEventSupplier sourceElementWithEvents)) {
      return;
    }

    final var sourceCatchEventIdsByTargetCatchEventId = new HashMap<String, List<String>>();
    sourceElementWithEvents.getEvents().stream()
        .map(catchEvent -> BufferUtil.bufferAsString(catchEvent.getId()))
        .filter(mappingInstructions::containsKey)
        .forEach(
            sourceCatchEventId -> {
              final String targetCatchEventId = mappingInstructions.get(sourceCatchEventId);
              sourceCatchEventIdsByTargetCatchEventId
                  .computeIfAbsent(targetCatchEventId, k -> new ArrayList<>())
                  .add(sourceCatchEventId);
            });

    sourceCatchEventIdsByTargetCatchEventId.forEach(
        (targetCatchEventId, sourceCatchEventIds) -> {
          if (sourceCatchEventIds.size() > 1) {
            final var reason =
                String.format(
                    """
                    Expected to migrate process instance '%s' but active element with id '%s' \
                    has a catch event attached that is mapped to a catch event with id '%s'. \
                    There are multiple mapping instructions that target this catch event: '%s'. \
                    Catch events cannot be merged by process instance migration. \
                    Please ensure the mapping instructions target a catch event only once.""",
                    processInstanceKey,
                    sourceElementId,
                    targetCatchEventId,
                    sourceCatchEventIds.stream().sorted().collect(Collectors.joining("', '")));
            throw new ProcessInstanceMigrationPreconditionFailedException(
                reason, RejectionType.INVALID_STATE);
          }
        });
  }

  /**
   * It should not be possible to change the event type of a catch event during process instance
   * migration. This would mean that the catch event is subscribed to a different event type than
   * before.
   *
   * @param processInstanceKey process instance key to be logged
   * @param mappingInstructions mapping instructions (source catch event id to target catch event
   *     id)
   * @param sourceProcessDefinition source process definition to check
   * @param targetProcessDefinition target process definition to check
   * @param sourceElementId source element id to check
   */
  public static void requireNoCatchEventMappingToChangeEventType(
      final long processInstanceKey,
      final Map<String, String> mappingInstructions,
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition,
      final String sourceElementId) {
    final var sourceElement = sourceProcessDefinition.getProcess().getElementById(sourceElementId);
    if (!(sourceElement instanceof final ExecutableCatchEventSupplier sourceElementWithEvents)) {
      return;
    }

    for (final ExecutableCatchEvent sourceCatchEvent : sourceElementWithEvents.getEvents()) {
      final String sourceCatchEventId = BufferUtil.bufferAsString(sourceCatchEvent.getId());
      if (!mappingInstructions.containsKey(sourceCatchEventId)) {
        continue;
      }

      final String targetCatchEventId = mappingInstructions.get(sourceCatchEventId);
      final var targetCatchEvent =
          targetProcessDefinition.getProcess().getElementById(targetCatchEventId);
      if (sourceCatchEvent.getEventType() != targetCatchEvent.getEventType()) {
        final var reason =
            String.format(
                """
                Expected to migrate process instance '%s' but active element with id '%s' \
                has a catch event with id '%s' that is mapped to a catch event with id '%s'. \
                These catch events have different event types: '%s' and '%s'. \
                The event type of a catch event cannot be changed by process instance migration. \
                Please ensure the event type of the catch event remains the same \
                or remove the mapping instruction for these catch events.""",
                processInstanceKey,
                sourceElementId,
                sourceCatchEventId,
                targetCatchEventId,
                sourceCatchEvent.getEventType(),
                targetCatchEvent.getEventType());
        throw new ProcessInstanceMigrationPreconditionFailedException(
            reason, RejectionType.INVALID_STATE);
      }
    }
  }

  /**
   * Checks whether the given process instance has a concurrent command. Throws an exception if the
   * given process instance has a concurrent command.
   *
   * <p>Some concurrent commands are a job complete, a timer trigger, or a message correlation.
   * Since the concurrent command modifies the process instance, it is not safe to apply the
   * migration in between.
   *
   * @param eventScopeInstanceState event scope instance state to retrieve the event trigger
   * @param elementInstance element instance to do the check active sequence flows
   * @param processInstanceKey process instance key to be logged
   */
  public static void requireNoConcurrentCommand(
      final EventScopeInstanceState eventScopeInstanceState,
      final ElementInstance elementInstance,
      final long processInstanceKey) {
    final EventTrigger eventTrigger =
        eventScopeInstanceState.peekEventTrigger(elementInstance.getKey());

    // An event trigger indicates a concurrent command. It is created when completing a job, or
    // triggering a timer/message/signal event.
    // or
    // An active sequence flow indicates a concurrent command. It is created when taking a
    // sequence flow and writing an ACTIVATE command for the next element.
    if (eventTrigger != null || elementInstance.getActiveSequenceFlows() > 0) {
      final String reason = String.format(ERROR_CONCURRENT_COMMAND, processInstanceKey);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.INVALID_STATE);
    }
  }

  /**
   * Throws an exception if the element instance is already subscribed to the same message.
   *
   * <p>We cannot support re-subscribing to message catch events that we're already subscribed to.
   * The user must provide a mapping instruction for such catch events to migrate them instead.
   *
   * @param existSubscriptionForMessageName whether the element instance is already subscribed to
   *     the message, if true this method throws an exception
   * @param elementInstance the element instance to check for subscriptions
   * @param messageName the name of the message that the element should not be subscribed to
   * @param targetCatchEventId the id of the catch event that would subscribe to this message
   */
  public static void requireNoSubscriptionForMessage(
      final boolean existSubscriptionForMessageName,
      final ElementInstance elementInstance,
      final DirectBuffer messageName,
      final String targetCatchEventId) {
    if (existSubscriptionForMessageName) {
      final long processInstanceKey = elementInstance.getValue().getProcessInstanceKey();
      final String elementId = elementInstance.getValue().getElementId();
      final String messageNameString = BufferUtil.bufferAsString(messageName);

      throw new ProcessInstanceMigrationPreconditionFailedException(
          """
          Expected to migrate process instance '%s' but active element with id '%s' \
          attempts to subscribe to a message it is already subscribed to with name '%s'. \
          Migrating active elements that subscribe to a message they are already \
          subscribed to is not possible yet. Please provide a mapping instruction to \
          message catch event with id '%s' to migrate the respective message subscription.\
          """
              .formatted(processInstanceKey, elementId, messageNameString, targetCatchEventId),
          RejectionType.INVALID_STATE);
    }
  }

  /**
   * Throws an exception if the given message subscription distribution is pending.
   *
   * @param distributionState the distribution state to check for pending distributions
   * @param distributionKey the distribution key of the distribution that is being checked
   * @param elementId the element id of the element that is being migrated (for logging)
   * @param processInstanceKey the process instance key of the process instance that is being
   *     migrated (for logging)
   * @param eventElementId the element id of the event that is being migrated (for logging)
   */
  public static void requireNoPendingMsgSubMigrationDistribution(
      final DistributionState distributionState,
      final long distributionKey,
      final String elementId,
      final long processInstanceKey,
      final String eventElementId) {
    final String message =
        ERROR_PENDING_DISTRIBUTION.formatted(processInstanceKey, elementId, eventElementId);
    requireNoPendingMigrationDistribution(distributionState, distributionKey, message);
  }

  public static void requireSameMultiInstanceLoopCharacteristics(
      final DeployedProcess sourceProcessDefinition,
      final String sourceElementId,
      final DeployedProcess targetProcessDefinition,
      final String targetElementId,
      final long processInstanceKey) {
    final BpmnElementType targetElementType =
        targetProcessDefinition.getProcess().getElementById(targetElementId).getElementType();
    final BpmnElementType sourceElementType =
        sourceProcessDefinition.getProcess().getElementById(sourceElementId).getElementType();
    if (sourceElementType == BpmnElementType.MULTI_INSTANCE_BODY
        && targetElementType == BpmnElementType.MULTI_INSTANCE_BODY) {
      final var targetElement =
          targetProcessDefinition
              .getProcess()
              .getElementById(targetElementId, ExecutableMultiInstanceBody.class);
      final var sourceElement =
          sourceProcessDefinition
              .getProcess()
              .getElementById(sourceElementId, ExecutableMultiInstanceBody.class);

      if (targetElement.getLoopCharacteristics().isSequential()
          != sourceElement.getLoopCharacteristics().isSequential()) {
        final String reason =
            String.format(
                ERROR_UPDATED_LOOP_CHARACTERISTICS,
                processInstanceKey,
                sourceElementId,
                targetElementId);
        throw new ProcessInstanceMigrationPreconditionFailedException(
            reason, RejectionType.INVALID_STATE);
      }
    }
  }

  /**
   * This precondition checks whether the given distribution is pending to prevent the scenario:
   *
   * <p>Partition 1 migrates a process instance that is subscribed to a message catch even. It
   * starts distributing the msg-sub migration command to partition 2. But the distribution
   * continues to be retried because of some random reason.
   *
   * <p>Partition 1 migrates that same process instance to a different target again before the
   * previous message subscription migration distribution completed. It starts distributing the
   * msg-sub migration command to partition 2 for this target as well. This continues to be retried
   * again.
   *
   * <p>But the order of these msg-sub migrate distribution commands is not guaranteed. When the
   * scenario mentioned above occurs, it could lead to data corruption on partition 2 (out of sync
   * with partition 1).
   *
   * <p>So we should not allow migrating the process instance if it's currently distributing a
   * migration command (e.g. msg sub migration distribution). As a result of introducing command
   * distribution to Process Instance Migration, a migration is no longer atomic, because
   * distribution is not.
   *
   * <p>To avoid migrating the instance during the period that the instance is already migrating, we
   * retrieve pending distributions of command distribution and reject migration if there are any
   * pending distribution for the given distribution key.
   *
   * @param distributionState the distribution state to check for pending distributions
   * @param distributionKey the distribution key of the distribution that is being checked
   * @param message the message to use in the exception if the distribution is pending
   */
  private static void requireNoPendingMigrationDistribution(
      final DistributionState distributionState, final long distributionKey, final String message) {
    if (distributionState.hasPendingDistribution(distributionKey)) {
      // We can't migrate until the previous migration has completed
      throw new ProcessInstanceMigrationPreconditionFailedException(
          message, RejectionType.INVALID_STATE);
    }
  }

  public static final class ProcessInstanceMigrationPreconditionFailedException
      extends RuntimeException {
    private final RejectionType rejectionType;

    public ProcessInstanceMigrationPreconditionFailedException(
        final String message, final RejectionType rejectionType) {
      super(message);
      this.rejectionType = rejectionType;
    }

    public RejectionType getRejectionType() {
      return rejectionType;
    }
  }
}
