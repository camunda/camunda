/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import java.time.Instant;
import java.util.Optional;

public record CompletedChange(long id, Status status, Instant startedAt, Instant completedAt) {

  /**
   * Merges two brokers' records of a sub-configuration's last completed change.
   *
   * <p>Needed because a graph change is completed without a coordinator: {@code
   * completeGraphChangeIfDrained()} runs on every broker, and each mints its own record from its
   * own view of when the last operation completed. Two brokers minting a moment apart disagree on
   * {@code completedAt} for the very same change at the very same sub-configuration version, and
   * every other equal-version field is resolved by keeping the receiver's copy — which would leave
   * that disagreement standing forever, with the cluster reporting two different completion times
   * for one change. See {@link DependencyChangePlan#toCompletedChange()}.
   *
   * <p>Resolved the way {@link DependencyChangePlan#merge} already resolves the analogous case for
   * individual operation completions: same change id, earliest {@code completedAt} wins; different
   * id — a genuinely later, unrelated change — the higher id wins, since ids are monotonic.
   */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public static Optional<CompletedChange> merge(
      final Optional<CompletedChange> mine, final Optional<CompletedChange> theirs) {
    if (mine.isEmpty()) {
      return theirs;
    }
    if (theirs.isEmpty()) {
      return mine;
    }
    final var mineChange = mine.orElseThrow();
    final var theirsChange = theirs.orElseThrow();
    if (mineChange.id() != theirsChange.id()) {
      return mineChange.id() > theirsChange.id() ? mine : theirs;
    }
    return mineChange.completedAt().isBefore(theirsChange.completedAt()) ? mine : theirs;
  }
}
