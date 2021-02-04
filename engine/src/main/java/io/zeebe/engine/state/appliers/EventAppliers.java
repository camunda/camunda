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
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * Applies state changes from events to the {@link io.zeebe.engine.state.ZeebeState}.
 *
 * <p>Finds the correct {@link EventApplier} and delegates.
 */
public final class EventAppliers {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  // todo (#6202): after migration this should log at WARN level
  private static final Function<Intent, EventApplier<?, ?>> UNIMPLEMENTED_EVENT_APPLIER =
      intent ->
          (key, value) ->
              LOG.debug("No state changed: tried to use unimplemented event applier {}", intent);

  @SuppressWarnings("rawtypes")
  private final Map<Intent, EventApplier> mapping = new HashMap<>();

  public EventAppliers(final ZeebeState state) {
    register(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        new WorkflowInstanceElementActivatedApplier(state));
  }

  @SuppressWarnings("java:S2326")
  private <I extends Intent> void register(final I intent, final EventApplier<I, ?> applier) {
    mapping.put(intent, applier);
  }

  @SuppressWarnings("unchecked")
  public void applyState(final long key, final Intent intent, final UnpackedObject value) {
    final var eventApplier =
        mapping.getOrDefault(intent, UNIMPLEMENTED_EVENT_APPLIER.apply(intent));
    eventApplier.applyState(key, value);
  }
}
