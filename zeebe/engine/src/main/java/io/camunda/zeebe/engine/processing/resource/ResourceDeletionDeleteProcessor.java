/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static io.camunda.zeebe.engine.state.instance.TimerInstance.NO_ELEMENT_INSTANCE;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.deployment.StartEventSubscriptionManager;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthenticatedAuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.exception.ForbiddenException;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedDrg;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.deployment.PersistedForm;
import io.camunda.zeebe.engine.state.deployment.PersistedResource;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.FormState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ResourceState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.agrona.concurrent.UnsafeBuffer;

public class ResourceDeletionDeleteProcessor
    implements DistributedTypedRecordProcessor<ResourceDeletionRecord> {

  private static final List<ResourceType> SUPPORTED_HISTORY_DELETION_TYPES =
      List.of(ResourceType.PROCESS_DEFINITION, ResourceType.DECISION_REQUIREMENTS);

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final KeyGenerator keyGenerator;
  private final DecisionState decisionState;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final ProcessState processState;
  private final ElementInstanceState elementInstanceState;
  private final TimerInstanceState timerInstanceState;
  private final BannedInstanceState bannedInstanceState;
  private final CatchEventBehavior catchEventBehavior;
  private final StartEventSubscriptions startEventSubscriptions;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final StartEventSubscriptionManager startEventSubscriptionManager;
  private final FormState formState;
  private final ResourceState resourceState;
  private final TenantState tenantState;

  public ResourceDeletionDeleteProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior,
      final BpmnBehaviors bpmnBehaviors,
      final AuthorizationCheckBehavior authCheckBehavior) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    decisionState = processingState.getDecisionState();
    this.commandDistributionBehavior = commandDistributionBehavior;
    processState = processingState.getProcessState();
    elementInstanceState = processingState.getElementInstanceState();
    timerInstanceState = processingState.getTimerState();
    bannedInstanceState = processingState.getBannedInstanceState();
    catchEventBehavior = bpmnBehaviors.catchEventBehavior();
    this.authCheckBehavior = authCheckBehavior;
    startEventSubscriptionManager =
        new StartEventSubscriptionManager(processingState, keyGenerator, stateWriter);
    startEventSubscriptions =
        new StartEventSubscriptions(
            bpmnBehaviors.expressionProcessor(), catchEventBehavior, startEventSubscriptionManager);
    formState = processingState.getFormState();
    resourceState = processingState.getResourceState();
    tenantState = processingState.getTenantState();
  }

  @Override
  public void processNewCommand(final TypedRecord<ResourceDeletionRecord> command) {
    final var value = command.getValue();
    final long eventKey = keyGenerator.nextKey();

    tryDeleteResources(command, eventKey);

    stateWriter.appendFollowUpEvent(eventKey, ResourceDeletionIntent.DELETED, value);
    commandDistributionBehavior
        .withKey(eventKey)
        .inQueue(DistributionQueue.DEPLOYMENT)
        .distribute(command);
    responseWriter.writeEventOnCommand(eventKey, ResourceDeletionIntent.DELETED, value, command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ResourceDeletionRecord> command) {
    final var value = command.getValue();

    tryDeleteResources(command, command.getKey());

    stateWriter.appendFollowUpEvent(command.getKey(), ResourceDeletionIntent.DELETED, value);
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ResourceDeletionRecord> command, final Throwable error) {
    if (error instanceof final ForbiddenException exception) {
      rejectionWriter.appendRejection(
          command, exception.getRejectionType(), exception.getMessage());
      responseWriter.writeRejectionOnCommand(
          command, exception.getRejectionType(), exception.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    } else if (error instanceof final NoSuchResourceException exception) {
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, exception.getMessage());
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.NOT_FOUND, exception.getMessage());

      if (command.isCommandDistributed()) {
        // If the command is distributed, and it cannot be found upon processing, we can acknowledge
        // the distribution.
        commandDistributionBehavior.acknowledgeCommand(command);
      }

      return ProcessingError.EXPECTED_ERROR;
    } else if (error instanceof final ActiveProcessInstancesException exception) {
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, exception.getMessage());
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.INVALID_STATE, exception.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    }

    return ProcessingError.UNEXPECTED_ERROR;
  }

  private void tryDeleteResources(
      final TypedRecord<ResourceDeletionRecord> command, final long eventKey) {
    final var value = command.getValue();

    final var resourceDeleted =
        untilResourceDeleted(command, tenantId -> tryDeleteResource(command, tenantId, eventKey));

    if (!resourceDeleted) {
      if (value.isDeleteHistory()
          && SUPPORTED_HISTORY_DELETION_TYPES.contains(value.getResourceType())) {
        deleteHistory(eventKey, command);
      } else {
        throw new NoSuchResourceException(value.getResourceKey());
      }
    }
  }

  private boolean tryDeleteResource(
      final TypedRecord<ResourceDeletionRecord> command,
      final String tenantId,
      final long eventKey) {
    final var value = command.getValue();

    final var process = processState.getProcessByKeyAndTenant(value.getResourceKey(), tenantId);
    if (process != null) {
      command
          .getValue()
          .setResourceType(ResourceType.PROCESS_DEFINITION)
          .setResourceId(process.getBpmnProcessId())
          .setTenantId(process.getTenantId());
      return authorizeAndDelete(
          command,
          eventKey,
          PermissionType.DELETE_PROCESS,
          bufferAsString(process.getBpmnProcessId()),
          process.getTenantId(),
          () -> deleteProcess(process, command, eventKey));
    }

    final var drgOptional =
        decisionState.findDecisionRequirementsByTenantAndKey(tenantId, value.getResourceKey());
    if (drgOptional.isPresent()) {
      final var drg = drgOptional.get();
      command
          .getValue()
          .setResourceType(ResourceType.DECISION_REQUIREMENTS)
          .setResourceId(drg.getDecisionRequirementsId())
          .setTenantId(drg.getTenantId());
      return authorizeAndDelete(
          command,
          eventKey,
          PermissionType.DELETE_DRD,
          bufferAsString(drg.getDecisionRequirementsId()),
          drg.getTenantId(),
          () -> deleteDecisionRequirements(drg, command, eventKey));
    }

    final var formOptional = formState.findFormByKey(value.getResourceKey(), tenantId);
    if (formOptional.isPresent()) {
      final var form = formOptional.get();
      command
          .getValue()
          .setResourceType(ResourceType.FORM)
          .setResourceId(form.getFormId())
          .setTenantId(form.getTenantId());
      return authorizeAndDelete(
          command,
          eventKey,
          PermissionType.DELETE_FORM,
          bufferAsString(form.getFormId()),
          form.getTenantId(),
          () -> deleteForm(form));
    }

    final var resourceOptional = resourceState.findResourceByKey(value.getResourceKey(), tenantId);
    if (resourceOptional.isPresent()) {
      final var resource = resourceOptional.get();
      command
          .getValue()
          .setResourceType(ResourceType.UNKNOWN)
          .setResourceId(resource.getResourceId())
          .setTenantId(resource.getTenantId());
      return authorizeAndDelete(
          command,
          eventKey,
          PermissionType.DELETE_RESOURCE,
          bufferAsString(resource.getResourceId()),
          resource.getTenantId(),
          () -> deleteResource(resource));
    }

    return false;
  }

  private boolean authorizeAndDelete(
      final TypedRecord<ResourceDeletionRecord> command,
      final long eventKey,
      final PermissionType permissionType,
      final String resourceId,
      final String tenantId,
      final Runnable deletionAction) {
    checkAuthorization(
        command, AuthorizationResourceType.RESOURCE, permissionType, resourceId, tenantId);
    stateWriter.appendFollowUpEvent(eventKey, ResourceDeletionIntent.DELETING, command.getValue());
    setTenantId(command, tenantId);
    deletionAction.run();
    return true;
  }

  private void deleteDecisionRequirements(
      final DeployedDrg drg,
      final TypedRecord<ResourceDeletionRecord> command,
      final long eventKey) {
    decisionState
        .findDecisionsByTenantAndDecisionRequirementsKey(
            drg.getTenantId(), drg.getDecisionRequirementsKey())
        .forEach(this::deleteDecision);

    if (!command.isCommandDistributed() && command.getValue().isDeleteHistory()) {
      deleteDecisionInstanceHistory(drg.getDecisionRequirementsKey(), eventKey, command.getValue());
    }

    final var drgRecord =
        new DecisionRequirementsRecord()
            .setDecisionRequirementsId(bufferAsString(drg.getDecisionRequirementsId()))
            .setDecisionRequirementsName(bufferAsString(drg.getDecisionRequirementsName()))
            .setDecisionRequirementsVersion(drg.getDecisionRequirementsVersion())
            .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
            .setResourceName(bufferAsString(drg.getResourceName()))
            .setChecksum(drg.getChecksum())
            .setResource(drg.getResource())
            .setTenantId(drg.getTenantId())
            .setDeploymentKey(drg.getDeploymentKey());

    stateWriter.appendFollowUpEvent(
        keyGenerator.nextKey(), DecisionRequirementsIntent.DELETED, drgRecord);
  }

  private void deleteDecision(final PersistedDecision persistedDecision) {
    final var decisionRecord =
        new DecisionRecord()
            .setDecisionId(bufferAsString(persistedDecision.getDecisionId()))
            .setDecisionName(bufferAsString(persistedDecision.getDecisionName()))
            .setVersion(persistedDecision.getVersion())
            .setVersionTag(persistedDecision.getVersionTag())
            .setDecisionKey(persistedDecision.getDecisionKey())
            .setDecisionRequirementsId(
                bufferAsString(persistedDecision.getDecisionRequirementsId()))
            .setDecisionRequirementsKey(persistedDecision.getDecisionRequirementsKey())
            .setTenantId(persistedDecision.getTenantId())
            .setDeploymentKey(persistedDecision.getDeploymentKey());

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), DecisionIntent.DELETED, decisionRecord);
  }

  private void deleteProcess(
      final DeployedProcess process,
      final TypedRecord<ResourceDeletionRecord> command,
      final long eventKey) {
    // We don't add the checksum or resource in this event. The checksum is not easily available
    // and the resources are left out to prevent exceeding the maximum batch size.
    final var processIdBuffer = process.getBpmnProcessId();
    final var processRecord =
        new ProcessRecord()
            .setBpmnProcessId(processIdBuffer)
            .setVersion(process.getVersion())
            .setVersionTag(process.getVersionTag())
            .setKey(process.getKey())
            .setResourceName(process.getResourceName())
            .setTenantId(process.getTenantId())
            .setDeploymentKey(process.getDeploymentKey());
    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), ProcessIntent.DELETING, processRecord);

    final String processId = processRecord.getBpmnProcessId();
    final var latestVersion =
        processState.getLatestProcessVersion(processId, processRecord.getTenantId());

    // If we are deleting the latest version we must unsubscribe the start events
    if (latestVersion == process.getVersion()) {
      unsubscribeStartEvents(process);

      final var previousVersion =
          processState.findProcessVersionBefore(
              processId, latestVersion, processRecord.getTenantId());
      // If there is a previous version we must resubscribe to the previous version's start events.
      if (previousVersion.isPresent()) {
        final var previousProcess =
            processState.getProcessByProcessIdAndVersion(
                processIdBuffer, previousVersion.get(), process.getTenantId());
        startEventSubscriptions.resubscribeToStartEvents(previousProcess);
      }
    }

    final var bannedInstances = bannedInstanceState.getBannedProcessInstanceKeys();
    final var hasRunningInstances =
        elementInstanceState.hasActiveProcessInstances(process.getKey(), bannedInstances);

    if (!hasRunningInstances) {
      if (!command.isCommandDistributed() && command.getValue().isDeleteHistory()) {
        deleteProcessInstanceHistory(process.getKey(), eventKey, command.getValue());
      }
      stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), ProcessIntent.DELETED, processRecord);
    } else {
      throw new ActiveProcessInstancesException(process.getKey());
    }
  }

  private void deleteHistory(
      final long eventKey, final TypedRecord<ResourceDeletionRecord> command) {
    final var commandValue = command.getValue();
    switch (commandValue.getResourceType()) {
      case PROCESS_DEFINITION -> {
        checkAuthorization(
            command,
            AuthorizationResourceType.RESOURCE,
            PermissionType.DELETE_PROCESS,
            commandValue.getResourceId(),
            commandValue.getTenantId());
        deleteProcessInstanceHistory(commandValue.getResourceKey(), eventKey, commandValue);
      }
      case DECISION_REQUIREMENTS -> {
        checkAuthorization(
            command,
            AuthorizationResourceType.RESOURCE,
            PermissionType.DELETE_DRD,
            commandValue.getResourceId(),
            commandValue.getTenantId());
        deleteDecisionInstanceHistory(commandValue.getResourceKey(), eventKey, commandValue);
      }
    }
  }

  private void deleteProcessInstanceHistory(
      final long processDefinitionKey,
      final long eventKey,
      final ResourceDeletionRecord resourceDeletionRecord) {
    final var filter =
        new ProcessInstanceFilter.Builder().processDefinitionKeys(processDefinitionKey).build();
    final long batchOperationKey = keyGenerator.nextKey();
    final var batchOperationRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE)
            .setEntityFilter(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(filter)))
            .setAuthentication(
                new UnsafeBuffer(
                    MsgPackConverter.convertToMsgPack(CamundaAuthentication.anonymous())))
            .setFollowUpCommand(
                ValueType.HISTORY_DELETION,
                HistoryDeletionIntent.DELETE,
                new HistoryDeletionRecord()
                    .setResourceKey(processDefinitionKey)
                    .setResourceType(HistoryDeletionType.PROCESS_DEFINITION));
    commandWriter.appendFollowUpCommand(
        eventKey, BatchOperationIntent.CREATE, batchOperationRecord);

    resourceDeletionRecord.setBatchOperationKey(batchOperationKey);
    resourceDeletionRecord.setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE);
  }

  private void deleteDecisionInstanceHistory(
      final long decisionRequirementsKey,
      final long eventKey,
      final ResourceDeletionRecord resourceDeletionRecord) {
    final var filter =
        new DecisionInstanceFilter.Builder()
            .decisionRequirementsKeys(decisionRequirementsKey)
            .build();
    final long batchOperationKey = keyGenerator.nextKey();
    final var batchOperationRecord =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(BatchOperationType.DELETE_DECISION_INSTANCE)
            .setEntityFilter(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(filter)))
            .setAuthentication(
                new UnsafeBuffer(
                    MsgPackConverter.convertToMsgPack(CamundaAuthentication.anonymous())))
            .setFollowUpCommand(
                ValueType.HISTORY_DELETION,
                HistoryDeletionIntent.DELETE,
                new HistoryDeletionRecord()
                    .setResourceKey(decisionRequirementsKey)
                    .setResourceType(HistoryDeletionType.DECISION_REQUIREMENTS));
    commandWriter.appendFollowUpCommand(
        eventKey, BatchOperationIntent.CREATE, batchOperationRecord);

    resourceDeletionRecord.setBatchOperationKey(batchOperationKey);
    resourceDeletionRecord.setBatchOperationType(BatchOperationType.DELETE_DECISION_INSTANCE);
  }

  private void unsubscribeStartEvents(final DeployedProcess deployedProcess) {
    final var process = deployedProcess.getProcess();
    if (process.hasTimerStartEvent()) {
      timerInstanceState.forEachTimerForElementInstance(
          NO_ELEMENT_INSTANCE,
          timer -> {
            if (timer.getProcessDefinitionKey() == deployedProcess.getKey()) {
              catchEventBehavior.unsubscribeFromTimerEvent(timer);
            }
          });
    }

    startEventSubscriptionManager.closeStartEventSubscriptions(deployedProcess);
  }

  private void deleteForm(final PersistedForm persistedForm) {
    final var form =
        new FormRecord()
            .setFormId(persistedForm.getFormId())
            .setFormKey(persistedForm.getFormKey())
            .setTenantId(persistedForm.getTenantId())
            .setResourceName(persistedForm.getResourceName())
            .setResource(persistedForm.getResource())
            .setChecksum(persistedForm.getChecksum())
            .setVersion(persistedForm.getVersion())
            .setVersionTag(persistedForm.getVersionTag())
            .setDeploymentKey(persistedForm.getDeploymentKey());

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), FormIntent.DELETED, form);
  }

  private void deleteResource(final PersistedResource persistedResource) {
    final var resource =
        new ResourceRecord()
            .setResourceId(persistedResource.getResourceId())
            .setResourceKey(persistedResource.getResourceKey())
            .setTenantId(persistedResource.getTenantId())
            .setResourceName(persistedResource.getResourceName())
            .setResource(BufferUtil.wrapString(persistedResource.getResource()))
            .setChecksum(persistedResource.getChecksum())
            .setVersion(persistedResource.getVersion())
            .setVersionTag(persistedResource.getVersionTag())
            .setDeploymentKey(persistedResource.getDeploymentKey());
    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), ResourceIntent.DELETED, resource);
  }

  private AuthorizedTenants getAuthorizedTenants(
      final TypedRecord<ResourceDeletionRecord> command) {
    final String tenantId = command.getValue().getTenantId();
    if (tenantId.isEmpty()) {
      return authCheckBehavior.getAuthorizedTenantIds(command);
    }
    return new AuthenticatedAuthorizedTenants(tenantId);
  }

  private boolean untilResourceDeleted(
      final TypedRecord<ResourceDeletionRecord> command,
      final Function<String, Boolean> resourceDeletionCallback) {
    final var authorizedTenants = getAuthorizedTenants(command);

    if (AuthorizedTenants.ANONYMOUS.equals(authorizedTenants)) {
      return Optional.of(tryToDeleteResourceAssignedToDefaultTenant(resourceDeletionCallback))
          .filter(Boolean::booleanValue)
          .orElseGet(() -> forEachTenantUntilResourceDeleted(resourceDeletionCallback));
    } else {
      for (final var tenant : authorizedTenants.getAuthorizedTenantIds()) {
        if (resourceDeletionCallback.apply(tenant)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Tries to delete the resource, iff it is assigned to the default tenant. If the resource was
   * deleted, it returns true, otherwise false.
   */
  private boolean tryToDeleteResourceAssignedToDefaultTenant(
      final Function<String, Boolean> resourceDeletionCallback) {
    return resourceDeletionCallback.apply(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  /**
   * Loops over the existing tenants to find the resource to delete. If found and deleted, it
   * returns true, otherwise false.
   */
  private boolean forEachTenantUntilResourceDeleted(
      final Function<String, Boolean> resourceDeletionCallback) {
    final var resourceDeleted = new AtomicBoolean(false);
    tenantState.forEachTenant(
        tenant -> {
          resourceDeleted.set(resourceDeletionCallback.apply(tenant));
          return !resourceDeleted.get();
        });
    return resourceDeleted.get();
  }

  private void setTenantId(
      final TypedRecord<ResourceDeletionRecord> command, final String tenantId) {
    command.getValue().setTenantId(tenantId);
  }

  private void checkAuthorization(
      final TypedRecord<ResourceDeletionRecord> command,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final String resourceId,
      final String tenantId) {
    final var authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(resourceType)
            .permissionType(permissionType)
            .tenantId(tenantId)
            .addResourceId(resourceId)
            .build();
    if (authCheckBehavior.isAuthorizedOrInternalCommand(authRequest).isLeft()) {
      throw new ForbiddenException(authRequest);
    }
  }

  private static final class NoSuchResourceException extends IllegalStateException {
    private static final String ERROR_MESSAGE_RESOURCE_NOT_FOUND =
        "Expected to delete resource but no resource found with key `%d`";

    private NoSuchResourceException(final long resourceKey) {
      super(String.format(ERROR_MESSAGE_RESOURCE_NOT_FOUND, resourceKey));
    }
  }

  private static final class ActiveProcessInstancesException extends IllegalStateException {
    private static final String ERROR_MESSAGE_RUNNING_INSTANCES =
        "Expected to delete resource with key `%d` but there are still running instances";

    private ActiveProcessInstancesException(final long processDefinitionKey) {
      super(String.format(ERROR_MESSAGE_RUNNING_INSTANCES, processDefinitionKey));
    }
  }
}
