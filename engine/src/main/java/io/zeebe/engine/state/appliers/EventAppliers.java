/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.state.EventApplier;
import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.MessageIntent;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.intent.WorkflowIntent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * Applies state changes from events to the {@link io.zeebe.engine.state.ZeebeState}.
 *
 * <p>Finds the correct {@link TypedEventApplier} and delegates.
 */
public final class EventAppliers implements EventApplier {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  // todo (#6202): after migration this should log at WARN level
  private static final Function<Intent, TypedEventApplier<?, ?>> UNIMPLEMENTED_EVENT_APPLIER =
      intent ->
          (key, value) ->
              LOG.trace(
                  "No state changed: tried to use unimplemented event applier {}.{}",
                  intent.getClass().getSimpleName(),
                  intent);

  @SuppressWarnings("rawtypes")
  private final Map<Intent, TypedEventApplier> mapping = new HashMap<>();

  public EventAppliers(final ZeebeState state) {
    registerWorkflowInstanceEventAppliers(state);

    register(WorkflowIntent.CREATED, new WorkflowCreatedApplier(state));
    register(DeploymentDistributionIntent.DISTRIBUTING, new DeploymentDistributionApplier(state));
    register(
        DeploymentDistributionIntent.COMPLETED,
        new DeploymentDistributionCompletedApplier(state.getDeploymentState()));

    register(DeploymentIntent.CREATED, new DeploymentCreatedApplier(state.getDeploymentState()));
    register(
        DeploymentIntent.DISTRIBUTED, new DeploymentDistributedApplier(state.getWorkflowState()));
    register(
        DeploymentIntent.FULLY_DISTRIBUTED,
        new DeploymentFullyDistributedApplier(state.getDeploymentState()));

    register(MessageIntent.PUBLISHED, new MessagePublishedApplier(state.getMessageState()));
    register(MessageIntent.EXPIRED, new MessageExpiredApplier(state.getMessageState()));

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
        MessageStartEventSubscriptionIntent.CORRELATED,
        new MessageStartEventSubscriptionCorrelatedApplier(
            state.getMessageState(), state.getEventScopeInstanceState()));

    registerJobIntentEventAppliers(state);
  }

  private void registerWorkflowInstanceEventAppliers(final ZeebeState state) {
    final var elementInstanceState = state.getElementInstanceState();
    final var eventScopeInstanceState = state.getEventScopeInstanceState();
    register(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        new WorkflowInstanceElementActivatingApplier(elementInstanceState));
    register(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        new WorkflowInstanceElementActivatedApplier(elementInstanceState));
    register(
        WorkflowInstanceIntent.ELEMENT_COMPLETING,
        new WorkflowInstanceElementCompletingApplier(elementInstanceState));
    register(
        WorkflowInstanceIntent.ELEMENT_COMPLETED,
        new WorkflowInstanceElementCompletedApplier(elementInstanceState, eventScopeInstanceState));
    register(
        WorkflowInstanceIntent.ELEMENT_TERMINATING,
        new WorkflowInstanceElementTerminatingApplier(elementInstanceState));
    register(
        WorkflowInstanceIntent.ELEMENT_TERMINATED,
        new WorkflowInstanceElementTerminatedApplier(
            elementInstanceState, eventScopeInstanceState));
    register(
        WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
        new WorkflowInstanceSequenceFlowTakenApplier(elementInstanceState));
  }

  private void registerJobIntentEventAppliers(final ZeebeState state) {
    register(JobIntent.CREATED, new JobCreatedApplier(state));
    register(JobIntent.COMPLETED, new JobCompletedEventApplier(state));
    register(JobIntent.FAILED, new JobFailedApplier(state));
    register(JobIntent.ERROR_THROWN, new ErrorThrownEventApplier(state));
    register(JobIntent.TIMED_OUT, new JobTimedOutApplier(state));
  }

  private <I extends Intent> void register(final I intent, final TypedEventApplier<I, ?> applier) {
    mapping.put(intent, applier);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void applyState(final long key, final Intent intent, final RecordValue value) {
    final var eventApplier =
        mapping.getOrDefault(intent, UNIMPLEMENTED_EVENT_APPLIER.apply(intent));
    eventApplier.applyState(key, value);
  }
}
