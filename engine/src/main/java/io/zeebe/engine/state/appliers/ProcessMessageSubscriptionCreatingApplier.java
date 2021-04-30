/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.zeebe.util.sched.clock.ActorClock;

public final class ProcessMessageSubscriptionCreatingApplier
    implements TypedEventApplier<
        ProcessMessageSubscriptionIntent, ProcessMessageSubscriptionRecord> {

  private final MutableProcessMessageSubscriptionState subscriptionState;

  public ProcessMessageSubscriptionCreatingApplier(
      final MutableProcessMessageSubscriptionState subscriptionState) {
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void applyState(final long key, final ProcessMessageSubscriptionRecord value) {
    // TODO (saig0): the send time for the retry should be deterministic (#6364)
    final var sentTime = ActorClock.currentTimeMillis();

    if (subscriptionState.existSubscriptionForElementInstance(
        value.getElementInstanceKey(), value.getMessageNameBuffer())) {
      // TODO (saig0): avoid state change on reprocessing of a not yet migrated processor (#6200)
      return;
    }

    subscriptionState.put(key, value, sentTime);
  }
}
