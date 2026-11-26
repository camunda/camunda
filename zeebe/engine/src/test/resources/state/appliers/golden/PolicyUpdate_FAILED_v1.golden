/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.HubEventState.HubEventStatus;
import io.camunda.zeebe.engine.state.immutable.HubEventState.HubEventType;
import io.camunda.zeebe.engine.state.mutable.MutableHubEventState;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyUpdateState;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import org.jspecify.annotations.NullMarked;

/**
 * Removes the failed update and all its buffered chunks. Only one update is open at a time, so this
 * clears the whole buffer.
 */
@NullMarked
public final class PolicyUpdateFailedApplier
    implements TypedEventApplier<PolicyUpdateIntent, PolicyUpdateRecord> {

  private final MutablePolicyUpdateState policyUpdateState;
  private final MutableHubEventState hubEventState;

  public PolicyUpdateFailedApplier(
      final MutablePolicyUpdateState policyUpdateState, final MutableHubEventState hubEventState) {
    this.policyUpdateState = policyUpdateState;
    this.hubEventState = hubEventState;
  }

  @Override
  public void applyState(final long key, final PolicyUpdateRecord value) {
    policyUpdateState.removeAll();
    hubEventState.setStatus(
        value.getSeqNo(),
        HubEventType.POLICY_UPDATE,
        HubEventStatus.REJECTED,
        value.getErrorMessage());
  }
}
