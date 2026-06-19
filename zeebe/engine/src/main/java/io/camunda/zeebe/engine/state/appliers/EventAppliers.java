/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.EventApplier.NoSuchEventApplier.NoApplierForIntent;
import io.camunda.zeebe.engine.state.EventApplier.NoSuchEventApplier.NoApplierForVersion;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.ApplierVersionId;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.CompensationSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ConditionalEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.protocol.record.intent.EscalationIntent;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
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
import io.camunda.zeebe.protocol.record.intent.ResourceReexportIntent;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * Applies state changes from events to the {@link MutableProcessingState}.
 *
 * <p>Finds the correct {@link TypedEventApplier} and delegates.
 */
public final class EventAppliers implements EventApplier {

  public static final TypedEventApplier<Intent, RecordValue> NOOP_EVENT_APPLIER =
      (key, value) -> {};

  /**
   * Allowlist of {@code (intent, version)} pairs with {@code version > 1} that are <em>not</em>
   * ECV-gated. Two populations belong here:
   *
   * <ul>
   *   <li><b>Pre-ECV legacy.</b> Multi-version appliers that predated the ECV mechanism. They use
   *       record-version selection for on-disk compatibility but were never gated by an ECV
   *       ordinal.
   *   <li><b>Retired ECV-gated appliers.</b> Once the binary's minimum supported ordinal exceeds a
   *       capability's ordinal, the gate is no longer interesting — every cluster running this
   *       binary is past it. To remove the {@code features.isActive(Capability.X)} branching, the
   *       catalog entry's {@code appliers()} set is emptied, the applier's {@code gatedBy()}
   *       returns BASELINE, and the {@code (intent, version)} pair is added here. The ordinal
   *       itself stays in the {@link Capability} enum — ordinals are never reused.
   * </ul>
   *
   * <p>A {@code version > 1} applier that defaults to {@link Capability#BASELINE} but is not on
   * this list fails registration loudly — the engine refuses to silently treat it as un-gated.
   */
  private static final Set<ApplierVersionId> BASELINE_APPLIERS =
      Set.of(
          new ApplierVersionId(BatchOperationChunkIntent.CREATED, 2),
          new ApplierVersionId(BatchOperationIntent.CREATED, 2),
          new ApplierVersionId(BatchOperationIntent.RESUMED, 2),
          new ApplierVersionId(BatchOperationIntent.SUSPENDED, 2),
          new ApplierVersionId(CommandDistributionIntent.DISTRIBUTING, 2),
          new ApplierVersionId(DecisionEvaluationIntent.EVALUATED, 2),
          new ApplierVersionId(DecisionIntent.CREATED, 2),
          new ApplierVersionId(DeploymentIntent.CREATED, 2),
          new ApplierVersionId(DeploymentIntent.CREATED, 3),
          new ApplierVersionId(FormIntent.CREATED, 2),
          new ApplierVersionId(IncidentIntent.RESOLVED, 2),
          new ApplierVersionId(IncidentIntent.RESOLVED, 3),
          new ApplierVersionId(JobIntent.CANCELED, 2),
          new ApplierVersionId(JobIntent.CANCELED, 3),
          new ApplierVersionId(JobIntent.COMPLETED, 2),
          new ApplierVersionId(JobIntent.COMPLETED, 3),
          new ApplierVersionId(JobIntent.CREATED, 2),
          new ApplierVersionId(JobIntent.ERROR_THROWN, 2),
          new ApplierVersionId(JobIntent.FAILED, 2),
          new ApplierVersionId(JobIntent.TIMED_OUT, 2),
          new ApplierVersionId(JobIntent.UPDATED, 2),
          new ApplierVersionId(MessageIntent.EXPIRED, 2),
          new ApplierVersionId(ProcessEventIntent.TRIGGERING, 2),
          new ApplierVersionId(ProcessEventIntent.TRIGGERING, 3),
          new ApplierVersionId(ProcessInstanceCreationIntent.CREATED, 2),
          new ApplierVersionId(ProcessInstanceIntent.ELEMENT_ACTIVATING, 2),
          new ApplierVersionId(ProcessInstanceIntent.ELEMENT_ACTIVATING, 3),
          new ApplierVersionId(ProcessInstanceIntent.ELEMENT_COMPLETED, 2),
          new ApplierVersionId(ProcessInstanceIntent.ELEMENT_MIGRATED, 2),
          new ApplierVersionId(ProcessInstanceIntent.ELEMENT_MIGRATED, 3),
          new ApplierVersionId(ProcessInstanceIntent.ELEMENT_MIGRATED, 4),
          new ApplierVersionId(ProcessInstanceIntent.ELEMENT_MIGRATED, 5),
          new ApplierVersionId(ProcessInstanceIntent.ELEMENT_TERMINATED, 2),
          new ApplierVersionId(ProcessIntent.CREATED, 2),
          new ApplierVersionId(TimerIntent.CREATED, 2),
          new ApplierVersionId(UserTaskIntent.ASSIGNED, 2),
          new ApplierVersionId(UserTaskIntent.ASSIGNED, 3),
          new ApplierVersionId(UserTaskIntent.ASSIGNED, 4),
          new ApplierVersionId(UserTaskIntent.ASSIGNING, 2),
          new ApplierVersionId(UserTaskIntent.ASSIGNING, 3),
          new ApplierVersionId(UserTaskIntent.CANCELED, 2),
          new ApplierVersionId(UserTaskIntent.CANCELING, 2),
          new ApplierVersionId(UserTaskIntent.CANCELING, 3),
          new ApplierVersionId(UserTaskIntent.CANCELING, 4),
          new ApplierVersionId(UserTaskIntent.CLAIMING, 2),
          new ApplierVersionId(UserTaskIntent.COMPLETED, 2),
          new ApplierVersionId(UserTaskIntent.COMPLETED, 3),
          new ApplierVersionId(UserTaskIntent.COMPLETING, 2),
          new ApplierVersionId(UserTaskIntent.COMPLETING, 3),
          new ApplierVersionId(UserTaskIntent.CREATED, 2),
          new ApplierVersionId(UserTaskIntent.CREATED, 3),
          new ApplierVersionId(UserTaskIntent.CREATING, 2),
          new ApplierVersionId(UserTaskIntent.CREATING, 3),
          new ApplierVersionId(UserTaskIntent.UPDATED, 2),
          new ApplierVersionId(UserTaskIntent.UPDATED, 3),
          new ApplierVersionId(UserTaskIntent.UPDATING, 2),
          new ApplierVersionId(UserTaskIntent.UPDATING, 3));

  private final Map<Intent, Map<Integer, TypedEventApplier>> mapping = new HashMap<>();
  // Per-(intent, version) Engine Capability Version requirement, copied from
  // ClusterVersionCatalog at registration. A version absent from this map is unconditionally
  // eligible (always available). Used by selectVersionFor to pick the highest version safe to
  // emit under the current active ECV. Value is the required ordinal.
  private final Map<Intent, Map<Integer, Integer>> requirements = new HashMap<>();
  // Cached highest registered version per intent. Maintained at register() time so the hot path
  // (selectVersionFor on intents without gated versions) reduces to one HashMap lookup.
  private final Map<Intent, Integer> latestVersionByIntent = new HashMap<>();
  // Lookup of the cluster's currently active ECV ordinal — wired by the engine after registration.
  // Defaults to Integer.MAX_VALUE so all requirements are trivially satisfied for tests that
  // don't wire ECV.
  private IntSupplier activeOrdinalSupplier = () -> Integer.MAX_VALUE;

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
    registerMessageStartProcessInstanceRequestAppliers(state);
    registerMessageStartCorrelationKeyLockReleaseAppliers(state);

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
    registerClusterVersionAppliers(state);
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
    registerClusterVariableEventAppliers(state);
    registerHistoryDeletionAppliers();
    registerConditionalSubscriptionAppliers(state);
    registerConditionalEvaluationAppliers();
    registerExpressionEvaluationEventAppliers();
    registerGlobalListenersEventAppliers(state);
    registerJobMetricsBatchEventAppliers(state);
    registerAgentInstanceEventAppliers(state);
    registerAgentHistoryEventAppliers();
    // Boot-time invariant: every applier the ClusterVersionCatalog declares must actually be
    // wired above. Catches the "added a Capability entry but forgot to register the applier"
    // mistake before any traffic. Cheap — O(catalog size).
    ClusterVersionCatalog.validateApplierCoverage(snapshotRegisteredApplierIds());
    return this;
  }

  private Set<ApplierVersionId> snapshotRegisteredApplierIds() {
    final var ids = new HashSet<ApplierVersionId>();
    for (final var entry : mapping.entrySet()) {
      for (final var version : entry.getValue().keySet()) {
        ids.add(new ApplierVersionId(entry.getKey(), version));
      }
    }
    return ids;
  }

  private void registerAgentHistoryEventAppliers() {
    register(AgentHistoryIntent.CREATED, NOOP_EVENT_APPLIER);
    register(AgentHistoryIntent.COMMITTED, NOOP_EVENT_APPLIER);
    register(AgentHistoryIntent.DISCARDED, NOOP_EVENT_APPLIER);
  }

  private void registerAgentInstanceEventAppliers(final MutableProcessingState state) {
    register(
        AgentInstanceIntent.CREATED,
        new AgentInstanceCreatedApplier(
            state.getAgentInstanceState(), state.getElementInstanceState()));
    register(
        AgentInstanceIntent.UPDATED,
        new AgentInstanceUpdatedApplier(
            state.getAgentInstanceState(), state.getElementInstanceState()));
    register(AgentInstanceIntent.COMPLETED, NOOP_EVENT_APPLIER);
  }

  private void registerJobMetricsBatchEventAppliers(final MutableProcessingState state) {
    register(JobMetricsBatchIntent.EXPORTED, new JobMetricsBatchExportedApplier(state));
  }

  private void registerExpressionEvaluationEventAppliers() {
    register(ExpressionIntent.EVALUATED, NOOP_EVENT_APPLIER);
  }

  private void registerConditionalSubscriptionAppliers(final MutableProcessingState state) {
    register(
        ConditionalSubscriptionIntent.CREATED,
        new ConditionalSubscriptionCreatedApplier(state.getConditionalSubscriptionState()));
    register(
        ConditionalSubscriptionIntent.TRIGGERED,
        new ConditionalSubscriptionTriggeredApplier(state.getConditionalSubscriptionState()));
    register(
        ConditionalSubscriptionIntent.DELETED,
        new ConditionalSubscriptionDeletedApplier(state.getConditionalSubscriptionState()));
    register(
        ConditionalSubscriptionIntent.MIGRATED,
        new ConditionalSubscriptionMigratedApplier(state.getConditionalSubscriptionState()));
  }

  private void registerGlobalListenersEventAppliers(final MutableProcessingState state) {
    register(GlobalListenerBatchIntent.CONFIGURED, new GlobalListenerBatchConfiguredApplier(state));
    register(GlobalListenerIntent.CREATED, new GlobalListenerCreatedApplier(state));
    register(GlobalListenerIntent.UPDATED, new GlobalListenerUpdatedApplier(state));
    register(GlobalListenerIntent.DELETED, new GlobalListenerDeletedApplier(state));
  }

  private void registerClusterVariableEventAppliers(final MutableProcessingState state) {
    register(
        ClusterVariableIntent.CREATED,
        new ClusterVariableCreatedApplier(state.getClusterVariableState()));
    register(
        ClusterVariableIntent.UPDATED,
        new ClusterVariableUpdatedApplier(state.getClusterVariableState()));
    register(
        ClusterVariableIntent.DELETED,
        new ClusterVariableDeletedApplier(state.getClusterVariableState()));
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
    register(TimerIntent.CREATED, 2, new TimerCreatedV2Applier(state.getTimerState()));
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
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        3,
        new ProcessInstanceElementActivatingV3Applier(
            elementInstanceState, processState, eventScopeInstanceState));
    register(
        ProcessInstanceIntent.ELEMENT_ACTIVATED,
        new ProcessInstanceElementActivatedApplier(elementInstanceState));
    register(
        ProcessInstanceIntent.ELEMENT_COMPLETING,
        new ProcessInstanceElementCompletingApplier(elementInstanceState));
    register(
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        1,
        new ProcessInstanceElementCompletedV1Applier(
            elementInstanceState,
            eventScopeInstanceState,
            variableState,
            processState,
            multiInstanceState,
            bufferedStartMessageEventStateApplier));
    register(
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        2,
        new ProcessInstanceElementCompletedV2Applier(
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
        1,
        new ProcessInstanceElementTerminatedV1Applier(
            elementInstanceState,
            eventScopeInstanceState,
            multiInstanceState,
            bufferedStartMessageEventStateApplier));
    register(
        ProcessInstanceIntent.ELEMENT_TERMINATED,
        2,
        new ProcessInstanceElementTerminatedV2Applier(
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
        ProcessInstanceIntent.ELEMENT_MIGRATED,
        3,
        new ProcessInstanceElementMigratedV3Applier(
            elementInstanceState, processState, state.getMessageState()));
    register(
        ProcessInstanceIntent.ELEMENT_MIGRATED,
        4,
        new ProcessInstanceElementMigratedV4Applier(
            elementInstanceState, processState, state.getMessageState()));
    register(
        ProcessInstanceIntent.ELEMENT_MIGRATED,
        5,
        new ProcessInstanceElementMigratedV5Applier(
            elementInstanceState, processState, state.getMessageState()));
    register(
        ProcessInstanceIntent.ANCESTOR_MIGRATED,
        new ProcessInstanceAncestorMigratedApplier(elementInstanceState));
    register(
        RuntimeInstructionIntent.INTERRUPTED,
        new RuntimeInstructionInterruptedApplier(elementInstanceState));
    register(ProcessInstanceIntent.CANCELING, NOOP_EVENT_APPLIER);
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
    register(JobIntent.CANCELED, 1, new JobCanceledV1Applier(state));
    register(JobIntent.CANCELED, 2, new JobCanceledV2Applier(state));
    register(JobIntent.CANCELED, 3, new JobCanceledV3Applier(state));
    register(JobIntent.COMPLETED, 1, new JobCompletedV1Applier(state));
    register(JobIntent.COMPLETED, 2, new JobCompletedV2Applier(state));
    register(JobIntent.COMPLETED, 3, new JobCompletedV3Applier(state));
    register(JobIntent.CREATED, 1, new JobCreatedV1Applier(state));
    register(JobIntent.CREATED, 2, new JobCreatedV2Applier(state));
    register(JobIntent.CREATED, 3, new JobCreatedV3Applier(state));
    register(JobIntent.ERROR_THROWN, 1, new JobErrorThrownV1Applier(state));
    register(JobIntent.ERROR_THROWN, 2, new JobErrorThrownV2Applier(state));
    register(JobIntent.FAILED, 1, new JobFailedV1Applier(state));
    register(JobIntent.FAILED, 2, new JobFailedV2Applier(state));
    register(JobIntent.YIELDED, new JobYieldedApplier(state));
    register(JobIntent.RETRIES_UPDATED, new JobRetriesUpdatedApplier(state));
    register(JobIntent.TIMED_OUT, 1, new JobTimedOutV1Applier(state));
    register(JobIntent.TIMED_OUT, 2, new JobTimedOutV2Applier(state));
    register(JobIntent.RECURRED_AFTER_BACKOFF, new JobRecurredApplier(state));
    register(JobIntent.TIMEOUT_UPDATED, new JobTimeoutUpdatedApplier(state));
    register(JobIntent.UPDATED, 1, new JobUpdatedApplier(state));
    register(JobIntent.UPDATED, 2, NOOP_EVENT_APPLIER);
    register(JobIntent.MIGRATED, new JobMigratedApplier(state));
    register(JobIntent.PRIORITY_UPDATED, new JobPriorityUpdatedApplier(state));
  }

  private void registerMessageAppliers(final MutableProcessingState state) {
    register(MessageIntent.PUBLISHED, new MessagePublishedApplier(state.getMessageState()));
    register(MessageIntent.EXPIRED, 1, new MessageExpiredApplier(state.getMessageState()));
    register(
        MessageIntent.EXPIRED,
        2,
        new MessageExpiredV2Applier(
            state.getMessageState(), state.getMessageStartProcessInstanceAskState()));
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

  private void registerConditionalEvaluationAppliers() {
    register(ConditionalEvaluationIntent.EVALUATED, NOOP_EVENT_APPLIER);
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

  /**
   * Appliers for the cross-partition {@code MessageStartProcessInstanceRequest} handshake. All
   * intents are introduced together with this feature and have no prior stream history, so per the
   * versioned-applier convention a single V1 applier per intent is sufficient (no V2 alongside).
   *
   * <ul>
   *   <li>{@code REQUESTED}: acknowledgement event with no state effect.
   *   <li>{@code STARTED}: applied on {@code P_B} on cache miss + success; records the {@code
   *       (processDefinitionKey, messageKey) → processInstanceKey} dedup entry that lets retries
   *       from {@code P_K} be re-replied without a second activation. Also applied on {@code P_K}
   *       when the success reply is processed; removes the pending-ask entry so the scheduler stops
   *       retrying.
   *   <li>{@code UNIQUENESS_REJECTED}: applied on {@code P_K} when the rejection reply is
   *       processed; removes the pending-ask entry.
   *   <li>{@code NO_SUBSCRIPTION_REJECTED}: applied on {@code P_K} when the rejection reply is
   *       processed; removes the pending-ask entry.
   *   <li>{@code EXPIRED_DEDUP_DELETED}: removes a dedup entry after its post-completion expired
   *       dedup entry window has passed; emitted by the scheduled sweep.
   * </ul>
   */
  private void registerMessageStartProcessInstanceRequestAppliers(
      final MutableProcessingState state) {
    register(
        MessageStartProcessInstanceRequestIntent.REQUESTED,
        new MessageStartProcessInstanceRequestedV1Applier(
            state.getPartitionId(), state.getMessageStartProcessInstanceAskState()));
    register(
        MessageStartProcessInstanceRequestIntent.STARTED,
        new MessageStartProcessInstanceStartedV1Applier(
            state.getMessageStartProcessInstanceDedupState(),
            state.getMessageStartProcessInstanceAskState(),
            state.getMessageState()));
    register(
        MessageStartProcessInstanceRequestIntent.EXPIRED_DEDUP_DELETED,
        new MessageStartProcessInstanceExpiredDedupDeletedV1Applier(
            state.getMessageStartProcessInstanceDedupState()));
    register(
        MessageStartProcessInstanceRequestIntent.UNIQUENESS_REJECTED,
        new MessageStartProcessInstanceUniquenessRejectedV1Applier(
            state.getMessageStartProcessInstanceAskState()));
    register(
        MessageStartProcessInstanceRequestIntent.NO_SUBSCRIPTION_REJECTED,
        new MessageStartProcessInstanceNoSubscriptionRejectedV1Applier(
            state.getMessageStartProcessInstanceAskState()));
  }

  /**
   * Appliers for the pull-based correlation-key lock release lookup. Both event intents are
   * introduced together with this feature and have no prior stream history, so a single V1 applier
   * per intent is sufficient.
   *
   * <ul>
   *   <li>{@code QUERIED}: acknowledgement event on {@code P_B} with no state effect.
   *   <li>{@code RELEASED}: applied on {@code P_K} for each holder reported gone; removes the
   *       active process-instance lock and the cross-partition lock marker for that correlation
   *       key. The buffered-message pick-up is not done here but in the RELEASE command processor.
   * </ul>
   */
  private void registerMessageStartCorrelationKeyLockReleaseAppliers(
      final MutableProcessingState state) {
    register(MessageStartCorrelationKeyLockReleaseIntent.QUERIED, NOOP_EVENT_APPLIER);
    register(
        MessageStartCorrelationKeyLockReleaseIntent.RELEASED,
        new MessageStartCorrelationKeyLockReleaseReleasedV1Applier(state.getMessageState()));
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
    register(
        IncidentIntent.RESOLVED,
        3,
        new IncidentResolvedV3Applier(
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
        1,
        new ProcessEventTriggeringApplier(
            state.getEventScopeInstanceState(),
            state.getElementInstanceState(),
            state.getProcessState()));
    register(
        ProcessEventIntent.TRIGGERING,
        2,
        new ProcessEventTriggeringV2Applier(
            state.getEventScopeInstanceState(),
            state.getElementInstanceState(),
            state.getProcessState(),
            state.getUsageMetricState()));
    register(
        ProcessEventIntent.TRIGGERING,
        3,
        new ProcessEventTriggeringV3Applier(
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
    register(ResourceIntent.REEXPORTED, NOOP_EVENT_APPLIER);
    register(
        ResourceReexportIntent.STARTED,
        new ResourceReexportStartedApplier(state.getResourceState()));
    register(ResourceReexportIntent.FINISHED, NOOP_EVENT_APPLIER);
  }

  private void registerUserTaskAppliers(final MutableProcessingState state) {
    register(UserTaskIntent.CREATING, new UserTaskCreatingApplier(state));
    register(UserTaskIntent.CREATING, 2, new UserTaskCreatingV2Applier(state));
    register(UserTaskIntent.CREATING, 3, new UserTaskCreatingV3Applier(state));
    register(UserTaskIntent.CREATED, new UserTaskCreatedApplier(state));
    register(UserTaskIntent.CREATED, 2, new UserTaskCreatedV2Applier(state));
    register(UserTaskIntent.CREATED, 3, new UserTaskCreatedV3Applier(state));
    register(UserTaskIntent.CANCELING, 1, new UserTaskCancelingV1Applier(state));
    register(UserTaskIntent.CANCELING, 2, new UserTaskCancelingV2Applier(state));
    register(UserTaskIntent.CANCELING, 3, new UserTaskCancelingV3Applier(state));
    register(UserTaskIntent.CANCELING, 4, new UserTaskCancelingV4Applier(state));
    register(UserTaskIntent.CANCELED, new UserTaskCanceledApplier(state));
    register(UserTaskIntent.CANCELED, 2, new UserTaskCanceledV2Applier(state));
    register(UserTaskIntent.COMPLETING, 1, new UserTaskCompletingV1Applier(state));
    register(UserTaskIntent.COMPLETING, 2, new UserTaskCompletingV2Applier(state));
    register(UserTaskIntent.COMPLETING, 3, new UserTaskCompletingV3Applier(state));
    register(UserTaskIntent.COMPLETED, 1, new UserTaskCompletedV1Applier(state));
    register(UserTaskIntent.COMPLETED, 2, new UserTaskCompletedV2Applier(state));
    register(UserTaskIntent.COMPLETED, 3, new UserTaskCompletedV3Applier(state));
    register(UserTaskIntent.ASSIGNING, 1, new UserTaskAssigningV1Applier(state));
    register(UserTaskIntent.ASSIGNING, 2, new UserTaskAssigningV2Applier(state));
    register(UserTaskIntent.ASSIGNING, 3, new UserTaskAssigningV3Applier(state));
    register(UserTaskIntent.ASSIGNED, 1, new UserTaskAssignedV1Applier(state));
    register(UserTaskIntent.ASSIGNED, 2, new UserTaskAssignedV2Applier(state));
    register(UserTaskIntent.ASSIGNED, 3, new UserTaskAssignedV3Applier(state));
    register(UserTaskIntent.ASSIGNED, 4, new UserTaskAssignedV4Applier(state));
    register(UserTaskIntent.CLAIMING, 1, new UserTaskClaimingV1Applier(state));
    register(UserTaskIntent.CLAIMING, 2, new UserTaskClaimingV2Applier(state));
    register(UserTaskIntent.UPDATING, 1, new UserTaskUpdatingV1Applier(state));
    register(UserTaskIntent.UPDATING, 2, new UserTaskUpdatingV2Applier(state));
    register(UserTaskIntent.UPDATING, 3, new UserTaskUpdatingV3Applier(state));
    register(UserTaskIntent.UPDATED, 1, new UserTaskUpdatedV1Applier(state));
    register(UserTaskIntent.UPDATED, 2, new UserTaskUpdatedV2Applier(state));
    register(UserTaskIntent.UPDATED, 3, new UserTaskUpdatedV3Applier(state));
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
        1,
        new CommandDistributionDistributingApplier(distributionState));
    register(
        CommandDistributionIntent.DISTRIBUTING,
        2,
        new CommandDistributionDistributingApplierV2(distributionState));
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

  private void registerClusterVersionAppliers(final MutableProcessingState state) {
    // The gate lives on the applier class — each TypedEventApplier overrides gatedBy() to declare
    // its ECV classification, and register(...) cross-checks against ClusterVersionCatalog at
    // boot. To gate a new applier version, add the catalog entry and have the applier return the
    // matching Capability from gatedBy().
    register(
        ClusterVersionIntent.APPLIED,
        1,
        new ClusterVersionAppliedV1Applier(state.getClusterVersionState()));
    register(
        ClusterVersionIntent.APPLIED,
        2,
        new ClusterVersionAppliedV2Applier(state.getClusterVersionState()));
    register(
        ClusterVersionIntent.APPLIED,
        3,
        new ClusterVersionAppliedV3Applier(state.getClusterVersionState()));
    // PINGED and ECHOED do not mutate ECV state — the value of these events is their visible
    // appearance on the log, not a state change.
    register(ClusterVersionIntent.PINGED, NOOP_EVENT_APPLIER);
    register(ClusterVersionIntent.ECHOED, NOOP_EVENT_APPLIER);
    // Suppress/unsuppress mutate the per-cluster suppressed-flags set — the rollback-lite
    // override on top of the ECV gate.
    register(
        ClusterVersionIntent.FLAG_SUPPRESSED,
        new FlagSuppressedApplier(state.getClusterVersionState()));
    register(
        ClusterVersionIntent.FLAG_UNSUPPRESSED,
        new FlagUnsuppressedApplier(state.getClusterVersionState()));
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
    register(ScaleIntent.SCALED_UP, NOOP_EVENT_APPLIER);
    register(ScaleIntent.STATUS_RESPONSE, NOOP_EVENT_APPLIER);
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
        1,
        new BatchOperationCreatedV1Applier(state.getBatchOperationState()));
    register(
        BatchOperationIntent.CREATED,
        2,
        new BatchOperationCreatedV2Applier(state.getBatchOperationState()));
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
        1,
        new BatchOperationChunkCreatedV1Applier(state.getBatchOperationState()));
    register(
        BatchOperationChunkIntent.CREATED,
        2,
        new BatchOperationChunkCreatedV2Applier(state.getBatchOperationState()));
    register(
        BatchOperationExecutionIntent.EXECUTING,
        new BatchOperationExecutingApplier(state.getBatchOperationState()));
    register(BatchOperationExecutionIntent.EXECUTED, NOOP_EVENT_APPLIER);
    register(
        BatchOperationIntent.CANCELED,
        new BatchOperationCanceledApplier(state.getBatchOperationState()));
    register(
        BatchOperationIntent.SUSPENDED,
        new BatchOperationSuspendedV1Applier(state.getBatchOperationState()));
    register(
        BatchOperationIntent.SUSPENDED,
        2,
        new BatchOperationSuspendedV2Applier(state.getBatchOperationState()));
    register(
        BatchOperationIntent.RESUMED,
        new BatchOperationResumedV1Applier(state.getBatchOperationState()));
    register(
        BatchOperationIntent.RESUMED,
        2,
        new BatchOperationResumedV2Applier(state.getBatchOperationState()));
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

  private void registerHistoryDeletionAppliers() {
    register(HistoryDeletionIntent.DELETED, NOOP_EVENT_APPLIER);
  }

  private <I extends Intent> void register(final I intent, final TypedEventApplier<I, ?> applier) {
    register(intent, RecordMetadata.DEFAULT_RECORD_VERSION, applier);
  }

  /**
   * Register an applier version. The applier's {@link TypedEventApplier#gatedBy()} declares its ECV
   * classification; the catalog and a transitional legacy allowlist cross-check it:
   *
   * <ul>
   *   <li>{@code gatedBy() != BASELINE} → must be listed in {@link ClusterVersionCatalog} under
   *       that capability, or registration throws. This catches "I added a gated applier but forgot
   *       the catalog entry."
   *   <li>{@code gatedBy() == BASELINE} with {@code version == 1} → always accepted (the implicit
   *       pre-ECV case).
   *   <li>{@code gatedBy() == BASELINE} with {@code version > 1} → must be in {@link
   *       #LEGACY_BASELINE_APPLIERS}. New {@code v > 1} appliers cannot accept the BASELINE default
   *       silently — the developer has to either override {@code gatedBy()} to name a capability or
   *       add the pair to the allowlist (visible in PR review).
   * </ul>
   */
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

    // Coerce null → BASELINE. Real appliers inherit a non-null default from TypedEventApplier;
    // null only happens for Mockito mocks that don't stub gatedBy(). The v > 1 baseline-allowlist
    // check below still fires either way, so this is safe.
    final Capability gatedByOrNull = applier.gatedBy();
    final Capability gatedBy = gatedByOrNull == null ? Capability.BASELINE : gatedByOrNull;
    final var id = new ApplierVersionId(intent, version);

    if (gatedBy == Capability.BASELINE) {
      // Only enforce on real (enum) intents — production Intent implementations are always enum
      // constants, mock intents (test proxies) are not. Mock-driven unit tests register synthetic
      // intents to exercise the selection mechanism; they cannot meaningfully appear in the
      // allowlist, and the production-safety guarantee is unaffected because they cannot reach
      // production.
      if (version > 1 && intent.getClass().isEnum() && !BASELINE_APPLIERS.contains(id)) {
        throw new IllegalStateException(
            "Applier "
                + applier.getClass().getSimpleName()
                + " for "
                + id
                + " inherits the default Capability.BASELINE classification. Any version > 1"
                + " must opt in explicitly: either override gatedBy() to return the gating"
                + " Capability (and add a matching catalog entry), or add this (intent, version)"
                + " to EventAppliers.BASELINE_APPLIERS with a justification. The same applies"
                + " when retiring a previously ECV-gated applier — empty its catalog entry,"
                + " switch gatedBy() back to BASELINE, and add it here.");
      }
    } else {
      // ECV-gated: cross-check the catalog actually lists this pair under the named capability.
      ClusterVersionCatalog.assertGatedBy(intent, version, gatedBy);
    }

    final var previousApplier =
        mapping.computeIfAbsent(intent, unused -> new HashMap<>()).putIfAbsent(version, applier);
    if (previousApplier != null) {
      throw new IllegalArgumentException(
          String.format(
              "Applier for intent '%s' and version '%d' is already registered", intent, version));
    }
    if (gatedBy != Capability.BASELINE) {
      requirements.computeIfAbsent(intent, unused -> new HashMap<>()).put(version, gatedBy.at());
    }
    latestVersionByIntent.merge(intent, version, Math::max);
  }

  /**
   * Wire the lookup of the cluster's currently active ECV ordinal. Called once by the engine after
   * processing state is available; subsequent {@link #selectVersionFor(Intent)} calls read from
   * this supplier.
   */
  public EventAppliers setActiveClusterVersionProvider(final IntSupplier activeOrdinalSupplier) {
    this.activeOrdinalSupplier = Objects.requireNonNull(activeOrdinalSupplier);
    return this;
  }

  @Override
  public int getLatestVersion(final Intent intent) {
    return latestVersionByIntent.getOrDefault(intent, -1);
  }

  @Override
  public int selectVersionFor(final Intent intent) {
    final var perVersion = requirements.get(intent);
    // Fast path: no gated versions for this intent — the vast majority of intents take this
    // branch. No ECV read, no loop, no allocation.
    if (perVersion == null) {
      return getLatestVersion(intent);
    }
    // Gated path: walk the small set of registered versions, picking the highest whose
    // requirement is met by the current active ECV.
    final var versions = mapping.get(intent);
    if (versions == null) {
      return -1;
    }
    final int activeOrdinal = activeOrdinalSupplier.getAsInt();
    int selected = -1;
    for (final Integer version : versions.keySet()) {
      final Integer required = perVersion.get(version);
      // Ordinal-only check — backports keep the same ordinal across lines, so the active line
      // doesn't gate the requirement.
      if (required == null || activeOrdinal >= required) {
        if (version > selected) {
          selected = version;
        }
      }
    }
    return selected;
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
