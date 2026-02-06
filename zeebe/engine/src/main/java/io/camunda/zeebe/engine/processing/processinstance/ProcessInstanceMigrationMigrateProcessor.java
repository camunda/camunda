/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationPreconditions.*;
import static io.camunda.zeebe.engine.state.immutable.IncidentState.MISSING_INCIDENT;
import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.AD_HOC_SUB_PROCESS_ELEMENTS;
import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder;
import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
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
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
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
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue.ProcessInstanceMigrationMappingInstructionValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private final MessageState messageState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final BpmnBehaviors bpmnBehaviors;
  private final ProcessInstanceMigrationCatchEventBehaviour migrationCatchEventBehaviour;
  private final KeyGenerator keyGenerator;

  public ProcessInstanceMigrationMigrateProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final BpmnBehaviors bpmnBehaviors,
      final CommandDistributionBehavior commandDistributionBehavior,
      final int partitionId,
      final RoutingInfo routingInfo,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator) {
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
    messageState = processingState.getMessageState();
    this.authCheckBehavior = authCheckBehavior;
    this.bpmnBehaviors = bpmnBehaviors;
    this.keyGenerator = keyGenerator;

    migrationCatchEventBehaviour =
        new ProcessInstanceMigrationCatchEventBehaviour(
            processingState.getProcessMessageSubscriptionState(),
            bpmnBehaviors.catchEventBehavior(),
            bpmnBehaviors.compensationSubscriptionBehaviour(),
            writers.command(),
            commandDistributionBehavior,
            processingState.getDistributionState(),
            stateWriter,
            partitionId,
            routingInfo);
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceMigrationRecord> command) {
    final ProcessInstanceMigrationRecord value = command.getValue();
    final long processInstanceKey = value.getProcessInstanceKey();
    final long targetProcessDefinitionKey = value.getTargetProcessDefinitionKey();
    final var mappingInstructions = value.getMappingInstructions();
    final ElementInstance processInstance = elementInstanceState.getInstance(processInstanceKey);

    requireNonNullProcessInstance(processInstance, processInstanceKey);
    final ProcessInstanceRecord processInstanceRecord = processInstance.getValue();

    final var authorizationRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.UPDATE_PROCESS_INSTANCE)
            .tenantId(processInstanceRecord.getTenantId())
            .addResourceId(processInstanceRecord.getBpmnProcessId())
            .build();
    final var isAuthorized = authCheckBehavior.isAuthorizedOrInternalCommand(authorizationRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      final String errorMessage =
          RejectionType.NOT_FOUND.equals(rejection.type())
              ? AuthorizationCheckBehavior.NOT_FOUND_ERROR_MESSAGE.formatted(
                  "migrate a process instance",
                  processInstanceRecord.getProcessInstanceKey(),
                  "such process instance")
              : rejection.reason();
      enrichRejectionCommand(command, processInstanceRecord);
      rejectionWriter.appendRejection(command, rejection.type(), errorMessage);
      responseWriter.writeRejectionOnCommand(command, rejection.type(), errorMessage);
      return;
    }

    requireNonDuplicateSourceElementIds(mappingInstructions, processInstanceKey);

    final DeployedProcess targetProcessDefinition =
        processState.getProcessByKeyAndTenant(
            targetProcessDefinitionKey, processInstanceRecord.getTenantId());
    final DeployedProcess sourceProcessDefinition =
        processState.getProcessByKeyAndTenant(
            processInstanceRecord.getProcessDefinitionKey(), processInstanceRecord.getTenantId());

    requireNonNullTargetProcessDefinition(targetProcessDefinition, targetProcessDefinitionKey);
    requireNoStartEventInstanceForTargetProcess(
        processInstance, targetProcessDefinition, messageState);
    requireReferredElementsExist(
        sourceProcessDefinition, targetProcessDefinition, mappingInstructions, processInstanceKey);

    final Map<String, String> mappedElementIds =
        mapElementIds(
            mappingInstructions, processInstance, sourceProcessDefinition, targetProcessDefinition);

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

    value.setTenantId(processInstanceRecord.getTenantId());
    value.setRootProcessInstanceKey(processInstanceRecord.getRootProcessInstanceKey());
    value.setProcessDefinitionKey(processInstanceRecord.getProcessDefinitionKey());
    stateWriter.appendFollowUpEvent(
        processInstanceKey, ProcessInstanceMigrationIntent.MIGRATED, value);
    responseWriter.writeEventOnCommand(
        processInstanceKey, ProcessInstanceMigrationIntent.MIGRATED, value, command);
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ProcessInstanceMigrationRecord> command, final Throwable error) {

    if (error instanceof final ProcessInstanceMigrationPreconditionFailedException e) {
      enrichRejectionCommand(command);
      rejectionWriter.appendRejection(command, e.getRejectionType(), e.getMessage());
      responseWriter.writeRejectionOnCommand(command, e.getRejectionType(), e.getMessage());
      return ProcessingError.EXPECTED_ERROR;

    } else if (error instanceof final SafetyCheckFailedException e) {
      LOG.error(e.getMessage(), e);
      enrichRejectionCommand(command);
      rejectionWriter.appendRejection(command, RejectionType.PROCESSING_ERROR, e.getMessage());
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.PROCESSING_ERROR, e.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    }

    return ProcessingError.UNEXPECTED_ERROR;
  }

  /**
   * Enriches the command value with fields from the process instance record to ensure rejection
   * records have the proper context for audit logs export.
   */
  private void enrichRejectionCommand(
      final TypedRecord<ProcessInstanceMigrationRecord> command,
      final ProcessInstanceRecord processInstanceRecord) {
    command.getValue().setTenantId(processInstanceRecord.getTenantId());
    command.getValue().setRootProcessInstanceKey(processInstanceRecord.getRootProcessInstanceKey());
  }

  private void enrichRejectionCommand(final TypedRecord<ProcessInstanceMigrationRecord> command) {
    final var processInstanceKey = command.getValue().getProcessInstanceKey();
    final var processInstance = elementInstanceState.getInstance(processInstanceKey);
    if (processInstance != null) {
      enrichRejectionCommand(command, processInstance.getValue());
    } else {
      LOG.debug(
          "Could not enrich rejection command for process instance migration with key '{}', because the process instance was not found.",
          processInstanceKey);
    }
  }

  private Map<String, String> mapElementIds(
      final List<ProcessInstanceMigrationMappingInstructionValue> mappingInstructions,
      final ElementInstance processInstance,
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition) {
    final Map<String, String> mappedElementIds =
        mappingInstructions.stream()
            .collect(
                Collectors.toMap(
                    ProcessInstanceMigrationMappingInstructionValue::getSourceElementId,
                    ProcessInstanceMigrationMappingInstructionValue::getTargetElementId));

    mappingInstructions.stream()
        .filter(
            instruction ->
                isAdHocRelatedProcess(sourceProcessDefinition, instruction.getSourceElementId()))
        .forEach(
            instruction ->
                mappedElementIds.putIfAbsent(
                    instruction.getSourceElementId() + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX,
                    instruction.getTargetElementId()
                        + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX));

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

    requireSupportedElementType(elementInstanceRecord, processInstanceKey, sourceProcessDefinition);

    final String targetElementId = sourceElementIdToTargetElementId.get(elementId);
    requireNonNullTargetElementId(targetElementId, processInstanceKey, elementId);
    requireSameElementType(
        targetProcessDefinition, targetElementId, elementInstance, processInstanceKey);
    final boolean isUserTaskConversion =
        requireSupportedUserTaskImplementation(
            sourceProcessDefinition,
            targetProcessDefinition,
            targetElementId,
            elementInstance,
            processInstanceKey);
    requireUnchangedFlowScope(
        elementInstanceState, elementInstanceRecord, targetProcessDefinition, targetElementId);
    requireNoEventSubprocessInSource(
        sourceProcessDefinition,
        elementInstanceRecord,
        EnumSet.of(
            BpmnEventType.MESSAGE,
            BpmnEventType.TIMER,
            BpmnEventType.SIGNAL,
            BpmnEventType.ERROR,
            BpmnEventType.ESCALATION,
            BpmnEventType.CONDITIONAL));
    requireNoEventSubprocessInTarget(
        targetProcessDefinition,
        targetElementId,
        elementInstanceRecord,
        EnumSet.of(
            BpmnEventType.MESSAGE,
            BpmnEventType.TIMER,
            BpmnEventType.SIGNAL,
            BpmnEventType.ERROR,
            BpmnEventType.ESCALATION,
            BpmnEventType.CONDITIONAL));
    requireNoBoundaryEventInSource(
        sourceProcessDefinition,
        elementInstanceRecord,
        EnumSet.of(
            BpmnEventType.MESSAGE,
            BpmnEventType.TIMER,
            BpmnEventType.SIGNAL,
            BpmnEventType.ERROR,
            BpmnEventType.ESCALATION,
            BpmnEventType.COMPENSATION,
            BpmnEventType.CONDITIONAL));
    requireNoBoundaryEventInTarget(
        targetProcessDefinition,
        targetElementId,
        elementInstanceRecord,
        EnumSet.of(
            BpmnEventType.MESSAGE,
            BpmnEventType.TIMER,
            BpmnEventType.SIGNAL,
            BpmnEventType.ERROR,
            BpmnEventType.ESCALATION,
            BpmnEventType.COMPENSATION,
            BpmnEventType.CONDITIONAL));
    requireMappedCatchEventsToStayAttachedToSameElement(
        processInstanceKey,
        sourceProcessDefinition,
        targetProcessDefinition,
        elementId,
        targetElementId,
        sourceElementIdToTargetElementId);
    requireNoDuplicateTargetsInCatchEventMappings(
        processInstanceKey, sourceProcessDefinition, elementId, sourceElementIdToTargetElementId);
    requireNoCatchEventMappingToChangeEventType(
        processInstanceKey,
        sourceElementIdToTargetElementId,
        sourceProcessDefinition,
        targetProcessDefinition,
        elementId);
    requireSameMultiInstanceLoopCharacteristics(
        sourceProcessDefinition,
        elementId,
        targetProcessDefinition,
        targetElementId,
        processInstanceKey);
    requireNoConcurrentCommand(
        eventScopeInstanceState, elementInstanceState, elementInstance, processInstanceKey);

    final var updatedElementInstanceRecord =
        getUpdatedElementInstanceRecord(elementInstance, targetProcessDefinition, targetElementId);

    stateWriter.appendFollowUpEvent(
        elementInstance.getKey(),
        ProcessInstanceIntent.ELEMENT_MIGRATED,
        updatedElementInstanceRecord);

    final Set<ExecutableSequenceFlow> sequenceFlows =
        getSequenceFlowsToMigrate(
            sourceProcessDefinition,
            targetProcessDefinition,
            sourceElementIdToTargetElementId,
            elementInstance);

    // we chose to loop through the sequence flows again despite its redundancy to keep preventive
    // validation structure and write events after all validations passed
    requireNoMultipleActiveSequenceFlowsMappedToSameTarget(
        sequenceFlows, sourceElementIdToTargetElementId, processInstanceKey);

    sequenceFlows.forEach(
        sequenceFlow -> {
          // use the original element instance record to fill existing sequence flow data
          deleteTakenSequenceFlow(elementInstanceRecord, sequenceFlow, elementInstance.getKey());

          final var targetSequenceFlowId =
              getTargetSequenceFlowId(sourceElementIdToTargetElementId, sequenceFlow);
          // use updated element instance record to fill new sequence flow data
          takeNewSequenceFlow(
              updatedElementInstanceRecord,
              sequenceFlow,
              elementInstance.getKey(),
              targetSequenceFlowId);
        });

    if (elementInstance.getJobKey() > 0 && !isUserTaskConversion) {
      final var job = jobState.getJob(elementInstance.getJobKey());
      if (job == null) {
        throw new SafetyCheckFailedException(
            String.format(
                """
                Expected to migrate a job for process instance with key '%d', \
                but could not find job with key '%d'. \
                Please report this as a bug""",
                processInstanceKey, elementInstance.getJobKey()));
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
          processIncidentKey,
          targetProcessDefinition,
          targetElementId,
          updatedElementInstanceRecord);
    }

    final var jobIncidentKey = incidentState.getJobIncidentKey(elementInstance.getJobKey());
    if (jobIncidentKey != MISSING_INCIDENT) {
      appendIncidentMigratedEvent(
          jobIncidentKey, targetProcessDefinition, targetElementId, updatedElementInstanceRecord);
    }

    if (isUserTaskConversion) {
      final ProcessInstanceMigrationUserTaskBehavior migrationUserTaskBehavior =
          new ProcessInstanceMigrationUserTaskBehavior(
              processInstanceKey,
              elementInstance,
              sourceProcessDefinition,
              targetProcessDefinition,
              bpmnBehaviors,
              targetElementId,
              updatedElementInstanceRecord,
              stateWriter,
              jobState);
      migrationUserTaskBehavior.tryMigrateJobWorkerToCamundaUserTask();
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

    handleAdHocSubProcess(
        elementInstance, elementInstanceRecord, targetProcessDefinition, targetElementId);

    if (ProcessInstanceIntent.ELEMENT_ACTIVATING != elementInstance.getState()) {
      // Elements in ACTIVATING state haven't subscribed to events yet. We shouldn't subscribe such
      // elements to events during migration either. For elements that have been ACTIVATED, a
      // subscription would already exist if needed. So, we want to deal with the expected event
      // subscriptions. See: https://github.com/camunda/camunda/issues/19212
      migrationCatchEventBehaviour.handleCatchEvents(
          elementInstance,
          targetProcessDefinition,
          sourceProcessDefinition,
          sourceElementIdToTargetElementId,
          updatedElementInstanceRecord,
          targetElementId,
          processInstanceKey,
          elementId);
    }

    if (updatedElementInstanceRecord.getBpmnElementType() == BpmnElementType.CALL_ACTIVITY) {
      migrateCalledSubProcessElements(elementInstance.getCalledChildInstanceKey());
    }
  }

  private static DirectBuffer getTargetSequenceFlowId(
      final Map<String, String> sourceElementIdToTargetElementId,
      final ExecutableSequenceFlow sequenceFlow) {
    final String sourceSequenceFlowId = BufferUtil.bufferAsString(sequenceFlow.getId());
    final String targetSequenceFlowId = sourceElementIdToTargetElementId.get(sourceSequenceFlowId);

    return BufferUtil.wrapString(targetSequenceFlowId);
  }

  /**
   * Updates the element instance record with the new process definition key, bpmn process id,
   * version and recalculates the tree path.
   *
   * @param elementInstance the element instance to be updated
   * @param targetProcessDefinition the new process definition
   * @param targetElementId the new element id
   * @return the updated element instance record
   */
  private ProcessInstanceRecord getUpdatedElementInstanceRecord(
      final ElementInstance elementInstance,
      final DeployedProcess targetProcessDefinition,
      final String targetElementId) {
    final var elementInstanceRecord = new ProcessInstanceRecord();
    // copy all fields from the existing record and change the necessary ones only
    elementInstanceRecord.copyFrom(elementInstance.getValue());

    elementInstanceRecord
        .setProcessDefinitionKey(targetProcessDefinition.getKey())
        .setBpmnProcessId(targetProcessDefinition.getBpmnProcessId())
        .setVersion(targetProcessDefinition.getVersion())
        .setElementId(targetElementId);

    // recalculating the tree path is necessary because the element id changed
    final var elementTreePath =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceState::getInstance)
            .withCallActivityIndexProvider(processState::getFlowElement)
            .withElementInstanceKey(elementInstance.getKey())
            .withFlowScopeKey(elementInstance.getParentKey())
            .withRecordValue(elementInstanceRecord)
            .build();

    elementInstanceRecord
        .setElementInstancePath(elementTreePath.elementInstancePath())
        .setProcessDefinitionPath(elementTreePath.processDefinitionPath())
        .setCallingElementPath(elementTreePath.callingElementPath());

    return elementInstanceRecord;
  }

  /**
   * Migrates the elements of a called subprocess.
   *
   * <p>When migrating the parent process instance, new call activities might be included.
   * Therefore, we need to adjust the tree path of the called subprocess elements accordingly.
   *
   * @param calledChildInstanceKey the key of the called subprocess instance
   */
  private void migrateCalledSubProcessElements(final long calledChildInstanceKey) {
    final var calledInstance = elementInstanceState.getInstance(calledChildInstanceKey);
    if (calledInstance == null) {
      return;
    }
    final var elementInstances = new ArrayDeque<>(List.of(calledInstance));
    while (!elementInstances.isEmpty()) {
      final var instance = elementInstances.poll();
      adjustCalledInstancesTreePath(elementInstances, instance);
      final List<ElementInstance> children = elementInstanceState.getChildren(instance.getKey());
      elementInstances.addAll(children);
    }
  }

  /**
   * Adjusts the tree path of the called instances for a given element instance. This method
   * recalculates the tree path for the element instance and updates the element instance record
   * with the new paths. If the element instance is a call activity, it also processes the called
   * child instance.
   *
   * @param elementInstances the queue of element instances to process
   * @param instance the current element instance to adjust
   */
  private void adjustCalledInstancesTreePath(
      final ArrayDeque<ElementInstance> elementInstances, final ElementInstance instance) {
    final var elementInstanceRecord = instance.getValue();
    final var elementTreePath =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceState::getInstance)
            .withCallActivityIndexProvider(processState::getFlowElement)
            .withElementInstanceKey(instance.getKey())
            .withFlowScopeKey(instance.getParentKey())
            .withRecordValue(elementInstanceRecord)
            .build();

    elementInstanceRecord
        .setElementInstancePath(elementTreePath.elementInstancePath())
        .setProcessDefinitionPath(elementTreePath.processDefinitionPath())
        .setCallingElementPath(elementTreePath.callingElementPath());

    stateWriter.appendFollowUpEvent(
        instance.getKey(), ProcessInstanceIntent.ANCESTOR_MIGRATED, elementInstanceRecord);

    if (elementInstanceRecord.getBpmnElementType() == BpmnElementType.CALL_ACTIVITY) {
      // found more call activities? add the called child instance to the queue if present
      // to continue going deeper the tree
      final ElementInstance calledInstance =
          elementInstanceState.getInstance(instance.getCalledChildInstanceKey());
      if (calledInstance != null) {
        elementInstances.add(calledInstance);
      }
    }
  }

  private void appendIncidentMigratedEvent(
      final long incidentKey,
      final DeployedProcess targetProcessDefinition,
      final String targetElementId,
      final ProcessInstanceRecord elementInstanceRecord) {
    final var incidentRecord = incidentState.getIncidentRecord(incidentKey);
    if (incidentRecord == null) {
      throw new SafetyCheckFailedException(
          String.format(
              """
              Expected to migrate a user task for process instance with key '%d', \
              but could not find incident with key '%d'. \
              Please report this as a bug""",
              elementInstanceRecord.getProcessInstanceKey(), incidentKey));
    }
    stateWriter.appendFollowUpEvent(
        incidentKey,
        IncidentIntent.MIGRATED,
        incidentRecord
            .setProcessDefinitionKey(targetProcessDefinition.getKey())
            .setBpmnProcessId(targetProcessDefinition.getBpmnProcessId())
            .setElementId(BufferUtil.wrapString(targetElementId))
            .setElementInstancePath(elementInstanceRecord.getElementInstancePath())
            .setProcessDefinitionPath(elementInstanceRecord.getProcessDefinitionPath())
            .setCallingElementPath(elementInstanceRecord.getCallingElementPath()));
  }

  private Set<ExecutableSequenceFlow> getSequenceFlowsToMigrate(
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> sourceElementIdToTargetElementId,
      final ElementInstance elementInstance) {
    final long elementInstanceKey = elementInstance.getKey();
    final long processInstanceKey = elementInstance.getValue().getProcessInstanceKey();

    final List<ActiveSequenceFlow> activeSequenceFlows = new ArrayList<>();
    elementInstanceState.visitTakenSequenceFlows(
        elementInstanceKey,
        (scopeKey, gatewayElementId, sequenceFlowId, number) -> {
          final var sequenceFlow =
              sourceProcessDefinition
                  .getProcess()
                  .getElementById(sequenceFlowId, ExecutableSequenceFlow.class);
          activeSequenceFlows.add(new ActiveSequenceFlow(sequenceFlow, sequenceFlow.getTarget()));
        });

    return activeSequenceFlows.stream()
        .filter(
            sequenceFlow -> {
              final BpmnElementType elementType = sequenceFlow.target().getElementType();
              return elementType == BpmnElementType.PARALLEL_GATEWAY
                  || elementType == BpmnElementType.INCLUSIVE_GATEWAY;
            })
        .map(
            activeSequenceFlow -> {
              final ExecutableSequenceFlow activeFlow = activeSequenceFlow.sequenceFlow();
              final ExecutableFlowNode sourceGateway = activeSequenceFlow.target;
              requireNoConcurrentCommandForGateway(
                  elementInstanceState, sourceGateway, elementInstanceKey, processInstanceKey);

              final String targetGatewayId =
                  sourceElementIdToTargetElementId.get(
                      BufferUtil.bufferAsString(sourceGateway.getId()));
              requireValidGatewayMapping(
                  sourceGateway, targetGatewayId, targetProcessDefinition, processInstanceKey);

              final ExecutableFlowNode targetGateway =
                  targetProcessDefinition
                      .getProcess()
                      .getElementById(targetGatewayId, ExecutableFlowNode.class);
              requireValidTargetIncomingFlowCount(sourceGateway, targetGateway, processInstanceKey);
              requireNonNullTargetSequenceFlowId(
                  activeFlow, sourceElementIdToTargetElementId, processInstanceKey);

              return activeFlow;
            })
        .collect(Collectors.toSet());
  }

  private void deleteTakenSequenceFlow(
      final ProcessInstanceRecord elementInstanceRecord,
      final ExecutableSequenceFlow sequenceFlow,
      final long elementInstanceKey) {
    handleSequenceFlow(
        elementInstanceRecord,
        sequenceFlow,
        elementInstanceKey,
        sequenceFlow.getId(),
        ProcessInstanceIntent.SEQUENCE_FLOW_DELETED);
  }

  private void takeNewSequenceFlow(
      final ProcessInstanceRecord elementInstanceRecord,
      final ExecutableSequenceFlow sequenceFlow,
      final long elementInstanceKey,
      final DirectBuffer sequenceFlowId) {
    handleSequenceFlow(
        elementInstanceRecord,
        sequenceFlow,
        elementInstanceKey,
        sequenceFlowId,
        ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN);
  }

  private void handleSequenceFlow(
      final ProcessInstanceRecord elementInstanceRecord,
      final ExecutableSequenceFlow sequenceFlow,
      final long elementInstanceKey,
      final DirectBuffer sequenceFlowId,
      final ProcessInstanceIntent intent) {
    final var sequenceFlowRecord = new ProcessInstanceRecord();
    sequenceFlowRecord.copyFrom(elementInstanceRecord);
    sequenceFlowRecord
        .setElementId(sequenceFlowId)
        .setBpmnElementType(sequenceFlow.getElementType())
        .setBpmnEventType(sequenceFlow.getEventType())
        .setFlowScopeKey(elementInstanceKey)
        .resetElementInstancePath()
        .resetCallingElementPath()
        .resetProcessDefinitionPath();

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), intent, sequenceFlowRecord);
  }

  /**
   * Handles ad-hoc subprocess migration by updating the {@code adHocSubProcessElements} variable,
   * defined in {@link io.camunda.zeebe.model.bpmn.impl.ZeebeConstants#AD_HOC_SUB_PROCESS_ELEMENTS},
   * to reflect the target process definition.
   *
   * <p>When an ad-hoc subprocess is migrated to a different process definition version, the {@code
   * adHocSubProcessElements} variable, which contains metadata about available ad-hoc activities,
   * must be updated to match the target definition.
   *
   * <p>This ensures that the metadata accurately reflects the available activities in the target
   * process definition, preventing stale references to activities that may have been added,
   * removed, or modified in the new version.
   *
   * @param elementInstance the ad-hoc subprocess element instance being migrated
   * @param elementInstanceRecord the process instance record of the ad-hoc subprocess
   * @param targetProcessDefinition the target process definition to migrate to
   * @param targetElementId the ID of the ad-hoc subprocess element in the target process definition
   */
  private void handleAdHocSubProcess(
      final ElementInstance elementInstance,
      final ProcessInstanceRecord elementInstanceRecord,
      final DeployedProcess targetProcessDefinition,
      final String targetElementId) {

    if (isAdHocSubProcess(elementInstanceRecord.getBpmnElementType())) {
      final AbstractFlowElement element =
          targetProcessDefinition.getProcess().getElementById(targetElementId);

      final ExecutableAdHocSubProcess targetAhsp;
      if (element instanceof final ExecutableMultiInstanceBody mi) {
        targetAhsp = (ExecutableAdHocSubProcess) mi.getInnerActivity();
      } else {
        targetAhsp = (ExecutableAdHocSubProcess) element;
      }

      variableState.getVariablesLocal(elementInstance.getKey()).stream()
          .filter(
              variable ->
                  BufferUtil.equals(
                      variable.name(), BufferUtil.wrapString(AD_HOC_SUB_PROCESS_ELEMENTS)))
          .findFirst()
          .ifPresent(
              variable ->
                  stateWriter.appendFollowUpEvent(
                      variable.key(),
                      VariableIntent.UPDATED,
                      variableRecord
                          .setScopeKey(elementInstance.getKey())
                          .setName(variable.name())
                          .setValue(targetAhsp.getAdHocActivitiesMetadata())
                          .setProcessInstanceKey(elementInstance.getValue().getProcessInstanceKey())
                          .setProcessDefinitionKey(targetProcessDefinition.getKey())
                          .setBpmnProcessId(targetProcessDefinition.getBpmnProcessId())
                          .setTenantId(elementInstance.getValue().getTenantId())));
    }
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

  record ActiveSequenceFlow(ExecutableSequenceFlow sequenceFlow, ExecutableFlowNode target) {}
}
