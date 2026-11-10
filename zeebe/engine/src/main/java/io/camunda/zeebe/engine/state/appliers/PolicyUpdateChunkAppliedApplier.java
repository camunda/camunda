/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyUpdateState;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import org.jspecify.annotations.NullMarked;

/** Removes the update and all its buffered chunks once its last chunk is applied. */
@NullMarked
public final class PolicyUpdateChunkAppliedApplier
    implements TypedEventApplier<PolicyUpdateIntent, PolicyUpdateRecord> {

  private final MutablePolicyUpdateState policyUpdateState;

  public PolicyUpdateChunkAppliedApplier(final MutablePolicyUpdateState policyUpdateState) {
    this.policyUpdateState = policyUpdateState;
  }

  @Override
  public void applyState(final long key, final PolicyUpdateRecord value) {
    if (value.getChunkIndex() == value.getChunkCount() - 1) {
      policyUpdateState.removeUpdate(value.getUpdateId());
    }
  }
}
