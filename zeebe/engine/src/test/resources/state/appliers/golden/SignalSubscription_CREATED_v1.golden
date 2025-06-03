/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableSignalSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;

public final class SignalSubscriptionCreatedApplier
    implements TypedEventApplier<SignalSubscriptionIntent, SignalSubscriptionRecord> {

  private final MutableSignalSubscriptionState subscriptionState;

  public SignalSubscriptionCreatedApplier(final MutableSignalSubscriptionState subscriptionState) {
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void applyState(final long key, final SignalSubscriptionRecord value) {
    subscriptionState.put(key, value);
  }
}
