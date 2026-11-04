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
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PolicyUpdateVersionSwitchedApplier
    implements TypedEventApplier<PolicyUpdateIntent, PolicyUpdateRecord> {

  private final MutablePolicyState policyState;

  public PolicyUpdateVersionSwitchedApplier(final MutablePolicyState policyState) {
    this.policyState = policyState;
  }

  @Override
  public void applyState(final long key, final PolicyUpdateRecord value) {
    policyState.commit(value.getPolicyId(), value.getPolicyVersion(), value.getSeqNo());
  }
}
