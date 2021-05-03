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
import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;

public final class ProcessMessageSubscriptionCorrelatedApplier
    implements TypedEventApplier<
        ProcessMessageSubscriptionIntent, ProcessMessageSubscriptionRecord> {

  private final MutableProcessMessageSubscriptionState subscriptionState;
  private final MutableVariableState variableState;

  public ProcessMessageSubscriptionCorrelatedApplier(
      final MutableProcessMessageSubscriptionState subscriptionState,
      final MutableVariableState variableState) {
    this.subscriptionState = subscriptionState;
    this.variableState = variableState;
  }

  @Override
  public void applyState(final long key, final ProcessMessageSubscriptionRecord value) {
    if (value.isInterrupting()) {
      subscriptionState.remove(value.getElementInstanceKey(), value.getMessageNameBuffer());
    }

    final var eventScopeKey = value.getElementInstanceKey();
    if (value.getVariablesBuffer().capacity() > 0) {
      variableState.setTemporaryVariables(eventScopeKey, value.getVariablesBuffer());
    }
  }
}
