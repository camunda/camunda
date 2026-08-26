/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;

/**
 * Version 2 of the {@code CREATING} applier. Unlike v1 (which only inserts a new row), v2 also
 * handles the resume re-subscribe path, where a {@code CREATING} event is emitted for a row that
 * already exists in {@code OPENED} state (the durable resume manifest kept by the suspend path).
 *
 * <p>The behaviour is an upsert:
 *
 * <ul>
 *   <li>row does not exist → {@link MutableProcessMessageSubscriptionState#put} (first-time
 *       subscription, identical to v1)
 *   <li>row already exists → {@link MutableProcessMessageSubscriptionState#updateToOpeningState},
 *       which transitions {@code OPENED → OPENING} and enrolls the row in the pending-retry
 *       transient index so the open command is retried after a dropped send or leader failover
 * </ul>
 *
 * <p>v1 is retained unchanged and remains registered so that {@code CREATING} events written by
 * released versions (record version 1) replay byte-identically. Only new events written by this
 * version onward carry record version 2 and are applied here.
 */
public final class ProcessMessageSubscriptionCreatingV2Applier
    implements TypedEventApplier<
        ProcessMessageSubscriptionIntent, ProcessMessageSubscriptionRecord> {

  private final MutableProcessMessageSubscriptionState subscriptionState;

  public ProcessMessageSubscriptionCreatingV2Applier(
      final MutableProcessMessageSubscriptionState subscriptionState) {
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void applyState(final long key, final ProcessMessageSubscriptionRecord value) {
    final var existing =
        subscriptionState.getSubscription(
            value.getElementInstanceKey(), value.getMessageNameBuffer(), value.getTenantId());
    if (existing != null) {
      subscriptionState.updateToOpeningState(value);
    } else {
      subscriptionState.put(key, value);
    }
  }
}
