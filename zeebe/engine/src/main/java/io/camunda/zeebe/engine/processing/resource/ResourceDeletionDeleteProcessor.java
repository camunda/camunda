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

import io.camunda.security.core.authz.TenantAccess;
import io.camunda.zeebe.engine.metrics.ProcessDefinitionMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnProcessDeletionBehavior;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.deployment.StartEventSubscriptionManager;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.historydeletion.HistoryDeletionBehavior;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.exception.ForbiddenException;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
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
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.agrona.DirectBuffer;

public class ResourceDeletionDeleteProcessor
    implements DistributedTypedRecordProcessor<ResourceDeletionRecord> {

  private static final List<ResourceType> SUPPORTED_HISTORY_DELETION_TYPES =
      List.of(ResourceType.PROCESS_DEFINITION, ResourceType.DECISION_REQUIREMENTS);

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final KeyGenerator keyGenerator;
  private final DecisionState decisionState;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final ProcessState processState;
  private final BpmnProcessDeletionBehavior processDeletionBehavior;
  private final ElementInstanceState elementInstanceState;
  private final TimerInstanceState timerInstanceState;
  private final BannedInstanceState bannedInstanceState;
  private final CatchEventBehavior catchEventBehavior;
  private final StartEventSubscriptions startEventSubscriptions;
  private final PermissionsBehavior permissionsBehavior;
  private final CslTenantCheck tenantCheck;
  private final StartEventSubscriptionManager startEventSubscriptionManager;
  private final FormState formState;
  private final ResourceState resourceState;
  private final TenantState tenantState;
  private final ProcessDefinitionMetrics processDefinitionMetrics;
  private final HistoryDeletionBehavior historyDeletionBehavior;
  private final RoutingInfo routingInfo;

  public ResourceDeletionDeleteProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior,
      final BpmnBehaviors bpmnBehaviors,
      final PermissionsBehavior permissionsBehavior,
      final CslTenantCheck tenantCheck,
      final ProcessDefinitionMetrics processDefinitionMetrics,
      final RoutingInfo routingInfo) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    decisionState = processingState.getDecisionState();
    this.commandDistributionBehavior = commandDistributionBehavior;
    processState = processingState.getProcessState();
    processDeletionBehavior = bpmnBehaviors.processDeletionBehavior();
    elementInstanceState = processingState.getElementInstanceState();
    timerInstanceState = processingState.getTimerState();
    bannedInstanceState = processingState.getBannedInstanceState();
    catchEventBehavior = bpmnBehaviors.catchEventBehavior();
    this.permissionsBehavior = permissionsBehavior;
    this.tenantCheck = tenantCheck;
    startEventSubscriptionManager =
        new StartEventSubscriptionManager(processingState, keyGenerator, stateWriter);
    startEventSubscriptions =
        new StartEventSubscriptions(
            bpmnBehaviors.expressionProcessor(), catchEventBehavior, startEventSubscriptionManager);
    formState = processingState.getFormState();
    resourceState = processingState.getResourceState();
    tenantState = processingState.getTenantState();
    this.processDefinitionMetrics = processDefinitionMetrics;
    historyDeletionBehavior = new HistoryDeletionBehavior(keyGenerator, writers.command());
    this.routingInfo = routingInfo;
  }

  @Override
  public void processNewCommand(final TypedRecord<ResourceDeletionRecord> command) {
    final var value = command.getValue();
    final long eventKey = keyGenerator.nextKey();

    final var result = tryDeleteResources(command, eventKey);

    stateWriter.appendFollowUpEvent(eventKey, ResourceDeletionIntent.DELETED, value);
    commandDistributionBehavior
        .withKey(eventKey)
        .inQueue(DistributionQueue.DEPLOYMENT)
        .forPartitions(
            result.distributionPartitions.isEmpty()
                ? routingInfo.desiredPartitions()
                : result.distributionPartitions())
        .distribute(command);
    responseWriter.writeAcceptedResponseOnCommand(
        eventKey, ResourceDeletionIntent.DELETED, value, command);
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
      responseWriter.writeRejectedResponseOnCommand(
          command, exception.getRejectionType(), exception.getMessage());
      acknowledgeIfDistributed(command);
      return ProcessingError.EXPECTED_ERROR;
    } else if (error instanceof final NoSuchResourceException exception) {
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, exception.getMessage());
      responseWriter.writeRejectedResponseOnCommand(
          command, RejectionType.NOT_FOUND, exception.getMessage());
      acknowledgeIfDistributed(command);
      return ProcessingError.EXPECTED_ERROR;
    } else if (error instanceof final ResourceDeletionInProgressException exception) {
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, exception.getMessage());
      responseWriter.writeRejectedResponseOnCommand(
          command, RejectionType.INVALID_STATE, exception.getMessage());
      acknowledgeIfDistributed(command);
      return ProcessingError.EXPECTED_ERROR;
    }

    return ProcessingError.UNEXPECTED_ERROR;
  }

  // Ack even on rejection, otherwise the distribution never finishes and head-of-line blocks every
  // command queued behind it.
  private void acknowledgeIfDistributed(final TypedRecord<ResourceDeletionRecord> command) {
    if (command.isCommandDistributed()) {
      commandDistributionBehavior.acknowledgeCommand(command);
    }
  }

  private DeletionResult tryDeleteResources(
      final TypedRecord<ResourceDeletionRecord> command, final long eventKey) {
    final var value = command.getValue();

    final var result =
        untilResourceDeleted(command, tenantId -> tryDeleteResource(command, tenantId, eventKey));
    if (result.resourceDeleted()) {
      return result;
    }

    // Delete-time history purge is only for a resource already fully gone from primary storage.
    if (value.isDeleteHistory()
        && SUPPORTED_HISTORY_DELETION_TYPES.contains(value.getResourceType())) {
      deleteHistory(command);
      return DeletionResult.DELETED;
    }
    throw new NoSuchResourceException(value.getResourceKey());
  }

  private DeletionResult tryDeleteResource(
      final TypedRecord<ResourceDeletionRecord> command,
      final String tenantId,
      final long eventKey) {
    final var value = command.getValue();

    final var processDeletion = tryDeleteProcessDefinition(command, tenantId, eventKey);
    if (processDeletion.resourceDeleted()) {
      return processDeletion;
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
          () -> deleteDecisionRequirements(drg, command));
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

    return DeletionResult.NOT_DELETED;
  }

  private DeletionResult authorizeAndDelete(
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
    return DeletionResult.DELETED;
  }

  private void deleteDecisionRequirements(
      final DeployedDrg drg, final TypedRecord<ResourceDeletionRecord> command) {
    decisionState
        .findDecisionsByTenantAndDecisionRequirementsKey(
            drg.getTenantId(), drg.getDecisionRequirementsKey())
        .forEach(this::deleteDecision);

    if (!command.isCommandDistributed() && command.getValue().isDeleteHistory()) {
      deleteDecisionInstanceHistory(drg.getDecisionRequirementsKey(), command.getValue());
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

  private DeletionResult tryDeleteProcessDefinition(
      final TypedRecord<ResourceDeletionRecord> command,
      final String tenantId,
      final long eventKey) {
    final var processDefinitionKey = command.getValue().getResourceKey();
    final var process = processState.getProcessByKeyAndTenant(processDefinitionKey, tenantId);
    if (process != null) {
      return tryDeleteDeployedProcess(command, eventKey, process);
    }

    // Recovers definitions orphaned before #62820: gone here, but other partitions still owe a
    // drain report, so their delete is retried. See https://github.com/camunda/camunda/issues/64586
    final var pendingPartitions = processState.getPendingDeletionPartitions(processDefinitionKey);
    if (pendingPartitions.isEmpty()) {
      return DeletionResult.NOT_DELETED;
    }
    // A still-draining partition keeps its original deleteHistory flag, so a history deletion
    // could not be honored.
    if (command.isCommandDistributed() || command.getValue().isDeleteHistory()) {
      throw new ResourceDeletionInProgressException(processDefinitionKey);
    }
    stateWriter.appendFollowUpEvent(eventKey, ResourceDeletionIntent.DELETING, command.getValue());
    return DeletionResult.deletedOn(pendingPartitions);
  }

  private DeletionResult tryDeleteDeployedProcess(
      final TypedRecord<ResourceDeletionRecord> command,
      final long eventKey,
      final DeployedProcess process) {
    // Stamp metadata before the not-ACTIVE bail-out so a repeat-delete rejection carries the
    // resolved type/id/tenant, not whatever the client sent.
    command
        .getValue()
        .setResourceType(ResourceType.PROCESS_DEFINITION)
        .setResourceId(process.getBpmnProcessId())
        .setTenantId(process.getTenantId());
    if (!process.isActive()) {
      throw new ResourceDeletionInProgressException(process.getKey());
    }
    return authorizeAndDelete(
        command,
        eventKey,
        PermissionType.DELETE_PROCESS,
        bufferAsString(process.getBpmnProcessId()),
        process.getTenantId(),
        () -> deleteProcess(process, command));
  }

  private void deleteProcess(
      final DeployedProcess process, final TypedRecord<ResourceDeletionRecord> command) {
    // We don't add the checksum or resource in this event. The checksum is not easily available
    // and the resources are left out to prevent exceeding the maximum batch size.
    final var processIdBuffer = process.getBpmnProcessId();
    final var tenantId = process.getTenantId();
    final var processRecord = toProcessRecord(process);
    final String processId = processRecord.getBpmnProcessId();
    final var latestVersion = processState.getLatestProcessVersion(processId, tenantId);

    processRecord.setDrainPartitions(routingInfo.desiredPartitions());
    processRecord.setDeleteHistory(command.getValue().isDeleteHistory());
    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), ProcessIntent.DRAINING, processRecord);

    // If we are deleting the latest version we must unsubscribe the start events
    if (latestVersion == process.getVersion()) {
      unsubscribeStartEvents(process);

      // Hand the start subscription down to the latest ACTIVE version; DRAINING/deleted versions
      // reject new instances so must stay unsubscribed.
      findLatestActiveVersionBelow(processIdBuffer, processId, latestVersion, tenantId)
          .ifPresent(startEventSubscriptions::resubscribeToStartEvents);
    }

    final var bannedInstances = bannedInstanceState.getBannedProcessInstanceKeys();
    final boolean finalizedImmediately =
        !elementInstanceState.hasActiveProcessInstances(process.getKey(), bannedInstances);

    if (finalizedImmediately) {
      processDeletionBehavior.finalizeDeletion(processRecord);
      processDefinitionMetrics.processDefinitionDeleted(process.getKey());
    } else {
      processDefinitionMetrics.processDefinitionDraining(process.getKey());
    }
  }

  private ProcessRecord toProcessRecord(final DeployedProcess process) {
    return new ProcessRecord()
        .setBpmnProcessId(process.getBpmnProcessId())
        .setVersion(process.getVersion())
        .setVersionTag(process.getVersionTag())
        .setKey(process.getKey())
        .setResourceName(process.getResourceName())
        .setTenantId(process.getTenantId())
        .setDeploymentKey(process.getDeploymentKey());
  }

  private void deleteHistory(final TypedRecord<ResourceDeletionRecord> command) {
    if (command.isCommandDistributed()) {
      // We should not create batch operations for distributed commands. This gets handled by the
      // batch operation creator itself.
      return;
    }
    final var commandValue = command.getValue();
    final var resourceType = commandValue.getResourceType();

    final var permissionType =
        resourceType == ResourceType.PROCESS_DEFINITION
            ? PermissionType.DELETE_PROCESS
            : PermissionType.DELETE_DRD;
    final var isAuthorized =
        permissionsBehavior.isAuthorizedWithResourceIdentifiers(
            command,
            AuthorizationResourceType.RESOURCE,
            permissionType,
            commandValue.getResourceId());
    if (isAuthorized.isLeft()) {
      throw new ForbiddenException(isAuthorized.getLeft());
    }

    switch (resourceType) {
      case PROCESS_DEFINITION ->
          deleteProcessInstanceHistory(commandValue.getResourceKey(), commandValue);
      case DECISION_REQUIREMENTS ->
          deleteDecisionInstanceHistory(commandValue.getResourceKey(), commandValue);
      default -> {
        // No history to delete for forms and unknown resources
        // This should not be reached as SUPPORTED_HISTORY_DELETION_TYPES filters these out
      }
    }
  }

  private void deleteProcessInstanceHistory(
      final long processDefinitionKey, final ResourceDeletionRecord resourceDeletionRecord) {
    final long batchOperationKey =
        historyDeletionBehavior.deleteProcessInstanceHistory(processDefinitionKey);

    resourceDeletionRecord.setBatchOperationKey(batchOperationKey);
    resourceDeletionRecord.setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE);
  }

  private void deleteDecisionInstanceHistory(
      final long decisionRequirementsKey, final ResourceDeletionRecord resourceDeletionRecord) {
    final long batchOperationKey =
        historyDeletionBehavior.deleteDecisionInstanceHistory(decisionRequirementsKey);

    resourceDeletionRecord.setBatchOperationKey(batchOperationKey);
    resourceDeletionRecord.setBatchOperationType(BatchOperationType.DELETE_DECISION_INSTANCE);
  }

  /**
   * Latest active version (see {@link DeployedProcess#isActive()}) strictly below {@code version}.
   * Skips draining versions so they do not hold start-event subscriptions.
   *
   * <p>Loads known versions once and scans newest-first. {@code version >=} the deleted latest is
   * skipped because that version is already draining.
   */
  private Optional<DeployedProcess> findLatestActiveVersionBelow(
      final DirectBuffer processIdBuffer,
      final String processId,
      final int version,
      final String tenantId) {
    final var knownVersions = processState.getKnownProcessVersions(processId, tenantId);
    for (int i = knownVersions.size() - 1; i >= 0; i--) {
      final int candidateVersion = knownVersions.get(i).intValue();
      if (candidateVersion >= version) {
        continue;
      }
      final var process =
          processState.getProcessByProcessIdAndVersion(processIdBuffer, candidateVersion, tenantId);
      if (process != null && process.isActive()) {
        return Optional.of(process);
      }
    }
    return Optional.empty();
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
            .setResource(persistedResource.getResourceBuffer())
            .setChecksum(persistedResource.getChecksum())
            .setVersion(persistedResource.getVersion())
            .setVersionTag(persistedResource.getVersionTag())
            .setDeploymentKey(persistedResource.getDeploymentKey());
    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), ResourceIntent.DELETED, resource);
  }

  private TenantAccess getAuthorizedTenants(final TypedRecord<ResourceDeletionRecord> command) {
    final var userTenants = tenantCheck.resolveAuthorizedTenants(command.getAuthorizations());
    final String tenantId = command.getValue().getTenantId();
    if (tenantId.isEmpty()) {
      return userTenants;
    }
    if (!userTenants.isAuthorizedForTenantId(tenantId)) {
      throw new NoSuchResourceException(command.getValue().getResourceKey());
    }
    return TenantAccess.allowed(List.of(tenantId));
  }

  private DeletionResult untilResourceDeleted(
      final TypedRecord<ResourceDeletionRecord> command,
      final Function<String, DeletionResult> resourceDeletionCallback) {
    final var authorizedTenants = getAuthorizedTenants(command);

    if (authorizedTenants.wildcard()) {
      final var result = tryToDeleteResourceAssignedToDefaultTenant(resourceDeletionCallback);
      return result.resourceDeleted()
          ? result
          : forEachTenantUntilResourceDeleted(resourceDeletionCallback);
    } else {
      for (final var tenant : authorizedTenants.tenantIds()) {
        final var result = resourceDeletionCallback.apply(tenant);
        if (result.resourceDeleted()) {
          return result;
        }
      }
    }
    return DeletionResult.NOT_DELETED;
  }

  /** Tries to delete the resource, iff it is assigned to the default tenant. */
  private DeletionResult tryToDeleteResourceAssignedToDefaultTenant(
      final Function<String, DeletionResult> resourceDeletionCallback) {
    return resourceDeletionCallback.apply(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  /** Loops over the existing tenants to find the resource to delete. */
  private DeletionResult forEachTenantUntilResourceDeleted(
      final Function<String, DeletionResult> resourceDeletionCallback) {
    final var result = new AtomicReference<>(DeletionResult.NOT_DELETED);
    tenantState.forEachTenant(
        tenant -> {
          result.set(resourceDeletionCallback.apply(tenant));
          return !result.get().resourceDeleted();
        });
    return result.get();
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
    final var isAuthorized =
        permissionsBehavior.isAuthorizedWithResourceIdentifiers(
            command, resourceType, permissionType, resourceId);
    if (isAuthorized.isLeft()) {
      throw new ForbiddenException(isAuthorized.getLeft());
    }
  }

  /**
   * Whether the resource was deleted, and the partitions to distribute the deletion to; empty
   * partitions distribute to all.
   */
  private record DeletionResult(boolean resourceDeleted, Set<Integer> distributionPartitions) {
    private static final DeletionResult DELETED = new DeletionResult(true, Set.of());
    private static final DeletionResult NOT_DELETED = new DeletionResult(false, Set.of());

    private static DeletionResult deletedOn(final Set<Integer> distributionPartitions) {
      return new DeletionResult(true, distributionPartitions);
    }
  }

  private static final class NoSuchResourceException extends IllegalStateException {
    private static final String ERROR_MESSAGE_RESOURCE_NOT_FOUND =
        "Expected to delete resource but no resource found with key `%d`";

    private NoSuchResourceException(final long resourceKey) {
      super(String.format(ERROR_MESSAGE_RESOURCE_NOT_FOUND, resourceKey));
    }
  }

  private static final class ResourceDeletionInProgressException extends IllegalStateException {
    private static final String ERROR_MESSAGE_DELETION_IN_PROGRESS =
        "Expected to delete process definition with key `%d`, but it is already being deleted.";

    private ResourceDeletionInProgressException(final long resourceKey) {
      super(String.format(ERROR_MESSAGE_DELETION_IN_PROGRESS, resourceKey));
    }
  }
}
