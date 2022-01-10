/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableDmnDecisionState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;

public final class DecisionCreatedApplier
    implements TypedEventApplier<DecisionIntent, DecisionRecord> {

  private final MutableDmnDecisionState dmnDecisionState;

  public DecisionCreatedApplier(final MutableDmnDecisionState dmnDecisionState) {
    this.dmnDecisionState = dmnDecisionState;
  }

  @Override
  public void applyState(final long key, final DecisionRecord value) {
    dmnDecisionState.putDecision(value);
  }
}
