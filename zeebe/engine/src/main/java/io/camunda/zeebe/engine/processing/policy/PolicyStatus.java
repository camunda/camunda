/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.engine.state.immutable.PolicyState.CommittedPolicy;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The policy state of partition 1 that the engine publishes with the {@link PolicyStateNotifier}.
 *
 * @param committed the committed policy, or {@code null} if no policy update was committed
 * @param updateStatus the status of the open update
 * @param openUpdateId the id of the open update, or {@code null} if no update is open
 */
@NullMarked
public record PolicyStatus(
    @Nullable CommittedPolicy committed, UpdateStatus updateStatus, @Nullable String openUpdateId) {

  /** Whether an update is open, so the next one must not be sent yet. */
  public boolean hasOpenUpdate() {
    return updateStatus != UpdateStatus.NONE;
  }

  /** The Hub sequence of the committed policy, or 0 if no policy update was committed. */
  public long committedSeqNo() {
    return committed == null ? 0L : committed.seqNo();
  }

  /** The status of the update that is open on partition 1. */
  public enum UpdateStatus {
    /** No update is open: the last one was committed or failed, or there was none. */
    NONE,
    /** Some chunks of the update are buffered, but not all of them. */
    PENDING,
    /** All chunks of the update are buffered, and the update is applied but not committed yet. */
    APPLYING
  }
}
