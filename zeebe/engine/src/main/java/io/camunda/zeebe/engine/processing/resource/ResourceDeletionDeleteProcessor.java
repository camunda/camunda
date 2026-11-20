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

import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnProcessDeletionBehavior;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor.EvaluationException;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.StartEventSubscriptionManager;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
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
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.model.bpmn.util.time.Timer;
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
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class ResourceDeletionDeleteProcessor
    implements DistributedTypedRecordProcessor<ResourceDeletionRecord> {

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
  private final ExpressionProcessor expressionProcessor;
  private final StartEventSubscriptionManager startEventSubscriptionManager;
  private final FormState formState;
  private final ResourceState resourceState;
  private final RoutingInfo routingInfo;

  public ResourceDeletionDeleteProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior,
      final BpmnBehaviors bpmnBehaviors,
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
    expressionProcessor = bpmnBehaviors.expressionBehavior();
    startEventSubscriptionManager =
        new StartEventSubscriptionManager(processingState, keyGenerator, stateWriter);
    formState = processingState.getFormState();
    resourceState = processingState.getResourceState();
    this.routingInfo = routingInfo;
  }

  @Override
  public void processNewCommand(final TypedRecord<ResourceDeletionRecord> command) {
    final var value = command.getValue();
    final long eventKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(eventKey, ResourceDeletionIntent.DELETING, value);

    tryDeleteResources(command);

    stateWriter.appendFollowUpEvent(eventKey, ResourceDeletionIntent.DELETED, value);
    commandDistributionBehavior
        .withKey(eventKey)
        .inQueue(DistributionQueue.DEPLOYMENT)
        .distribute(command);
    responseWriter.writeAcceptedResponseOnCommand(
        eventKey, ResourceDeletionIntent.DELETING, value, command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ResourceDeletionRecord> command) {
    final var value = command.getValue();
    stateWriter.appendFollowUpEvent(command.getKey(), ResourceDeletionIntent.DELETING, value);

    tryDeleteResources(command);

    stateWriter.appendFollowUpEvent(command.getKey(), ResourceDeletionIntent.DELETED, value);
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ResourceDeletionRecord> command, final Throwable error) {
    if (error instanceof final NoSuchResourceException exception) {
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

  private void tryDeleteResources(final TypedRecord<ResourceDeletionRecord> command) {
    final var value = command.getValue();

    var drainingDeletionInFlight = false;
    for (final String tenantId : getAuthorizedTenants(command)) {
      final var process = processState.getProcessByKeyAndTenant(value.getResourceKey(), tenantId);
      if (process != null) {
        // A resting PENDING_DELETION counts as active and stays deletable, letting an operator
        // clear a stuck row via the normal API.
        if (process.isActive()) {
          setTenantId(command, tenantId);
          deleteProcess(process);
          return;
        }
        // found but not active: deletion already in flight, so the caller rejects as INVALID_STATE
        drainingDeletionInFlight = true;
        continue;
      }

      final var drgOptional =
          decisionState.findDecisionRequirementsByTenantAndKey(tenantId, value.getResourceKey());
      if (drgOptional.isPresent()) {
        setTenantId(command, tenantId);
        deleteDecisionRequirements(drgOptional.get());
        return;
      }

      final var formOptional = formState.findFormByKey(value.getResourceKey(), tenantId);
      if (formOptional.isPresent()) {
        setTenantId(command, tenantId);
        deleteForm(formOptional.get());
        return;
      }

      final var resourceOptional =
          resourceState.findResourceByKey(value.getResourceKey(), tenantId);
      if (resourceOptional.isPresent()) {
        setTenantId(command, tenantId);
        deleteResource(resourceOptional.get());
        return;
      }
    }

    if (drainingDeletionInFlight || processState.hasPendingDeletion(value.getResourceKey())) {
      throw new ResourceDeletionInProgressException(value.getResourceKey());
    }
    throw new NoSuchResourceException(value.getResourceKey());
  }

  private void deleteDecisionRequirements(final DeployedDrg drg) {
    decisionState
        .findDecisionsByTenantAndDecisionRequirementsKey(
            drg.getTenantId(), drg.getDecisionRequirementsKey())
        .forEach(this::deleteDecision);

    final var drgRecord =
        new DecisionRequirementsRecord()
            .setDecisionRequirementsId(bufferAsString(drg.getDecisionRequirementsId()))
            .setDecisionRequirementsName(bufferAsString(drg.getDecisionRequirementsName()))
            .setDecisionRequirementsVersion(drg.getDecisionRequirementsVersion())
            .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
            .setResourceName(bufferAsString(drg.getResourceName()))
            .setChecksum(drg.getChecksum())
            .setResource(drg.getResource())
            .setTenantId(drg.getTenantId());

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

  private void deleteProcess(final DeployedProcess process) {
    // We don't add the checksum or resource in this event. The checksum is not easily available
    // and the resources are left out to prevent exceeding the maximum batch size.
    final var processIdBuffer = process.getBpmnProcessId();
    final var tenantId = process.getTenantId();
    final var processRecord =
        new ProcessRecord()
            .setBpmnProcessId(processIdBuffer)
            .setVersion(process.getVersion())
            .setVersionTag(process.getVersionTag())
            .setKey(process.getKey())
            .setResourceName(process.getResourceName())
            .setTenantId(tenantId)
            .setDeploymentKey(process.getDeploymentKey());

    final String processId = processRecord.getBpmnProcessId();
    final var latestVersion = processState.getLatestProcessVersion(processId, tenantId);

    // 8.7 has no partition scaling, so the current partitions are the ones that must drain
    processRecord.setDrainPartitions(routingInfo.partitions());
    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), ProcessIntent.DRAINING, processRecord);

    // If we are deleting the latest version we must unsubscribe the start events
    if (latestVersion == process.getVersion()) {
      unsubscribeStartEvents(process);

      // Hand the start subscription down to the latest ACTIVE version; DRAINING/deleted versions
      // reject new instances so must stay unsubscribed.
      findLatestActiveVersionBelow(processIdBuffer, processId, latestVersion, tenantId)
          .ifPresent(this::resubscribeStartEvents);
    }

    final var bannedInstances = bannedInstanceState.getBannedProcessInstanceKeys();
    final boolean finalizedImmediately =
        !elementInstanceState.hasActiveProcessInstances(process.getKey(), bannedInstances);

    if (finalizedImmediately) {
      processDeletionBehavior.finalizeDeletion(processRecord);
    }
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

  private void resubscribeStartEvents(final DeployedProcess deployedProcess) {
    if (deployedProcess == null) {
      return;
    }

    final var process = deployedProcess.getProcess();
    if (process.hasTimerStartEvent()) {
      process.getStartEvents().stream()
          .filter(ExecutableCatchEventElement::isTimer)
          .forEach(
              timerStartEvent -> {
                final Either<Failure, Timer> failureOrTimer =
                    timerStartEvent
                        .getTimerFactory()
                        .apply(expressionProcessor, NO_ELEMENT_INSTANCE);

                if (failureOrTimer.isLeft()) {
                  throw new EvaluationException(failureOrTimer.getLeft().getMessage());
                }

                catchEventBehavior.subscribeToTimerEvent(
                    NO_ELEMENT_INSTANCE,
                    NO_ELEMENT_INSTANCE,
                    deployedProcess.getKey(),
                    timerStartEvent.getId(),
                    deployedProcess.getTenantId(),
                    failureOrTimer.get());
              });
    }

    startEventSubscriptionManager.openStartEventSubscriptions(deployedProcess);
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

  private List<String> getAuthorizedTenants(final TypedRecord<ResourceDeletionRecord> command) {
    final String tenantId = command.getValue().getTenantId();
    if (tenantId.isEmpty()) {
      return (List)
          command.getAuthorizations().getOrDefault(Authorization.AUTHORIZED_TENANTS, List.of());
    }

    return List.of(tenantId);
  }

  private void setTenantId(
      final TypedRecord<ResourceDeletionRecord> command, final String tenantId) {
    command.getValue().setTenantId(tenantId);
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
