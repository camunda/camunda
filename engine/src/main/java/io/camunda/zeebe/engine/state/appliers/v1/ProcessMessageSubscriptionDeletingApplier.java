/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers.v1;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;

public final class ProcessMessageSubscriptionDeletingApplier
    implements TypedEventApplier<
        ProcessMessageSubscriptionIntent, ProcessMessageSubscriptionRecord> {
  private final MutableProcessMessageSubscriptionState state;

  public ProcessMessageSubscriptionDeletingApplier(
      final MutableProcessMessageSubscriptionState state) {
    this.state = state;
  }

  @Override
  public void applyState(final long key, final ProcessMessageSubscriptionRecord value) {
    state.updateToClosingState(value);
  }
}
