/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.camunda.eventbridge.stream.TypedEventApplier;

/**
 * Applies {@code OFFSET_COMMITTED} events to the replicated {@link OffsetState} — the only place
 * that mutates offset state. It runs identically on the leader (right after {@link
 * OffsetCommitProcessor} writes the event) and on followers (on replay), so every replica converges
 * to the same offsets. Commits are monotonic, so applying an event is idempotent.
 */
final class OffsetCommittedApplier
    implements TypedEventApplier<CoordinatorIntent, OffsetCommitRecord> {

  private final OffsetState offsetState;

  OffsetCommittedApplier(final OffsetState offsetState) {
    this.offsetState = offsetState;
  }

  @Override
  public void applyState(final long key, final OffsetCommitRecord value) {
    offsetState.commit(value.getGroupId(), value.getPartitionId(), value.getOffset());
  }
}
