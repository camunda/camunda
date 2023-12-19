/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.engine.state.immutable.IncidentState.MISSING_INCIDENT;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventSupplier;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
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

  private static final long NO_PARENT = -1L;
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
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;

  public ProcessInstanceMigrationMigrateProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final BpmnBehaviors bpmnBehaviors) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    elementInstanceState = processingState.getElementInstanceState();
    processState = processingState.getProcessState();
    jobState = processingState.getJobState();
    variableState = processingState.getVariableState();
    incidentState = processingState.getIncidentState();
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

    if (processInstance.getValue().getParentProcessInstanceKey() != NO_PARENT) {
      throw new ChildProcessMigrationException(processInstanceKey);
    }

    final DeployedProcess processDefinition =
        processState.getProcessByKeyAndTenant(
            targetProcessDefinitionKey, processInstance.getValue().getTenantId());
    if (processDefinition == null) {
      final String reason =
          String.format(ERROR_MESSAGE_PROCESS_DEFINITION_NOT_FOUND, targetProcessDefinitionKey);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, reason);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
      return;
    }

    final Map<String, String> mappedElementIds =
        mapElementIds(mappingInstructions, processInstance, processDefinition);

    // avoid stackoverflow using a queue to iterate over the descendants instead of recursion
    final var elementInstances = new ArrayDeque<>(List.of(processInstance));
    while (!elementInstances.isEmpty()) {
      final var elementInstance = elementInstances.poll();
      tryMigrateElementInstance(elementInstance, processDefinition, mappedElementIds);
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
    if (error instanceof final UnsupportedElementMigrationException e) {
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, e.getMessage());
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, e.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    }
    if (error instanceof final UnmappedActiveElementException e) {
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, e.getMessage());
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, e.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    }
    if (error instanceof final IncorrectMappingException e) {
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, e.getMessage());
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, e.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    }
    if (error instanceof final ElementWithIncidentException e) {
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, e.getMessage());
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, e.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    }
    if (error instanceof final ChangedElementFlowScopeException e) {
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, e.getMessage());
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, e.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    }
    if (error instanceof final ChildProcessMigrationException e) {
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, e.getMessage());
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, e.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    }

    return ProcessingError.UNEXPECTED_ERROR;
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
      final DeployedProcess processDefinition,
      final Map<String, String> sourceElementIdToTargetElementId) {

    final var elementInstanceRecord = elementInstance.getValue();
    if (UNSUPPORTED_ELEMENT_TYPES.contains(elementInstanceRecord.getBpmnElementType())) {
      throw new UnsupportedElementMigrationException(
          elementInstanceRecord.getProcessInstanceKey(),
          elementInstanceRecord.getElementId(),
          elementInstanceRecord.getBpmnElementType());
    }

    final String targetElementId =
        sourceElementIdToTargetElementId.get(elementInstanceRecord.getElementId());
    if (targetElementId == null) {
      throw new UnmappedActiveElementException(
          elementInstanceRecord.getProcessInstanceKey(), elementInstanceRecord.getElementId());
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
        processDefinition.getProcess().getElementById(targetElementId).getElementType();
    if (elementInstanceRecord.getBpmnElementType() != targetElementType) {
      throw new IncorrectMappingException(
          elementInstanceRecord.getProcessInstanceKey(),
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
          processDefinition.getProcess().getElementById(targetElementId).getFlowScope().getId();

      if (!expectedFlowScopeId.equals(actualFlowScopeId)) {
        throw new ChangedElementFlowScopeException(
            elementInstanceRecord.getProcessInstanceKey(),
            elementInstanceRecord.getElementId(),
            BufferUtil.bufferAsString(expectedFlowScopeId),
            BufferUtil.bufferAsString(actualFlowScopeId));
      }
    }

    eventSubscriptionBehavior.unsubscribeFromEvents(elementInstance.getKey());

    stateWriter.appendFollowUpEvent(
        elementInstance.getKey(),
        ProcessInstanceIntent.ELEMENT_MIGRATED,
        elementInstanceRecord
            .setProcessDefinitionKey(processDefinition.getKey())
            .setBpmnProcessId(processDefinition.getBpmnProcessId())
            .setVersion(processDefinition.getVersion())
            .setElementId(targetElementId));

    if (elementInstance.getJobKey() > 0) {
      final var job = jobState.getJob(elementInstance.getJobKey());
      if (job != null) {
        stateWriter.appendFollowUpEvent(
            elementInstance.getJobKey(),
            JobIntent.MIGRATED,
            job.setProcessDefinitionKey(processDefinition.getKey())
                .setProcessDefinitionVersion(processDefinition.getVersion())
                .setBpmnProcessId(processDefinition.getBpmnProcessId())
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
                        .setProcessDefinitionKey(processDefinition.getKey())
                        .setBpmnProcessId(processDefinition.getBpmnProcessId())
                        .setTenantId(elementInstance.getValue().getTenantId())));

    final var targetElement =
        processDefinition
            .getProcess()
            .getElementById(targetElementId, ExecutableCatchEventSupplier.class);
    final var bpmnElementContext = new BpmnElementContextImpl();
    bpmnElementContext.init(
        elementInstance.getKey(), elementInstance.getValue(), elementInstance.getState());
    eventSubscriptionBehavior.subscribeToEvents(targetElement, bpmnElementContext);
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
  private static final class IncorrectMappingException extends RuntimeException {
    IncorrectMappingException(
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
              Active elements must be mapped to the same type.""",
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
}
