/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyState;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyUpdateState;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import org.jspecify.annotations.NullMarked;

/**
 * Commits the policy version, which completes the update: the buffer then holds nothing that is
 * still needed, so all updates and their chunks are removed. Only partition 1 buffers updates, so
 * this removes nothing on the other partitions.
 */
@NullMarked
public final class PolicyUpdateVersionCommittedApplier
    implements TypedEventApplier<PolicyUpdateIntent, PolicyUpdateRecord> {

  private final MutablePolicyState policyState;
  private final MutablePolicyUpdateState policyUpdateState;

  public PolicyUpdateVersionCommittedApplier(
      final MutablePolicyState policyState, final MutablePolicyUpdateState policyUpdateState) {
    this.policyState = policyState;
    this.policyUpdateState = policyUpdateState;
  }

  @Override
  public void applyState(final long key, final PolicyUpdateRecord value) {
    policyState.commit(value.getPolicyId(), value.getPolicyVersion(), value.getSeqNo());
    policyUpdateState.removeAll();
  }
}
