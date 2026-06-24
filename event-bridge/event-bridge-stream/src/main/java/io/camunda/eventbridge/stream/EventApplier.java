/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;

/**
 * Applies the state changes of a committed event, dispatching to the {@link TypedEventApplier}
 * registered for its intent. Mirrors the Zeebe engine's {@code EventApplier}: it is invoked both on
 * the leader (right after a follow-up event is written, via {@link StateWriter}) and on followers
 * (on replay), so it is the single state-mutation path across the cluster.
 */
public interface EventApplier {

  /**
   * Applies the state changes of the given event.
   *
   * @param key the key of the event
   * @param intent the intent of the event
   * @param value the value of the event
   * @throws NoSuchEventApplier if no applier is registered for the intent
   */
  void applyState(long key, Intent intent, RecordValue value);

  /** Thrown when no event applier is registered for a given intent. */
  final class NoSuchEventApplier extends RuntimeException {
    NoSuchEventApplier(final Intent intent) {
      super(
          "Expected to find an event applier for intent '%s', but none was registered."
              .formatted(intent));
    }
  }
}
