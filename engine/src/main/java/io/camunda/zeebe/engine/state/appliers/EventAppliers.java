/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.EventApplier.NoSuchEventApplier.NoApplierForIntent;
import io.camunda.zeebe.engine.state.EventApplier.NoSuchEventApplier.NoApplierForVersion;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.protocol.record.intent.EscalationIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceResultIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
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
    register(ProcessInstanceResultIntent.COMPLETED, NOOP_EVENT_APPLIER);
    register(ProcessInstanceBatchIntent.ACTIVATED, NOOP_EVENT_APPLIER);
    register(ProcessInstanceBatchIntent.TERMINATED, NOOP_EVENT_APPLIER);

    registerProcessAppliers(state);
    register(ErrorIntent.CREATED, new ErrorCreatedApplier(state.getBannedInstanceState()));
    registerDeploymentAppliers(state);

    registerMessageAppliers(state);
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
    registerDecisionEvaluationAppliers();

    registerFormAppliers(state);

    registerSignalAppliers(state);

    registerCommandDistributionAppliers(state);
    registerEscalationAppliers();
    registerResourceDeletionAppliers();
    return this;
  }

  private void registerProcessAppliers(final MutableProcessingState state) {
    register(ProcessIntent.CREATED, new ProcessCreatedApplier(state));
    register(ProcessIntent.DELETING, new ProcessDeletingApplier(state));
    register(ProcessIntent.DELETED, new ProcessDeletedApplier(state));
  }

  private void registerTimeEventAppliers(final MutableProcessingState state) {
    register(TimerIntent.CREATED, new TimerCreatedApplier(state.getTimerState()));
    register(TimerIntent.CANCELED, new TimerCancelledApplier(state.getTimerState()));
    register(TimerIntent.TRIGGERED, new TimerTriggeredApplier(state.getTimerState()));
  }

  private void registerDeploymentAppliers(final MutableProcessingState state) {
    register(DeploymentDistributionIntent.DISTRIBUTING, new DeploymentDistributionApplier(state));
    register(
        DeploymentDistributionIntent.COMPLETED,
        new DeploymentDistributionCompletedApplier(state.getDeploymentState()));

    register(DeploymentIntent.CREATED, 1, new DeploymentCreatedApplier(state.getDeploymentState()));
    register(DeploymentIntent.CREATED, 2, NOOP_EVENT_APPLIER);
    register(
        DeploymentIntent.DISTRIBUTED,
        new DeploymentDistributedApplier(state.getProcessState(), state.getDecisionState()));
    register(
        DeploymentIntent.FULLY_DISTRIBUTED,
        new DeploymentFullyDistributedApplier(state.getDeploymentState()));
  }

  private void registerVariableEventAppliers(final MutableProcessingState state) {
    final VariableApplier variableApplier = new VariableApplier(state.getVariableState());
    register(VariableIntent.CREATED, variableApplier);
    register(VariableIntent.UPDATED, variableApplier);
    register(VariableDocumentIntent.UPDATED, NOOP_EVENT_APPLIER);
  }

  private void registerProcessInstanceEventAppliers(final MutableProcessingState state) {
    final var elementInstanceState = state.getElementInstanceState();
    final var eventScopeInstanceState = state.getEventScopeInstanceState();
    final var processState = state.getProcessState();
    final var variableState = state.getVariableState();
    final var bufferedStartMessageEventStateApplier =
        new BufferedStartMessageEventStateApplier(processState, state.getMessageState());

    register(
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        new ProcessInstanceElementActivatingApplier(
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
            bufferedStartMessageEventStateApplier));
    register(
        ProcessInstanceIntent.ELEMENT_TERMINATING,
        new ProcessInstanceElementTerminatingApplier(elementInstanceState));
    register(
        ProcessInstanceIntent.ELEMENT_TERMINATED,
        new ProcessInstanceElementTerminatedApplier(
            elementInstanceState, eventScopeInstanceState, bufferedStartMessageEventStateApplier));
    register(
        ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN,
        new ProcessInstanceSequenceFlowTakenApplier(elementInstanceState, processState));
  }

  private void registerProcessInstanceCreationAppliers(final MutableProcessingState state) {
    final var processState = state.getProcessState();
    final var elementInstanceState = state.getElementInstanceState();

    register(
        ProcessInstanceCreationIntent.CREATED,
        new ProcessInstanceCreationCreatedApplier(processState, elementInstanceState));
  }

  private void registerProcessInstanceModificationAppliers(final MutableProcessingState state) {
    register(
        ProcessInstanceModificationIntent.MODIFIED,
        new ProcessInstanceModifiedEventApplier(
            state.getElementInstanceState(), state.getProcessState()));
  }

  private void registerJobIntentEventAppliers(final MutableProcessingState state) {
    register(JobIntent.CANCELED, new JobCanceledApplier(state));
    register(JobIntent.COMPLETED, new JobCompletedApplier(state));
    register(JobIntent.CREATED, new JobCreatedApplier(state));
    register(JobIntent.ERROR_THROWN, new JobErrorThrownApplier(state));
    register(JobIntent.FAILED, new JobFailedApplier(state));
    register(JobIntent.YIELDED, new JobYieldedApplier(state));
    register(JobIntent.RETRIES_UPDATED, new JobRetriesUpdatedApplier(state));
    register(JobIntent.TIMED_OUT, new JobTimedOutApplier(state));
    register(JobIntent.RECURRED_AFTER_BACKOFF, new JobRecurredApplier(state));
  }

  private void registerMessageAppliers(final MutableProcessingState state) {
    register(MessageIntent.PUBLISHED, new MessagePublishedApplier(state.getMessageState()));
    register(MessageIntent.EXPIRED, new MessageExpiredApplier(state.getMessageState()));
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
        new IncidentResolvedV2Applier(state.getIncidentState(), state.getJobState()));
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
    register(SignalIntent.BROADCASTED, NOOP_EVENT_APPLIER);
  }

  private void registerDecisionAppliers(final MutableProcessingState state) {
    register(DecisionIntent.CREATED, new DecisionCreatedApplier(state.getDecisionState()));
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

  private void registerDecisionEvaluationAppliers() {
    register(DecisionEvaluationIntent.EVALUATED, NOOP_EVENT_APPLIER);
    register(DecisionEvaluationIntent.FAILED, NOOP_EVENT_APPLIER);
  }

  private void registerFormAppliers(final MutableProcessingState state) {
    register(FormIntent.CREATED, new FormCreatedApplier(state));
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
  }

  private void registerEscalationAppliers() {
    register(EscalationIntent.ESCALATED, NOOP_EVENT_APPLIER);
    register(EscalationIntent.NOT_ESCALATED, NOOP_EVENT_APPLIER);
  }

  private void registerResourceDeletionAppliers() {
    register(ResourceDeletionIntent.DELETING, NOOP_EVENT_APPLIER);
    register(ResourceDeletionIntent.DELETED, NOOP_EVENT_APPLIER);
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
}
