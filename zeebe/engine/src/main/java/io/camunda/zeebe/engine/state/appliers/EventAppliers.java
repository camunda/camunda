/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.processing.scaling.ScaleUpStatusResponseApplier;
import io.camunda.zeebe.engine.processing.scaling.ScaledUpApplier;
import io.camunda.zeebe.engine.processing.scaling.ScalingUpApplier;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.EventApplier.NoSuchEventApplier.NoApplierForIntent;
import io.camunda.zeebe.engine.state.EventApplier.NoSuchEventApplier.NoApplierForVersion;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.CompensationSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.protocol.record.intent.EscalationIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MultiInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceResultIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.RuntimeInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Applies state changes from events to the {@link MutableProcessingState}.
 *
 * <p>Finds the correct {@link TypedEventApplier} and delegates.
 */
public final class EventAppliers implements EventApplier {

  public static final TypedEventApplier<Intent, RecordValue> NOOP_EVENT_APPLIER =
      (key, value) -> {};

  private final Map<Intent, Map<Integer, TypedEventApplier>> mapping = new HashMap<>();

  public EventAppliers registerEventAppliers(final MutableProcessingState state) {
    registerProcessInstanceEventAppliers(state);
    registerProcessInstanceCreationAppliers(state);
    registerProcessInstanceModificationAppliers(state);
    registerProcessInstanceMigrationAppliers();
    register(ProcessInstanceResultIntent.COMPLETED, NOOP_EVENT_APPLIER);
    register(ProcessInstanceBatchIntent.ACTIVATED, NOOP_EVENT_APPLIER);
    register(ProcessInstanceBatchIntent.TERMINATED, NOOP_EVENT_APPLIER);

    registerProcessAppliers(state);
    register(ErrorIntent.CREATED, new ErrorCreatedApplier(state.getBannedInstanceState()));
    registerDeploymentAppliers(state);

    registerMessageAppliers(state);
    registerMessageCorrelationAppliers(state);
    registerMessageSubscriptionAppliers(state);
    registerMessageStartEventSubscriptionAppliers(state);

    registerJobIntentEventAppliers(state);
    registerVariableEventAppliers(state);
    register(JobBatchIntent.ACTIVATED, new JobBatchActivatedApplier(state));
    registerIncidentEventAppliers(state);
    registerProcessMessageSubscriptionEventAppliers(state);
    registerTimeEventAppliers(state);
    registerProcessEventAppliers(state);

    registerDecisionAppliers(state);
    registerDecisionRequirementsAppliers(state);
    registerDecisionEvaluationAppliers(state);

    registerFormAppliers(state);

    registerResourceAppliers(state);

    registerUserTaskAppliers(state);

    registerSignalAppliers(state);

    registerCompensationSubscriptionApplier(state);

    registerCommandDistributionAppliers(state);
    registerEscalationAppliers();
    registerResourceDeletionAppliers();

    registerAdHocSubProcessInstructionAppliers(state);

    registerUserAppliers(state);
    registerAuthorizationAppliers(state);
    registerClockAppliers(state);
    registerRoleAppliers(state);
    registerGroupAppliers(state);
    registerScalingAppliers(state);
    registerTenantAppliers(state);
    registerMappingRuleAppliers(state);
    registerBatchOperationAppliers(state);
    registerIdentitySetupAppliers();
    registerAsyncRequestAppliers(state);
    registerUsageMetricsAppliers(state);

    registerMultiInstanceAppliers(state);

    return this;
  }

  private void registerMultiInstanceAppliers(final MutableProcessingState state) {
    register(
        MultiInstanceIntent.INPUT_COLLECTION_EVALUATED,
        new MultiInstanceInputCollectionEvaluatedApplier(state.getMultiInstanceState()));
  }

  private void registerUsageMetricsAppliers(final MutableProcessingState state) {
    register(UsageMetricIntent.EXPORTED, new UsageMetricsExportedApplier(state));
  }

  private void registerProcessAppliers(final MutableProcessingState state) {
    register(ProcessIntent.CREATED, 1, new ProcessCreatedV1Applier(state));
    register(ProcessIntent.CREATED, 2, new ProcessCreatedV2Applier(state));
    register(ProcessIntent.DELETING, new ProcessDeletingApplier(state));
    register(ProcessIntent.DELETED, new ProcessDeletedApplier(state));
  }

  private void registerTimeEventAppliers(final MutableProcessingState state) {
    register(TimerIntent.CREATED, new TimerCreatedApplier(state.getTimerState()));
    register(TimerIntent.CANCELED, new TimerCancelledApplier(state.getTimerState()));
    register(TimerIntent.TRIGGERED, new TimerTriggeredApplier(state.getTimerState()));
    register(TimerIntent.MIGRATED, new TimerInstanceMigratedApplier(state.getTimerState()));
  }

  private void registerDeploymentAppliers(final MutableProcessingState state) {
    register(DeploymentDistributionIntent.DISTRIBUTING, new DeploymentDistributionApplier(state));
    register(
        DeploymentDistributionIntent.COMPLETED,
        new DeploymentDistributionCompletedApplier(state.getDeploymentState()));

    register(
        DeploymentIntent.CREATED, 1, new DeploymentCreatedV1Applier(state.getDeploymentState()));
    register(DeploymentIntent.CREATED, 2, NOOP_EVENT_APPLIER);
    register(
        DeploymentIntent.CREATED, 3, new DeploymentCreatedV3Applier(state.getDeploymentState()));
    register(
        DeploymentIntent.DISTRIBUTED,
        new DeploymentDistributedApplier(state.getProcessState(), state.getDecisionState()));
    register(
        DeploymentIntent.FULLY_DISTRIBUTED,
        new DeploymentFullyDistributedApplier(state.getDeploymentState()));
    register(DeploymentIntent.RECONSTRUCTED, new DeploymentReconstructedApplier(state));
    register(
        DeploymentIntent.RECONSTRUCTED_ALL,
        new DeploymentReconstructedAllApplier(state.getDeploymentState()));
  }

  private void registerVariableEventAppliers(final MutableProcessingState state) {
    final var variableState = state.getVariableState();
    final var variableApplier = new VariableApplier(variableState);
    register(VariableIntent.CREATED, variableApplier);
    register(VariableIntent.UPDATED, variableApplier);
    register(VariableIntent.MIGRATED, new VariableMigratedApplier());
    register(VariableDocumentIntent.UPDATING, new VariableDocumentUpdatingApplier(variableState));
    register(VariableDocumentIntent.UPDATED, new VariableDocumentUpdatedApplier(variableState));
    register(
        VariableDocumentIntent.UPDATE_DENIED,
        new VariableDocumentUpdateDeniedApplier(variableState));
  }

  private void registerProcessInstanceEventAppliers(final MutableProcessingState state) {
    final var elementInstanceState = state.getElementInstanceState();
    final var eventScopeInstanceState = state.getEventScopeInstanceState();
    final var processState = state.getProcessState();
    final var variableState = state.getVariableState();
    final var bufferedStartMessageEventStateApplier =
        new BufferedStartMessageEventStateApplier(processState, state.getMessageState());
    final var multiInstanceState = state.getMultiInstanceState();

    register(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        1,
        new ProcessInstanceElementActivatingV1Applier(
            elementInstanceState, processState, eventScopeInstanceState));
    register(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        2,
        new ProcessInstanceElementActivatingV2Applier(
            elementInstanceState, processState, eventScopeInstanceState));
    register(
        ProcessInstanceIntent.ELEMENT_ACTIVATED,
        new ProcessInstanceElementActivatedApplier(elementInstanceState));
    register(
        ProcessInstanceIntent.ELEMENT_COMPLETING,
        new ProcessInstanceElementCompletingApplier(elementInstanceState));
    register(
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        new ProcessInstanceElementCompletedApplier(
            elementInstanceState,
            eventScopeInstanceState,
            variableState,
            processState,
            multiInstanceState,
            bufferedStartMessageEventStateApplier));
    register(
        ProcessInstanceIntent.ELEMENT_TERMINATING,
        new ProcessInstanceElementTerminatingApplier(elementInstanceState));
    register(
        ProcessInstanceIntent.ELEMENT_TERMINATED,
        new ProcessInstanceElementTerminatedApplier(
            elementInstanceState,
            eventScopeInstanceState,
            multiInstanceState,
            bufferedStartMessageEventStateApplier));
    register(
        ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN,
        new ProcessInstanceSequenceFlowTakenApplier(elementInstanceState, processState));
    register(
        ProcessInstanceIntent.SEQUENCE_FLOW_DELETED,
        new ProcessInstanceSequenceFlowDeletedApplier(elementInstanceState, processState));
    register(
        ProcessInstanceIntent.ELEMENT_MIGRATED,
        1,
        new ProcessInstanceElementMigratedV1Applier(elementInstanceState));
    register(
        ProcessInstanceIntent.ELEMENT_MIGRATED,
        2,
        new ProcessInstanceElementMigratedV2Applier(
            elementInstanceState, processState, state.getMessageState()));
    register(
        ProcessInstanceIntent.ANCESTOR_MIGRATED,
        new ProcessInstanceAncestorMigratedApplier(elementInstanceState));
    register(
        RuntimeInstructionIntent.INTERRUPTED,
        new RuntimeInstructionInterruptedApplier(elementInstanceState));
  }

  private void registerProcessInstanceCreationAppliers(final MutableProcessingState state) {
    final var processState = state.getProcessState();
    final var elementInstanceState = state.getElementInstanceState();
    final var usageMetricState = state.getUsageMetricState();

    register(
        ProcessInstanceCreationIntent.CREATED,
        1,
        new ProcessInstanceCreationCreatedV1Applier(processState, elementInstanceState));
    register(
        ProcessInstanceCreationIntent.CREATED,
        2,
        new ProcessInstanceCreationCreatedV2Applier(
            processState, elementInstanceState, usageMetricState));
  }

  private void registerProcessInstanceModificationAppliers(final MutableProcessingState state) {
    register(
        ProcessInstanceModificationIntent.MODIFIED,
        new ProcessInstanceModifiedEventApplier(
            state.getElementInstanceState(), state.getProcessState()));
  }

  private void registerProcessInstanceMigrationAppliers() {
    register(ProcessInstanceMigrationIntent.MIGRATED, NOOP_EVENT_APPLIER);
  }

  private void registerJobIntentEventAppliers(final MutableProcessingState state) {
    register(JobIntent.CANCELED, new JobCanceledApplier(state));
    register(JobIntent.COMPLETED, 1, new JobCompletedV1Applier(state));
    register(JobIntent.COMPLETED, 2, new JobCompletedApplierV2(state));
    register(JobIntent.CREATED, new JobCreatedApplier(state));
    register(JobIntent.ERROR_THROWN, new JobErrorThrownApplier(state));
    register(JobIntent.FAILED, new JobFailedApplier(state));
    register(JobIntent.YIELDED, new JobYieldedApplier(state));
    register(JobIntent.RETRIES_UPDATED, new JobRetriesUpdatedApplier(state));
    register(JobIntent.TIMED_OUT, new JobTimedOutApplier(state));
    register(JobIntent.RECURRED_AFTER_BACKOFF, new JobRecurredApplier(state));
    register(JobIntent.TIMEOUT_UPDATED, new JobTimeoutUpdatedApplier(state));
    register(JobIntent.UPDATED, new JobUpdatedApplier(state));
    register(JobIntent.MIGRATED, new JobMigratedApplier(state));
  }

  private void registerMessageAppliers(final MutableProcessingState state) {
    register(MessageIntent.PUBLISHED, new MessagePublishedApplier(state.getMessageState()));
    register(MessageIntent.EXPIRED, new MessageExpiredApplier(state.getMessageState()));
  }

  private void registerMessageCorrelationAppliers(final MutableProcessingState state) {
    register(MessageCorrelationIntent.CORRELATING, new MessageCorrelationCorrelatingApplier(state));
    register(MessageCorrelationIntent.CORRELATED, new MessageCorrelationCorrelatedApplier(state));
    register(
        MessageCorrelationIntent.NOT_CORRELATED, new MessageCorrelationNotCorrelatedApplier(state));
  }

  private void registerUserAppliers(final MutableProcessingState state) {
    register(UserIntent.CREATED, new UserCreatedApplier(state.getUserState()));
    register(UserIntent.UPDATED, new UserUpdatedApplier(state.getUserState()));
    register(UserIntent.DELETED, new UserDeletedApplier(state));
    register(UserIntent.INITIAL_ADMIN_CREATED, NOOP_EVENT_APPLIER);
  }

  private void registerMessageSubscriptionAppliers(final MutableProcessingState state) {
    register(
        MessageSubscriptionIntent.CREATED,
        new MessageSubscriptionCreatedApplier(state.getMessageSubscriptionState()));
    register(
        MessageSubscriptionIntent.CORRELATING,
        new MessageSubscriptionCorrelatingApplier(
            state.getMessageSubscriptionState(), state.getMessageState()));
    register(
        MessageSubscriptionIntent.CORRELATED,
        new MessageSubscriptionCorrelatedApplier(state.getMessageSubscriptionState()));
    register(
        MessageSubscriptionIntent.REJECTED,
        new MessageSubscriptionRejectedApplier(
            state.getMessageState(), state.getMessageSubscriptionState()));
    register(
        MessageSubscriptionIntent.DELETED,
        new MessageSubscriptionDeletedApplier(state.getMessageSubscriptionState()));
    register(
        MessageSubscriptionIntent.MIGRATED,
        new MessageSubscriptionMigratedApplier(state.getMessageSubscriptionState()));
  }

  private void registerMessageStartEventSubscriptionAppliers(final MutableProcessingState state) {
    register(
        MessageStartEventSubscriptionIntent.CREATED,
        new MessageStartEventSubscriptionCreatedApplier(
            state.getMessageStartEventSubscriptionState()));
    register(
        MessageStartEventSubscriptionIntent.CORRELATED,
        new MessageStartEventSubscriptionCorrelatedApplier(state.getMessageState()));
    register(
        MessageStartEventSubscriptionIntent.DELETED,
        new MessageStartEventSubscriptionDeletedApplier(
            state.getMessageStartEventSubscriptionState()));
  }

  private void registerIncidentEventAppliers(final MutableProcessingState state) {
    register(
        IncidentIntent.CREATED,
        new IncidentCreatedApplier(state.getIncidentState(), state.getJobState()));
    register(
        IncidentIntent.RESOLVED,
        1,
        new IncidentResolvedV1Applier(
            state.getIncidentState(), state.getJobState(), state.getElementInstanceState()));
    register(
        IncidentIntent.RESOLVED,
        2,
        new IncidentResolvedV2Applier(
            state.getIncidentState(), state.getJobState(), state.getElementInstanceState()));
    register(IncidentIntent.MIGRATED, new IncidentMigratedApplier(state.getIncidentState()));
  }

  private void registerProcessMessageSubscriptionEventAppliers(final MutableProcessingState state) {
    final MutableProcessMessageSubscriptionState subscriptionState =
        state.getProcessMessageSubscriptionState();

    register(
        ProcessMessageSubscriptionIntent.CREATING,
        new ProcessMessageSubscriptionCreatingApplier(subscriptionState));
    register(
        ProcessMessageSubscriptionIntent.CREATED,
        new ProcessMessageSubscriptionCreatedApplier(subscriptionState));
    register(
        ProcessMessageSubscriptionIntent.CORRELATED,
        new ProcessMessageSubscriptionCorrelatedApplier(subscriptionState));
    register(
        ProcessMessageSubscriptionIntent.DELETING,
        new ProcessMessageSubscriptionDeletingApplier(subscriptionState));
    register(
        ProcessMessageSubscriptionIntent.DELETED,
        new ProcessMessageSubscriptionDeletedApplier(subscriptionState));
    register(
        ProcessMessageSubscriptionIntent.MIGRATED,
        new ProcessMessageSubscriptionMigratedApplier(subscriptionState));
  }

  private void registerProcessEventAppliers(final MutableProcessingState state) {
    register(
        ProcessEventIntent.TRIGGERING,
        new ProcessEventTriggeringApplier(
            state.getEventScopeInstanceState(),
            state.getElementInstanceState(),
            state.getProcessState()));
    register(
        ProcessEventIntent.TRIGGERED,
        new ProcessEventTriggeredApplier(state.getEventScopeInstanceState()));
  }

  private void registerSignalAppliers(final MutableProcessingState state) {
    register(
        SignalSubscriptionIntent.CREATED,
        new SignalSubscriptionCreatedApplier(state.getSignalSubscriptionState()));
    register(
        SignalSubscriptionIntent.DELETED,
        new SignalSubscriptionDeletedApplier(state.getSignalSubscriptionState()));
    register(
        SignalSubscriptionIntent.MIGRATED,
        new SignalSubscriptionMigratedApplier(state.getSignalSubscriptionState()));
    register(SignalIntent.BROADCASTED, NOOP_EVENT_APPLIER);
  }

  private void registerDecisionAppliers(final MutableProcessingState state) {
    register(DecisionIntent.CREATED, 1, new DecisionCreatedV1Applier(state.getDecisionState()));
    register(DecisionIntent.CREATED, 2, new DecisionCreatedV2Applier(state.getDecisionState()));
    register(DecisionIntent.DELETED, new DecisionDeletedApplier(state.getDecisionState()));
  }

  private void registerDecisionRequirementsAppliers(final MutableProcessingState state) {
    register(
        DecisionRequirementsIntent.CREATED,
        new DecisionRequirementsCreatedApplier(state.getDecisionState()));
    register(
        DecisionRequirementsIntent.DELETED,
        new DecisionRequirementsDeletedApplier(state.getDecisionState()));
  }

  private void registerDecisionEvaluationAppliers(final MutableProcessingState state) {
    register(DecisionEvaluationIntent.EVALUATED, 1, NOOP_EVENT_APPLIER);
    register(
        DecisionEvaluationIntent.EVALUATED,
        2,
        new DecisionEvaluationV2Applier(state.getUsageMetricState()));
    register(DecisionEvaluationIntent.FAILED, NOOP_EVENT_APPLIER);
  }

  private void registerFormAppliers(final MutableProcessingState state) {
    register(FormIntent.CREATED, 1, new FormCreatedV1Applier(state.getFormState()));
    register(FormIntent.CREATED, 2, new FormCreatedV2Applier(state.getFormState()));
    register(FormIntent.DELETED, new FormDeletedApplier(state.getFormState()));
  }

  private void registerResourceAppliers(final MutableProcessingState state) {
    register(ResourceIntent.CREATED, new ResourceCreatedApplier(state.getResourceState()));
    register(ResourceIntent.DELETED, new ResourceDeletedApplier(state.getResourceState()));
    register(ResourceIntent.FETCHED, NOOP_EVENT_APPLIER);
  }

  private void registerUserTaskAppliers(final MutableProcessingState state) {
    register(UserTaskIntent.CREATING, new UserTaskCreatingApplier(state));
    register(UserTaskIntent.CREATING, 2, new UserTaskCreatingV2Applier(state));
    register(UserTaskIntent.CREATED, new UserTaskCreatedApplier(state));
    register(UserTaskIntent.CREATED, 2, new UserTaskCreatedV2Applier(state));
    register(UserTaskIntent.CANCELING, 1, new UserTaskCancelingV1Applier(state));
    register(UserTaskIntent.CANCELING, 2, new UserTaskCancelingV2Applier(state));
    register(UserTaskIntent.CANCELED, new UserTaskCanceledApplier(state));
    register(UserTaskIntent.COMPLETING, 1, new UserTaskCompletingV1Applier(state));
    register(UserTaskIntent.COMPLETING, 2, new UserTaskCompletingV2Applier(state));
    register(UserTaskIntent.COMPLETED, 1, new UserTaskCompletedV1Applier(state));
    register(UserTaskIntent.COMPLETED, 2, new UserTaskCompletedV2Applier(state));
    register(UserTaskIntent.ASSIGNING, 1, new UserTaskAssigningV1Applier(state));
    register(UserTaskIntent.ASSIGNING, 2, new UserTaskAssigningV2Applier(state));
    register(UserTaskIntent.ASSIGNED, 1, new UserTaskAssignedV1Applier(state));
    register(UserTaskIntent.ASSIGNED, 2, new UserTaskAssignedV2Applier(state));
    register(UserTaskIntent.ASSIGNED, 3, new UserTaskAssignedV3Applier(state));
    register(UserTaskIntent.CLAIMING, new UserTaskClaimingApplier(state));
    register(UserTaskIntent.UPDATING, 1, new UserTaskUpdatingV1Applier(state));
    register(UserTaskIntent.UPDATING, 2, new UserTaskUpdatingV2Applier(state));
    register(UserTaskIntent.UPDATED, 1, new UserTaskUpdatedV1Applier(state));
    register(UserTaskIntent.UPDATED, 2, new UserTaskUpdatedV2Applier(state));
    register(UserTaskIntent.MIGRATED, new UserTaskMigratedApplier(state));
    register(UserTaskIntent.CORRECTED, new UserTaskCorrectedApplier(state));
    register(UserTaskIntent.COMPLETION_DENIED, new UserTaskCompletionDeniedApplier(state));
    register(UserTaskIntent.ASSIGNMENT_DENIED, new UserTaskAssignmentDeniedApplier(state));
    register(UserTaskIntent.UPDATE_DENIED, new UserTaskUpdateDeniedApplier(state));
  }

  private void registerCompensationSubscriptionApplier(
      final MutableProcessingState processingState) {
    register(
        CompensationSubscriptionIntent.CREATED,
        new CompensationSubscriptionCreatedApplier(
            processingState.getCompensationSubscriptionState()));
    register(
        CompensationSubscriptionIntent.TRIGGERED,
        new CompensationSubscriptionTriggeredApplier(
            processingState.getCompensationSubscriptionState()));
    register(
        CompensationSubscriptionIntent.COMPLETED,
        new CompensationSubscriptionCompletedApplier(
            processingState.getCompensationSubscriptionState()));
    register(
        CompensationSubscriptionIntent.DELETED,
        new CompensationSubscriptionDeletedApplier(
            processingState.getCompensationSubscriptionState()));
    register(
        CompensationSubscriptionIntent.MIGRATED,
        new CompensationSubscriptionMigratedApplier(
            processingState.getCompensationSubscriptionState()));
  }

  private void registerCommandDistributionAppliers(final MutableProcessingState state) {
    final var distributionState = state.getDistributionState();
    register(
        CommandDistributionIntent.STARTED,
        new CommandDistributionStartedApplier(distributionState));
    register(
        CommandDistributionIntent.DISTRIBUTING,
        new CommandDistributionDistributingApplier(distributionState));
    register(
        CommandDistributionIntent.ACKNOWLEDGED,
        new CommandDistributionAcknowledgedApplier(distributionState));
    register(
        CommandDistributionIntent.FINISHED,
        new CommandDistributionFinishedApplier(distributionState));
    register(
        CommandDistributionIntent.ENQUEUED,
        new CommandDistributionEnqueuedApplier(distributionState));
    register(
        CommandDistributionIntent.CONTINUATION_REQUESTED,
        new CommandDistributionContinuationRequestedApplier(distributionState));
    register(
        CommandDistributionIntent.CONTINUED,
        new CommandDistributionContinuedApplier(distributionState));
  }

  private void registerAuthorizationAppliers(final MutableProcessingState state) {
    register(
        AuthorizationIntent.CREATED,
        new AuthorizationCreatedApplier(state.getAuthorizationState()));
    register(
        AuthorizationIntent.DELETED,
        new AuthorizationDeletedApplier(state.getAuthorizationState()));
    register(
        AuthorizationIntent.UPDATED,
        new AuthorizationUpdatedApplier(state.getAuthorizationState()));
  }

  private void registerEscalationAppliers() {
    register(EscalationIntent.ESCALATED, NOOP_EVENT_APPLIER);
    register(EscalationIntent.NOT_ESCALATED, NOOP_EVENT_APPLIER);
  }

  private void registerResourceDeletionAppliers() {
    register(ResourceDeletionIntent.DELETING, NOOP_EVENT_APPLIER);
    register(ResourceDeletionIntent.DELETED, NOOP_EVENT_APPLIER);
  }

  private void registerAdHocSubProcessInstructionAppliers(final MutableProcessingState state) {
    register(AdHocSubProcessInstructionIntent.ACTIVATED, NOOP_EVENT_APPLIER);
    register(
        AdHocSubProcessInstructionIntent.COMPLETED,
        new AdHocSubProcessInstructionCompletedApplier(state.getElementInstanceState()));
  }

  private void registerClockAppliers(final MutableProcessingState state) {
    register(ClockIntent.PINNED, new ClockPinnedApplier(state.getClockState()));
    register(ClockIntent.RESETTED, new ClockResettedApplier(state.getClockState()));
  }

  private void registerRoleAppliers(final MutableProcessingState state) {
    register(RoleIntent.CREATED, new RoleCreatedApplier(state.getRoleState()));
    register(RoleIntent.UPDATED, new RoleUpdatedApplier(state.getRoleState()));
    register(RoleIntent.ENTITY_ADDED, new RoleEntityAddedApplier(state));
    register(RoleIntent.ENTITY_REMOVED, new RoleEntityRemovedApplier(state));
    register(RoleIntent.DELETED, new RoleDeletedApplier(state.getRoleState()));
  }

  private void registerGroupAppliers(final MutableProcessingState state) {
    register(GroupIntent.CREATED, new GroupCreatedApplier(state.getGroupState()));
    register(GroupIntent.UPDATED, new GroupUpdatedApplier(state.getGroupState()));
    register(GroupIntent.ENTITY_ADDED, new GroupEntityAddedApplier(state));
    register(GroupIntent.ENTITY_REMOVED, new GroupEntityRemovedApplier(state));
    register(GroupIntent.DELETED, new GroupDeletedApplier(state));
  }

  private void registerScalingAppliers(final MutableProcessingState state) {
    register(ScaleIntent.SCALING_UP, new ScalingUpApplier(state.getRoutingState()));
    register(ScaleIntent.SCALED_UP, new ScaledUpApplier(state.getRoutingState()));
    register(ScaleIntent.STATUS_RESPONSE, new ScaleUpStatusResponseApplier());
    register(ScaleIntent.PARTITION_BOOTSTRAPPED, new PartitionBootstrappedApplier(state));
  }

  private void registerTenantAppliers(final MutableProcessingState state) {
    register(TenantIntent.CREATED, new TenantCreatedApplier(state.getTenantState()));
    register(TenantIntent.UPDATED, new TenantUpdatedApplier(state.getTenantState()));
    register(TenantIntent.ENTITY_ADDED, new TenantEntityAddedApplier(state));
    register(TenantIntent.ENTITY_REMOVED, new TenantEntityRemovedApplier(state));
    register(TenantIntent.DELETED, new TenantDeletedApplier(state.getTenantState()));
  }

  private void registerMappingRuleAppliers(final MutableProcessingState state) {
    register(MappingRuleIntent.CREATED, new MappingRuleCreatedApplier(state.getMappingRuleState()));
    register(MappingRuleIntent.DELETED, new MappingRuleDeletedApplier(state.getMappingRuleState()));
    register(MappingRuleIntent.UPDATED, new MappingRuleUpdatedApplier(state.getMappingRuleState()));
  }

  private void registerBatchOperationAppliers(final MutableProcessingState state) {
    register(
        BatchOperationIntent.CREATED,
        new BatchOperationCreatedApplier(state.getBatchOperationState()));
    register(
        BatchOperationIntent.INITIALIZING,
        new BatchOperationInitializingApplier(state.getBatchOperationState()));
    register(
        BatchOperationIntent.INITIALIZED,
        new BatchOperationInitializedApplier(state.getBatchOperationState()));
    register(
        BatchOperationIntent.FAILED,
        new BatchOperationFailedApplier(state.getBatchOperationState()));

    register(
        BatchOperationChunkIntent.CREATED,
        new BatchOperationChunkCreatedApplier(state.getBatchOperationState()));
    register(
        BatchOperationExecutionIntent.EXECUTING,
        new BatchOperationExecutingApplier(state.getBatchOperationState()));
    register(BatchOperationExecutionIntent.EXECUTED, NOOP_EVENT_APPLIER);
    register(
        BatchOperationIntent.CANCELED,
        new BatchOperationCanceledApplier(state.getBatchOperationState()));
    register(
        BatchOperationIntent.SUSPENDED,
        new BatchOperatioSuspendedApplier(state.getBatchOperationState()));
    register(
        BatchOperationIntent.RESUMED,
        new BatchOperationResumedApplier(state.getBatchOperationState()));
    register(
        BatchOperationIntent.COMPLETED,
        new BatchOperationCompletedApplier(state.getBatchOperationState()));
    register(
        BatchOperationIntent.PARTITION_COMPLETED,
        new BatchOperationPartitionCompletedApplier(
            state.getBatchOperationState(), state.getPartitionId()));
    register(
        BatchOperationIntent.PARTITION_FAILED,
        new BatchOperationPartitionFailedApplier(
            state.getBatchOperationState(), state.getPartitionId()));
  }

  private void registerIdentitySetupAppliers() {
    register(IdentitySetupIntent.INITIALIZED, NOOP_EVENT_APPLIER);
  }

  private void registerAsyncRequestAppliers(final MutableProcessingState state) {
    final var asyncRequestState = state.getAsyncRequestState();
    register(AsyncRequestIntent.RECEIVED, new AsyncRequestReceivedApplier(asyncRequestState));
    register(AsyncRequestIntent.PROCESSED, new AsyncRequestProcessedApplier(asyncRequestState));
  }

  private <I extends Intent> void register(final I intent, final TypedEventApplier<I, ?> applier) {
    register(intent, RecordMetadata.DEFAULT_RECORD_VERSION, applier);
  }

  <I extends Intent> void register(
      final I intent, final int version, final TypedEventApplier<I, ?> applier) {
    Objects.requireNonNull(intent, "Intent must not be null");
    Objects.requireNonNull(applier, "Applier must not be null");
    if (version < 1) {
      throw new IllegalArgumentException("Version must be greater than 0");
    }
    if (!intent.isEvent()) {
      throw new IllegalArgumentException("Only event intents can be registered");
    }

    final var previousApplier =
        mapping.computeIfAbsent(intent, unused -> new HashMap<>()).putIfAbsent(version, applier);
    if (previousApplier != null) {
      throw new IllegalArgumentException(
          String.format(
              "Applier for intent '%s' and version '%d' is already registered", intent, version));
    }
  }

  @Override
  public int getLatestVersion(final Intent intent) {
    return mapping.getOrDefault(intent, new HashMap<>()).keySet().stream()
        .max(Comparator.naturalOrder())
        .orElse(-1);
  }

  @Override
  public void applyState(
      final long key, final Intent intent, final RecordValue value, final int recordVersion)
      throws NoSuchEventApplier {
    final var applierForIntent = mapping.get(intent);
    if (applierForIntent == null) {
      throw new NoApplierForIntent(intent);
    }
    final var applierForVersion = applierForIntent.get(recordVersion);
    if (applierForVersion == null) {
      throw new NoApplierForVersion(intent, recordVersion, getLatestVersion(intent));
    }

    applierForVersion.applyState(key, value);
  }

  public Map<Intent, Map<Integer, TypedEventApplier>> getRegisteredAppliers() {
    return Map.copyOf(mapping);
  }
}
