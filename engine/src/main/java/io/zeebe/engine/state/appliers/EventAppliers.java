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
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.MessageIntent;
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
              LOG.debug("No state changed: tried to use unimplemented event applier {}", intent);

  @SuppressWarnings("rawtypes")
  private final Map<Intent, TypedEventApplier> mapping = new HashMap<>();

  public EventAppliers(final ZeebeState state) {
    register(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        new WorkflowInstanceElementActivatedApplier(state));
    register(JobIntent.CREATED, new JobCreatedApplier(state));
    register(WorkflowIntent.CREATED, new WorkflowCreatedApplier(state));
    register(DeploymentDistributionIntent.DISTRIBUTING, new DeploymentDistributionApplier(state));

    register(MessageIntent.EXPIRED, new MessageExpiredApplier(state.getMessageState()));
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
