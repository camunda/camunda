/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationPreconditions.*;
import static io.camunda.zeebe.engine.state.immutable.IncidentState.MISSING_INCIDENT;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
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
<<<<<<< HEAD
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
=======
import io.camunda.zeebe.engine.state.immutable.MessageState;
>>>>>>> 71227d8c (feat: backport of #27114)
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue.ProcessInstanceMigrationMappingInstructionValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class ProcessInstanceMigrationMigrateProcessor
    implements TypedRecordProcessor<ProcessInstanceMigrationRecord> {

  private static final Logger LOG = Loggers.ENGINE_PROCESSING_LOGGER;
  private static final UnsafeBuffer NIL_VALUE = new UnsafeBuffer(MsgPackHelper.NIL);
  private final VariableRecord variableRecord = new VariableRecord().setValue(NIL_VALUE);

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final JobState jobState;
  private final UserTaskState userTaskState;
  private final VariableState variableState;
  private final IncidentState incidentState;
  private final EventScopeInstanceState eventScopeInstanceState;
<<<<<<< HEAD
  private final ProcessMessageSubscriptionState processMessageSubscriptionState;
  private final CatchEventBehavior catchEventBehavior;
=======
  private final MessageState messageState;
  private final ProcessInstanceMigrationCatchEventBehaviour migrationCatchEventBehaviour;
>>>>>>> 71227d8c (feat: backport of #27114)

  public ProcessInstanceMigrationMigrateProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final BpmnBehaviors bpmnBehaviors) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    elementInstanceState = processingState.getElementInstanceState();
    processState = processingState.getProcessState();
    jobState = processingState.getJobState();
    userTaskState = processingState.getUserTaskState();
    variableState = processingState.getVariableState();
    incidentState = processingState.getIncidentState();
    eventScopeInstanceState = processingState.getEventScopeInstanceState();
<<<<<<< HEAD
    processMessageSubscriptionState = processingState.getProcessMessageSubscriptionState();
    catchEventBehavior = bpmnBehaviors.catchEventBehavior();
=======
    messageState = processingState.getMessageState();

    migrationCatchEventBehaviour =
        new ProcessInstanceMigrationCatchEventBehaviour(
            processingState.getProcessMessageSubscriptionState(),
            bpmnBehaviors.catchEventBehavior(),
            writers.command(),
            commandDistributionBehavior,
            processingState.getDistributionState(),
            stateWriter,
            partitionId,
            routingInfo);
>>>>>>> 71227d8c (feat: backport of #27114)
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceMigrationRecord> command) {
    final ProcessInstanceMigrationRecord value = command.getValue();
    final long processInstanceKey = value.getProcessInstanceKey();
    final long targetProcessDefinitionKey = value.getTargetProcessDefinitionKey();
    final var mappingInstructions = value.getMappingInstructions();
    final ElementInstance processInstance = elementInstanceState.getInstance(processInstanceKey);

    requireNonNullProcessInstance(processInstance, processInstanceKey);
    requireAuthorizedTenant(
        command.getAuthorizations(), processInstance.getValue().getTenantId(), processInstanceKey);
    requireNonDuplicateSourceElementIds(mappingInstructions, processInstanceKey);

    final DeployedProcess targetProcessDefinition =
        processState.getProcessByKeyAndTenant(
            targetProcessDefinitionKey, processInstance.getValue().getTenantId());
    final DeployedProcess sourceProcessDefinition =
        processState.getProcessByKeyAndTenant(
            processInstance.getValue().getProcessDefinitionKey(),
            processInstance.getValue().getTenantId());

    requireNonNullTargetProcessDefinition(targetProcessDefinition, targetProcessDefinitionKey);
    requireNoStartEventInstanceForTargetProcess(
        processInstance, targetProcessDefinition, messageState);
    requireReferredElementsExist(
        sourceProcessDefinition, targetProcessDefinition, mappingInstructions, processInstanceKey);
    requireNoEventSubprocess(sourceProcessDefinition, targetProcessDefinition);

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

    if (error instanceof final ProcessInstanceMigrationPreconditionFailedException e) {
      rejectionWriter.appendRejection(command, e.getRejectionType(), e.getMessage());
      responseWriter.writeRejectionOnCommand(command, e.getRejectionType(), e.getMessage());
      return ProcessingError.EXPECTED_ERROR;

    } else if (error instanceof final SafetyCheckFailedException e) {
      LOG.error(e.getMessage(), e);
      rejectionWriter.appendRejection(command, RejectionType.PROCESSING_ERROR, e.getMessage());
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.PROCESSING_ERROR, e.getMessage());
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
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> sourceElementIdToTargetElementId) {

    final var elementInstanceRecord = elementInstance.getValue();
    final long processInstanceKey = elementInstanceRecord.getProcessInstanceKey();
    final var elementId = elementInstanceRecord.getElementId();

    requireSupportedElementType(elementInstanceRecord, processInstanceKey);

    final String targetElementId = sourceElementIdToTargetElementId.get(elementId);
    requireNonNullTargetElementId(targetElementId, processInstanceKey, elementId);
    requireSameElementType(
        targetProcessDefinition, targetElementId, elementInstanceRecord, processInstanceKey);
    requireSameUserTaskImplementation(
        targetProcessDefinition, targetElementId, elementInstance, processInstanceKey);
    requireUnchangedFlowScope(
        elementInstanceState, elementInstanceRecord, targetProcessDefinition, targetElementId);
    requireNoBoundaryEventInSource(
        sourceProcessDefinition, elementInstanceRecord, EnumSet.of(BpmnEventType.MESSAGE));
    requireNoBoundaryEventInTarget(
        targetProcessDefinition,
        targetElementId,
        elementInstanceRecord,
        EnumSet.of(BpmnEventType.MESSAGE));
    requireNoConcurrentCommand(eventScopeInstanceState, elementInstance, processInstanceKey);

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
      if (job == null) {
        throw new SafetyCheckFailedException(
            String.format(
                """
                Expected to migrate a job for process instance with key '%d', \
                but could not find job with key '%d'. \
                Please report this as a bug""",
                processInstanceKey, elementInstance.getUserTaskKey()));
      }
      stateWriter.appendFollowUpEvent(
          elementInstance.getJobKey(),
          JobIntent.MIGRATED,
          job.setProcessDefinitionKey(targetProcessDefinition.getKey())
              .setProcessDefinitionVersion(targetProcessDefinition.getVersion())
              .setBpmnProcessId(targetProcessDefinition.getBpmnProcessId())
              .setElementId(targetElementId));
    }

    final long processIncidentKey =
        incidentState.getProcessInstanceIncidentKey(elementInstance.getKey());
    if (processIncidentKey != MISSING_INCIDENT) {
      appendIncidentMigratedEvent(
          processIncidentKey, targetProcessDefinition, targetElementId, processInstanceKey);
    }

    final var jobIncidentKey = incidentState.getJobIncidentKey(elementInstance.getJobKey());
    if (jobIncidentKey != MISSING_INCIDENT) {
      appendIncidentMigratedEvent(
          jobIncidentKey, targetProcessDefinition, targetElementId, processInstanceKey);
    }

    if (elementInstance.getUserTaskKey() > 0) {
      final var userTask = userTaskState.getUserTask(elementInstance.getUserTaskKey());
      if (userTask == null) {
        throw new SafetyCheckFailedException(
            String.format(
                """
                Expected to migrate a user task for process instance with key '%d', \
                but could not find user task with key '%d'. \
                Please report this as a bug""",
                processInstanceKey, elementInstance.getUserTaskKey()));
      }
      stateWriter.appendFollowUpEvent(
          elementInstance.getUserTaskKey(),
          UserTaskIntent.MIGRATED,
          userTask
              .setProcessDefinitionKey(targetProcessDefinition.getKey())
              .setProcessDefinitionVersion(targetProcessDefinition.getVersion())
              .setBpmnProcessId(targetProcessDefinition.getBpmnProcessId())
              .setElementId(targetElementId)
              .setVariables(NIL_VALUE));
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

    if (ProcessInstanceIntent.ELEMENT_ACTIVATING != elementInstance.getState()) {
      // Elements in ACTIVATING state haven't subscribed to events yet. We shouldn't subscribe such
      // elements to events during migration either. For elements that have been ACTIVATED, a
      // subscription would already exist if needed. So, we want to deal with the expected event
      // subscriptions. See: https://github.com/camunda/camunda/issues/19212
      handleCatchEvents(
          elementInstance,
          targetProcessDefinition,
          sourceElementIdToTargetElementId,
          elementInstanceRecord,
          targetElementId,
          processInstanceKey,
          elementId);
    }
  }

  /**
   * Unsubscribes the element instance from unmapped catch events in the source process, and
   * subscribes it to unmapped catch events in the target process.
   *
   * <p>In the future, this method will also migrate event subscriptions if mappings are provided
   * for the associated catch events.
   */
  private void handleCatchEvents(
      final ElementInstance elementInstance,
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> sourceElementIdToTargetElementId,
      final ProcessInstanceRecord elementInstanceRecord,
      final String targetElementId,
      final long processInstanceKey,
      final String elementId) {
    final var context = new BpmnElementContextImpl();
    context.init(elementInstance.getKey(), elementInstanceRecord, elementInstance.getState());
    final var targetElement =
        targetProcessDefinition
            .getProcess()
            .getElementById(targetElementId, ExecutableActivity.class);
    final List<DirectBuffer> subscribedMessageNames = new ArrayList<>();
    catchEventBehavior
        .subscribeToEvents(
            context,
            targetElement,
            catchEvent -> {
              final var element = catchEvent.element();
              final String targetCatchEventId = BufferUtil.bufferAsString(element.getId());
              requireNoMappedCatchEventsInTarget(
                  sourceElementIdToTargetElementId,
                  targetCatchEventId,
                  processInstanceKey,
                  elementId,
                  targetElementId);
              if (element.isMessage()) {
                requireNoSubscriptionForMessage(
                    elementInstance,
                    catchEvent.messageName(),
                    elementInstanceRecord.getTenantId(),
                    targetCatchEventId);
                subscribedMessageNames.add(catchEvent.messageName());
              }
              return true;
            })
        .ifLeft(
            failure -> {
              throw new ProcessInstanceMigrationPreconditionFailedException(
                      """
                  Expected to migrate process instance '%s' \
                  but active element with id '%s' is mapped to element with id '%s' \
                  that must be subscribed to a message catch event. %s"""
                      .formatted(
                          processInstanceKey, elementId, targetElementId, failure.getMessage()),
                  RejectionType.INVALID_STATE);
            });

    catchEventBehavior.unsubscribeFromMessageEvents(
        elementInstance.getKey(),
        subscription -> {
          if (subscribedMessageNames.contains(subscription.getRecord().getMessageNameBuffer())) {
            // We just subscribed to this message for this migration, we don't want to undo that
            return false;
          }
          final var catchEventId = subscription.getRecord().getElementId();
          requireNoMappedCatchEventsInSource(
              sourceElementIdToTargetElementId, catchEventId, processInstanceKey, elementId);
          return true;
        });
  }

  private void appendIncidentMigratedEvent(
      final long incidentKey,
      final DeployedProcess targetProcessDefinition,
      final String targetElementId,
      final long processInstanceKey) {
    final var incidentRecord = incidentState.getIncidentRecord(incidentKey);
    if (incidentRecord == null) {
      throw new SafetyCheckFailedException(
          String.format(
              """
              Expected to migrate a user task for process instance with key '%d', \
              but could not find incident with key '%d'. \
              Please report this as a bug""",
              processInstanceKey, incidentKey));
    }
    stateWriter.appendFollowUpEvent(
        incidentKey,
        IncidentIntent.MIGRATED,
        incidentRecord
            .setProcessDefinitionKey(targetProcessDefinition.getKey())
            .setBpmnProcessId(targetProcessDefinition.getBpmnProcessId())
            .setElementId(BufferUtil.wrapString(targetElementId)));
  }

  private void requireNoSubscriptionForMessage(
      final ElementInstance elementInstance,
      final DirectBuffer messageName,
      final String tenantId,
      final String targetCatchEventId) {
    final boolean existSubscriptionForMessageName =
        processMessageSubscriptionState.existSubscriptionForElementInstance(
            elementInstance.getKey(), messageName, tenantId);
    ProcessInstanceMigrationPreconditions.requireNoSubscriptionForMessage(
        existSubscriptionForMessageName, elementInstance, messageName, targetCatchEventId);
  }

  /**
   * Exception that can be thrown when a safety check has failed during migration. It's likely that
   * a bug is present when this is thrown.
   */
  public static final class SafetyCheckFailedException extends RuntimeException {

    public SafetyCheckFailedException(final String message) {
      super(message);
    }
  }
}
