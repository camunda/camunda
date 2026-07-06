/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.metrics.ProcessDefinitionMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.deployment.StartEventSubscriptionManager;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthenticatedAuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.exception.ForbiddenException;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.resource.ResourceDeletionExceptions.ActiveProcessInstancesException;
import io.camunda.zeebe.engine.processing.resource.ResourceDeletionExceptions.NoSuchResourceException;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.engine.state.immutable.FormState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ResourceState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

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
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final FormState formState;
  private final ResourceState resourceState;
  private final TenantState tenantState;
  private final ProcessDeletionBehavior processDeletionBehavior;
  private final FormDeletionBehavior formDeletionBehavior;
  private final DecisionRequirementsDeletionBehavior decisionRequirementsDeletionBehavior;
  private final ResourceDeletionBehavior resourceDeletionBehavior;

  public ResourceDeletionDeleteProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior,
      final BpmnBehaviors bpmnBehaviors,
      final AuthorizationCheckBehavior authCheckBehavior,
      final ProcessDefinitionMetrics processDefinitionMetrics) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    decisionState = processingState.getDecisionState();
    this.commandDistributionBehavior = commandDistributionBehavior;
    processState = processingState.getProcessState();
    this.authCheckBehavior = authCheckBehavior;
    formState = processingState.getFormState();
    resourceState = processingState.getResourceState();
    tenantState = processingState.getTenantState();

    final var startEventSubscriptionManager =
        new StartEventSubscriptionManager(processingState, keyGenerator, stateWriter);
    final var startEventSubscriptions =
        new StartEventSubscriptions(
            bpmnBehaviors.expressionProcessor(),
            bpmnBehaviors.catchEventBehavior(),
            startEventSubscriptionManager);

    processDeletionBehavior =
        new ProcessDeletionBehavior(
            stateWriter,
            writers.command(),
            keyGenerator,
            processingState,
            bpmnBehaviors.catchEventBehavior(),
            startEventSubscriptionManager,
            startEventSubscriptions,
            processDefinitionMetrics);

    formDeletionBehavior = new FormDeletionBehavior(stateWriter, keyGenerator);

    decisionRequirementsDeletionBehavior =
        new DecisionRequirementsDeletionBehavior(
            stateWriter, writers.command(), keyGenerator, processingState.getDecisionState());

    resourceDeletionBehavior = new ResourceDeletionBehavior(stateWriter, keyGenerator);
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
          () -> processDeletionBehavior.deleteProcess(process, command, eventKey));
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
          () ->
              decisionRequirementsDeletionBehavior.deleteDecisionRequirements(
                  drg, command, eventKey));
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
          () -> formDeletionBehavior.deleteForm(form));
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
          () -> resourceDeletionBehavior.deleteResource(resource));
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

  private void deleteHistory(
      final long eventKey, final TypedRecord<ResourceDeletionRecord> command) {
    if (command.isCommandDistributed()) {
      // We should not create batch operations for distributed commands. This gets handled by the
      // batch operation creator itself.
      return;
    }
    final var commandValue = command.getValue();
    final var resourceType = commandValue.getResourceType();

    // We cannot rely on the existing checkAuthorization method as it would swallow the not found in
    // case the caller has no access to the tenant.
    final var authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.RESOURCE)
            .permissionType(
                resourceType == ResourceType.PROCESS_DEFINITION
                    ? PermissionType.DELETE_PROCESS
                    : PermissionType.DELETE_DRD)
            .addResourceId(commandValue.getResourceId())
            .tenantId(commandValue.getTenantId())
            .build();
    final var authResponse = authCheckBehavior.isAuthorizedOrInternalCommand(authRequest);
    if (authResponse.isLeft()) {
      if (authResponse.getLeft().type() == RejectionType.NOT_FOUND) {
        throw new NoSuchResourceException(commandValue.getResourceKey());
      } else {
        throw new ForbiddenException(authRequest);
      }
    }

    switch (resourceType) {
      case PROCESS_DEFINITION ->
          processDeletionBehavior.deleteProcessInstanceHistory(
              commandValue.getResourceKey(), eventKey, commandValue);
      case DECISION_REQUIREMENTS ->
          decisionRequirementsDeletionBehavior.deleteDecisionInstanceHistory(
              commandValue.getResourceKey(), eventKey, commandValue);
      default -> {
        // No history to delete for forms and unknown resources
        // This should not be reached as SUPPORTED_HISTORY_DELETION_TYPES filters these out
      }
    }
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

  private boolean tryToDeleteResourceAssignedToDefaultTenant(
      final Function<String, Boolean> resourceDeletionCallback) {
    return resourceDeletionCallback.apply(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

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
}
