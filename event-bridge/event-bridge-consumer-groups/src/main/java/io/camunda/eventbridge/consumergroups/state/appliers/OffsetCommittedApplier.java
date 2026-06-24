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
 * Applies {@code OFFSET_COMMITTED}: commits the position monotonically (never moves backwards). The
 * never-rewind decision lives here; {@link MutableOffsetState} only does a raw put. Runs
 * identically on leader (after {@code OffsetCommitProcessor}) and follower (on replay), so every
 * replica converges; applying the same event twice is idempotent.
 */
public final class OffsetCommittedApplier
    implements TypedEventApplier<CoordinatorIntent, OffsetCommitRecord> {

  private final MutableOffsetState offsetState;

  public OffsetCommittedApplier(final MutableOffsetState offsetState) {
    this.offsetState = offsetState;
  }

  @Override
  public void applyState(final long key, final OffsetCommitRecord value) {
    final var existing = offsetState.getOffset(value.getGroupId(), value.getPartitionId());
    final var committed = existing < 0 ? value.getOffset() : Math.max(existing, value.getOffset());
    offsetState.putOffset(value.getGroupId(), value.getPartitionId(), committed);
  }
}
