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
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import org.jspecify.annotations.NullMarked;

/** Marks the Hub event of the update as applying, from the first applied chunk on. */
@NullMarked
public final class PolicyUpdateChunkAppliedApplier
    implements TypedEventApplier<PolicyUpdateIntent, PolicyUpdateRecord> {

  private final MutableHubEventState hubEventState;

  public PolicyUpdateChunkAppliedApplier(final MutableHubEventState hubEventState) {
    this.hubEventState = hubEventState;
  }

  @Override
  public void applyState(final long key, final PolicyUpdateRecord value) {
    hubEventState.setStatus(
        value.getSeqNo(), HubEventType.POLICY_UPDATE, HubEventStatus.APPLYING, null);
  }
}
