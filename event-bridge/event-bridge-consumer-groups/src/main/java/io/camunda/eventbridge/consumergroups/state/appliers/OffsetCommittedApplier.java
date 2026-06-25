/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.appliers;

import io.camunda.eventbridge.consumergroups.record.OffsetCommitRecord;
import io.camunda.eventbridge.consumergroups.state.mutable.MutableOffsetState;
import io.camunda.eventbridge.stream.TypedEventApplier;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;

/**
 * Applies {@code OFFSET_COMMITTED}: stores the committed position the {@code OffsetCommitProcessor}
 * already resolved (it applied the monotonic never-rewind rule and stamped the actual value on the
 * event). {@link MutableOffsetState} does the raw put. Runs identically on leader (after {@code
 * OffsetCommitProcessor}) and follower (on replay), so every replica converges; applying the same
 * event twice is idempotent.
 */
public final class OffsetCommittedApplier
    implements TypedEventApplier<CoordinatorIntent, OffsetCommitRecord> {

  private final MutableOffsetState offsetState;

  public OffsetCommittedApplier(final MutableOffsetState offsetState) {
    this.offsetState = offsetState;
  }

  @Override
  public void applyState(final long key, final OffsetCommitRecord value) {
    offsetState.putOffset(
        value.getGroupId(), value.getTopic(), value.getPartitionId(), value.getOffset());
  }
}
