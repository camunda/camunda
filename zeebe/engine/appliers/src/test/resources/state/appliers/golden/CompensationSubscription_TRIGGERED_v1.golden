/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableCompensationSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.CompensationSubscriptionIntent;

public class CompensationSubscriptionTriggeredApplier
    implements TypedEventApplier<CompensationSubscriptionIntent, CompensationSubscriptionRecord> {

  private final MutableCompensationSubscriptionState compensationState;

  public CompensationSubscriptionTriggeredApplier(
      final MutableCompensationSubscriptionState compensationState) {
    this.compensationState = compensationState;
  }

  @Override
  public void applyState(final long key, final CompensationSubscriptionRecord value) {
    compensationState.update(key, value);
  }
}
