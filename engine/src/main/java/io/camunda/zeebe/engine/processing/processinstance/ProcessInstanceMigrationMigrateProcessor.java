/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.engine.state.immutable.IncidentState.MISSING_INCIDENT;

import io.camunda.zeebe.auth.impl.TenantAuthorizationCheckerImpl;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.EventTrigger;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue.ProcessInstanceMigrationMappingInstructionValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ProcessInstanceMigrationMigrateProcessor
    implements TypedRecordProcessor<ProcessInstanceMigrationRecord> {

  private static final EnumSet<BpmnElementType> SUPPORTED_ELEMENT_TYPES =
      EnumSet.of(BpmnElementType.PROCESS, BpmnElementType.SERVICE_TASK);
  private static final Set<BpmnElementType> UNSUPPORTED_ELEMENT_TYPES =
      EnumSet.complementOf(SUPPORTED_ELEMENT_TYPES);

  private static final String ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND =
      "Expected to migrate process instance but no process instance found with key '%d'";
  private static final String ERROR_MESSAGE_PROCESS_DEFINITION_NOT_FOUND =
      "Expected to migrate process instance to process definition but no process definition found with key '%d'";
  private static final String ERROR_MESSAGE_DUPLICATE_SOURCE_ELEMENT_IDS =
      "Expected to migrate process instance '%s' but the mapping instructions contain duplicate source element ids '%s'.";
  private static final String ERROR_MESSAGE_EVENT_SUBPROCESS_NOT_SUPPORTED_IN_PROCESS_INSTANCE =
      "Expected to migrate process instance but process instance has an event subprocess. Process instances with event subprocesses cannot be migrated yet.";
  private static final String ERROR_MESSAGE_EVENT_SUBPROCESS_NOT_SUPPORTED_IN_TARGET_PROCESS =
      "Expected to migrate process instance but target process has an event subprocess. Target processes with event subprocesses cannot be migrated yet.";

  private static final long NO_PARENT = -1L;

  private static final Map<Class<? extends Exception>, RejectionType> MIGRATION_EXCEPTIONS =
      Map.ofEntries(
          Map.entry(UnsupportedElementMigrationException.class, RejectionType.INVALID_STATE),
          Map.entry(UnmappedActiveElementException.class, RejectionType.INVALID_STATE),
          Map.entry(ElementTypeChangedException.class, RejectionType.INVALID_STATE),
          Map.entry(ElementWithIncidentException.class, RejectionType.INVALID_STATE),
          Map.entry(ChangedElementFlowScopeException.class, RejectionType.INVALID_STATE),
          Map.entry(ChildProcessMigrationException.class, RejectionType.INVALID_STATE),
          Map.entry(NonExistingElementException.class, RejectionType.INVALID_ARGUMENT),
          Map.entry(
              EventSubscriptionMigrationNotSupportedException.class, RejectionType.INVALID_STATE),
          Map.entry(ConcurrentCommandException.class, RejectionType.INVALID_STATE),
          Map.entry(
              ProcessInstanceMigrationPreconditionFailedException.class,
              RejectionType.INVALID_STATE));

  private static final UnsafeBuffer NIL_VALUE = new UnsafeBuffer(MsgPackHelper.NIL);
  private final VariableRecord variableRecord = new VariableRecord().setValue(NIL_VALUE);

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final JobState jobState;
  private final VariableState variableState;
  private final IncidentState incidentState;
  private final EventScopeInstanceState eventScopeInstanceState;
  private final MessageState messageState;

  public ProcessInstanceMigrationMigrateProcessor(
      final Writers writers, final ProcessingState processingState) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    elementInstanceState = processingState.getElementInstanceState();
    processState = processingState.getProcessState();
    jobState = processingState.getJobState();
    variableState = processingState.getVariableState();
    incidentState = processingState.getIncidentState();
    eventScopeInstanceState = processingState.getEventScopeInstanceState();
    messageState = processingState.getMessageState();
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceMigrationRecord> command) {
    final ProcessInstanceMigrationRecord value = command.getValue();
    final long processInstanceKey = value.getProcessInstanceKey();
    final long targetProcessDefinitionKey = value.getTargetProcessDefinitionKey();
    final var mappingInstructions = value.getMappingInstructions();

    final ElementInstance processInstance = elementInstanceState.getInstance(processInstanceKey);

    if (processInstance == null) {
      final String reason =
          String.format(ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND, processInstanceKey);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, reason);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
      return;
    }

    final boolean isTenantAuthorized =
        TenantAuthorizationCheckerImpl.fromAuthorizationMap(command.getAuthorizations())
            .isAuthorized(processInstance.getValue().getTenantId());
    if (!isTenantAuthorized) {
      final String reason =
          String.format(ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND, processInstanceKey);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, reason);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
      return;
    }

    if (processInstance.getValue().getParentProcessInstanceKey() != NO_PARENT) {
      throw new ChildProcessMigrationException(processInstanceKey);
    }

    final DeployedProcess targetProcessDefinition =
        processState.getProcessByKeyAndTenant(
            targetProcessDefinitionKey, processInstance.getValue().getTenantId());
    if (targetProcessDefinition == null) {
      final String reason =
          String.format(ERROR_MESSAGE_PROCESS_DEFINITION_NOT_FOUND, targetProcessDefinitionKey);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, reason);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
      return;
    }

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
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_ARGUMENT, reason);
      rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, reason);
      return;
    }

    final DeployedProcess sourceProcessDefinition =
        processState.getProcessByKeyAndTenant(
            processInstance.getValue().getProcessDefinitionKey(),
            processInstance.getValue().getTenantId());
    mappingInstructions.forEach(
        instruction -> {
          final String sourceElementId = instruction.getSourceElementId();
          if (sourceProcessDefinition.getProcess().getElementById(sourceElementId) == null) {
            throw new NonExistingElementException(processInstanceKey, sourceElementId, "source");
          }

          final String targetElementId = instruction.getTargetElementId();
          if (targetProcessDefinition.getProcess().getElementById(targetElementId) == null) {
            throw new NonExistingElementException(processInstanceKey, targetElementId, "target");
          }
        });

    final boolean processInstanceHasEventSubprocess =
        !sourceProcessDefinition.getProcess().getEventSubprocesses().isEmpty();

    if (processInstanceHasEventSubprocess) {
      responseWriter.writeRejectionOnCommand(
          command,
          RejectionType.INVALID_STATE,
          ERROR_MESSAGE_EVENT_SUBPROCESS_NOT_SUPPORTED_IN_PROCESS_INSTANCE);
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_STATE,
          ERROR_MESSAGE_EVENT_SUBPROCESS_NOT_SUPPORTED_IN_PROCESS_INSTANCE);
      return;
    }

    final boolean targetProcessHasEventSubprocess =
        !targetProcessDefinition.getProcess().getEventSubprocesses().isEmpty();

    if (targetProcessHasEventSubprocess) {
      responseWriter.writeRejectionOnCommand(
          command,
          RejectionType.INVALID_STATE,
          ERROR_MESSAGE_EVENT_SUBPROCESS_NOT_SUPPORTED_IN_TARGET_PROCESS);
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_STATE,
          ERROR_MESSAGE_EVENT_SUBPROCESS_NOT_SUPPORTED_IN_TARGET_PROCESS);
      return;
    }

    final Map<String, String> mappedElementIds =
        mapElementIds(mappingInstructions, processInstance, targetProcessDefinition);

    // avoid stackoverflow using a queue to iterate over the descendants instead of recursion
    final var elementInstances = new ArrayDeque<>(List.of(processInstance));
    while (!elementInstances.isEmpty()) {
      final var elementInstance = elementInstances.poll();
      tryMigrateElementInstance(
          elementInstance, sourceProcessDefinition, targetProcessDefinition, mappedElementIds);
      final List<ElementInstance> children =
          elementInstanceState.getChildren(elementInstance.getKey());
      elementInstances.addAll(children);
    }

    stateWriter.appendFollowUpEvent(
        processInstanceKey, ProcessInstanceMigrationIntent.MIGRATED, value);
    responseWriter.writeEventOnCommand(
        processInstanceKey, ProcessInstanceMigrationIntent.MIGRATED, value, command);
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ProcessInstanceMigrationRecord> command, final Throwable error) {

    return MIGRATION_EXCEPTIONS.entrySet().stream()
        .filter(entry -> entry.getKey().isInstance(error))
        .findFirst()
        .map(
            entry -> {
              final var rejectionType = entry.getValue();
              rejectionWriter.appendRejection(command, rejectionType, error.getMessage());
              responseWriter.writeRejectionOnCommand(command, rejectionType, error.getMessage());

              return ProcessingError.EXPECTED_ERROR;
            })
        .orElse(ProcessingError.UNEXPECTED_ERROR);
  }

  private Map<String, String> mapElementIds(
      final List<ProcessInstanceMigrationMappingInstructionValue> mappingInstructions,
      final ElementInstance processInstance,
      final DeployedProcess targetProcessDefinition) {
    final Map<String, String> mappedElementIds =
        mappingInstructions.stream()
            .collect(
                Collectors.toMap(
                    ProcessInstanceMigrationMappingInstructionValue::getSourceElementId,
                    ProcessInstanceMigrationMappingInstructionValue::getTargetElementId));
    // users don't provide a mapping instruction for the bpmn process id
    mappedElementIds.put(
        processInstance.getValue().getBpmnProcessId(),
        BufferUtil.bufferAsString(targetProcessDefinition.getBpmnProcessId()));
    return mappedElementIds;
  }

  private void tryMigrateElementInstance(
      final ElementInstance elementInstance,
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> sourceElementIdToTargetElementId) {

    final var elementInstanceRecord = elementInstance.getValue();
    final long processInstanceKey = elementInstanceRecord.getProcessInstanceKey();

    if (UNSUPPORTED_ELEMENT_TYPES.contains(elementInstanceRecord.getBpmnElementType())) {
      throw new UnsupportedElementMigrationException(
          processInstanceKey,
          elementInstanceRecord.getElementId(),
          elementInstanceRecord.getBpmnElementType());
    }

    final String targetElementId =
        sourceElementIdToTargetElementId.get(elementInstanceRecord.getElementId());
    if (targetElementId == null) {
      throw new UnmappedActiveElementException(
          processInstanceKey, elementInstanceRecord.getElementId());
    }

    final boolean hasIncident =
        incidentState.getProcessInstanceIncidentKey(elementInstance.getKey()) != MISSING_INCIDENT
            || (elementInstance.getJobKey() > -1L
                && incidentState.getJobIncidentKey(elementInstance.getJobKey())
                    != MISSING_INCIDENT);

    if (hasIncident) {
      throw new ElementWithIncidentException(
          elementInstanceRecord.getProcessInstanceKey(), elementInstanceRecord.getElementId());
    }

    final BpmnElementType targetElementType =
        targetProcessDefinition.getProcess().getElementById(targetElementId).getElementType();
    if (elementInstanceRecord.getBpmnElementType() != targetElementType) {
      throw new ElementTypeChangedException(
          processInstanceKey,
          elementInstanceRecord.getElementId(),
          elementInstanceRecord.getBpmnElementType(),
          targetElementId,
          targetElementType);
    }

    final ElementInstance sourceFlowScopeElement =
        elementInstanceState.getInstance(elementInstanceRecord.getFlowScopeKey());
    if (sourceFlowScopeElement != null) {
      final DirectBuffer expectedFlowScopeId =
          sourceFlowScopeElement.getValue().getElementIdBuffer();
      final DirectBuffer actualFlowScopeId =
          targetProcessDefinition
              .getProcess()
              .getElementById(targetElementId)
              .getFlowScope()
              .getId();

      if (!expectedFlowScopeId.equals(actualFlowScopeId)) {
        throw new ChangedElementFlowScopeException(
            elementInstanceRecord.getProcessInstanceKey(),
            elementInstanceRecord.getElementId(),
            BufferUtil.bufferAsString(expectedFlowScopeId),
            BufferUtil.bufferAsString(actualFlowScopeId));
      }
    }

    final boolean hasBoundaryEventInSource =
        !sourceProcessDefinition
            .getProcess()
            .getElementById(elementInstanceRecord.getElementId(), ExecutableActivity.class)
            .getBoundaryEvents()
            .isEmpty();

    if (hasBoundaryEventInSource) {
      throw new EventSubscriptionMigrationNotSupportedException(
          elementInstanceRecord.getProcessInstanceKey(),
          elementInstanceRecord.getElementId(),
          "active");
    }

    final boolean hasBoundaryEventInTarget =
        !targetProcessDefinition
            .getProcess()
            .getElementById(targetElementId, ExecutableActivity.class)
            .getBoundaryEvents()
            .isEmpty();

    if (hasBoundaryEventInTarget) {
      throw new EventSubscriptionMigrationNotSupportedException(
          elementInstanceRecord.getProcessInstanceKey(),
          elementInstanceRecord.getElementId(),
          "target");
    }
    final EventTrigger eventTrigger =
        eventScopeInstanceState.peekEventTrigger(elementInstance.getKey());
    if (eventTrigger != null) {
      // An event trigger indicates a concurrent command. It is created when completing a job, or
      // triggering a timer/message/signal event.
      throw new ConcurrentCommandException(processInstanceKey);
    }

    if (elementInstance.getActiveSequenceFlows() > 0) {
      // An active sequence flow indicates a concurrent command. It is created when taking a
      // sequence flow and writing an ACTIVATE command for the next element.
      throw new ConcurrentCommandException(processInstanceKey);
    }

    requireNoStartEventInstanceForTargetProcess(
        elementInstance, targetProcessDefinition, messageState);

    stateWriter.appendFollowUpEvent(
        elementInstance.getKey(),
        ProcessInstanceIntent.ELEMENT_MIGRATED,
        elementInstanceRecord
            .setProcessDefinitionKey(targetProcessDefinition.getKey())
            .setBpmnProcessId(targetProcessDefinition.getBpmnProcessId())
            .setVersion(targetProcessDefinition.getVersion())
            .setElementId(targetElementId));

    if (elementInstance.getJobKey() > 0) {
      final var job = jobState.getJob(elementInstance.getJobKey());
      if (job != null) {
        stateWriter.appendFollowUpEvent(
            elementInstance.getJobKey(),
            JobIntent.MIGRATED,
            job.setProcessDefinitionKey(targetProcessDefinition.getKey())
                .setProcessDefinitionVersion(targetProcessDefinition.getVersion())
                .setBpmnProcessId(targetProcessDefinition.getBpmnProcessId())
                .setElementId(targetElementId));
      }
    }

    variableState
        .getVariablesLocal(elementInstance.getKey())
        .forEach(
            variable ->
                stateWriter.appendFollowUpEvent(
                    variable.key(),
                    VariableIntent.MIGRATED,
                    variableRecord
                        .setScopeKey(elementInstance.getKey())
                        .setName(variable.name())
                        .setProcessInstanceKey(elementInstance.getValue().getProcessInstanceKey())
                        .setProcessDefinitionKey(targetProcessDefinition.getKey())
                        .setBpmnProcessId(targetProcessDefinition.getBpmnProcessId())
                        .setTenantId(elementInstance.getValue().getTenantId())));
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
              """
              Expected to migrate process instance '%d' \
              but target process definition '%s' has an active instance triggered by a message start event with correlation key '%s'. \
              Only one instance per correlation key is allowed for message start events.""",
              processInstance.getKey(),
              targetProcessDefinition.getKey(),
              BufferUtil.bufferAsString(correlationKey));
      throw new ProcessInstanceMigrationPreconditionFailedException(reason);
    }
  }

  /**
   * Exception that can be thrown during the migration of a process instance, in case the engine
   * attempts to migrate an element which is not supported at this time.
   */
  private static final class UnsupportedElementMigrationException extends RuntimeException {
    UnsupportedElementMigrationException(
        final long processInstanceKey,
        final String elementId,
        final BpmnElementType bpmnElementType) {
      super(
          String.format(
              """
              Expected to migrate process instance '%s' \
              but active element with id '%s' has an unsupported type. \
              The migration of a %s is not supported.""",
              processInstanceKey, elementId, bpmnElementType));
    }
  }

  /**
   * Exception that can be thrown during the migration of a process instance, in case the engine
   * attempts to migrate an element which is not mapped.
   */
  private static final class UnmappedActiveElementException extends RuntimeException {
    UnmappedActiveElementException(final long processInstanceKey, final String elementId) {
      super(
          String.format(
              """
              Expected to migrate process instance '%s' \
              but no mapping instruction defined for active element with id '%s'. \
              Elements cannot be migrated without a mapping.""",
              processInstanceKey, elementId));
    }
  }

  /**
   * Exception that can be thrown during the migration of a process instance, in case any of the
   * mapping instructions of the command refer to a source and a target element with different
   * element type, or different event type.
   */
  private static final class ElementTypeChangedException extends RuntimeException {
    ElementTypeChangedException(
        final long processInstanceKey,
        final String elementId,
        final BpmnElementType bpmnElementType,
        final String targetElementId,
        final BpmnElementType targetBpmnElementType) {
      super(
          String.format(
              """
              Expected to migrate process instance '%s' \
              but active element with id '%s' and type '%s' is mapped to \
              an element with id '%s' and different type '%s'. \
              Elements must be mapped to elements of the same type.""",
              processInstanceKey,
              elementId,
              bpmnElementType,
              targetElementId,
              targetBpmnElementType));
    }
  }

  /**
   * Exception that can be thrown during the migration of a process instance, in case the engine
   * attempts to migrate an element that has an incident.
   */
  private static final class ElementWithIncidentException extends RuntimeException {
    ElementWithIncidentException(final long processInstanceKey, final String elementId) {
      super(
          String.format(
              """
              Expected to migrate process instance '%s' \
              but active element with id '%s' has an incident. \
              Elements cannot be migrated with an incident yet. \
              Please retry migration after resolving the incident.""",
              processInstanceKey, elementId));
    }
  }

  /**
   * Exception that can be thrown during the migration of a process instance, in case the engine
   * attempts to change element flow scope in the target process definition.
   */
  private static final class ChangedElementFlowScopeException extends RuntimeException {
    ChangedElementFlowScopeException(
        final long processInstanceKey,
        final String elementId,
        final String expectedFlowScopeId,
        final String actualFlowScopeId) {
      super(
          String.format(
              """
              Expected to migrate process instance '%s' \
              but the flow scope of active element with id '%s' is changed. \
              The flow scope of the active element is expected to be '%s' but was '%s'. \
              The flow scope of an element cannot be changed during migration yet.""",
              processInstanceKey, elementId, expectedFlowScopeId, actualFlowScopeId));
    }
  }

  /**
   * Exception that can be thrown during the migration of a process instance, in case any of the
   * process instance is a child process instance.
   */
  private static final class ChildProcessMigrationException extends RuntimeException {
    ChildProcessMigrationException(final long processInstanceKey) {
      super(
          String.format(
              """
              Expected to migrate process instance '%s' \
              but process instance is a child process instance. \
              Child process instances cannot be migrated.""",
              processInstanceKey));
    }
  }

  /**
   * Exception that can be thrown during the migration of a process instance, in following cases:
   *
   * <p>
   *
   * <ul>
   *   <li>A mapping instruction contains a source element id that does not exist in the source
   *       process definition.
   *   <li>A mapping instruction contains a target element id that does not exist in the target
   *       process definition.
   * </ul>
   *
   * <p>
   */
  private static final class NonExistingElementException extends RuntimeException {
    NonExistingElementException(
        final long processInstanceKey, final String elementId, final String elementSource) {
      super(
          String.format(
              """
              Expected to migrate process instance '%s' \
              but mapping instructions contain a non-existing %s element id '%s'. \
              Elements provided in mapping instructions must exist \
              in the %s process definition.""",
              processInstanceKey, elementSource, elementId, elementSource));
    }
  }

  /**
   * Exception that can be thrown during the migration of a process instance, in following cases:
   *
   * <p>
   *
   * <ul>
   *   <li>Process instance has an active element with a boundary event
   *   <li>Target process definition has an element with a boundary event
   * </ul>
   *
   * <p>
   */
  private static final class EventSubscriptionMigrationNotSupportedException
      extends RuntimeException {
    EventSubscriptionMigrationNotSupportedException(
        final long processInstanceKey, final String elementId, final String source) {
      super(
          String.format(
              """
              Expected to migrate process instance '%s' \
              but %s element with id '%s' has a boundary event. \
              Migrating %s elements with boundary events is not possible yet.""",
              processInstanceKey, source, elementId, source));
    }
  }

  /**
   * Exception that can be thrown during the migration of a process instance, in case the engine
   * processes another command concurrently for the process instance, for example, a job complete, a
   * timer trigger, or a message correlation. Since the concurrent command modifies the process
   * instance, it is not safe to apply the migration in between.
   */
  private static final class ConcurrentCommandException extends RuntimeException {
    ConcurrentCommandException(final long processInstanceKey) {
      super(
          String.format(
              """
              Expected to migrate process instance '%s' \
              but a concurrent command was executed on the process instance. \
              Please retry the migration.""",
              processInstanceKey));
    }
  }

  private static final class ProcessInstanceMigrationPreconditionFailedException
      extends RuntimeException {
    ProcessInstanceMigrationPreconditionFailedException(final String message) {
      super(message);
    }
  }
}
