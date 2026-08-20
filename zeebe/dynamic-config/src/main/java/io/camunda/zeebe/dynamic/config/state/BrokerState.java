/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents the cluster-wide lifecycle state of a single broker.
 *
 * <p>Unlike the legacy {@code MemberState}, this record carries <em>only</em> broker lifecycle
 * state and no partition assignment: which partitions a broker replicates within a group lives in
 * {@code BrokerPartitionState}, and per-broker recovery lives there too (as its {@code Mode}).
 * Accordingly the lifecycle {@link State} deliberately omits {@code RECOVERING} — recovery is
 * per-group, not a cluster-wide broker state.
 *
 * <p>{@code version} is incremented every time the state is updated. It is used to resolve
 * conflicts when members receive gossip updates out of order. Only a member updates its own state,
 * which prevents conflicting concurrent updates. To keep this invariant self-contained, the state
 * is only changed through {@link #setState(State)}, which returns a new instance with an
 * incremented version — callers never manage the version themselves.
 *
 * @param version version of this broker's lifecycle state
 * @param lastUpdated time this state was last updated
 * @param state the lifecycle state of the broker
 */
@NullMarked
public record BrokerState(long version, Instant lastUpdated, State state) {

  public BrokerState {
    Objects.requireNonNull(lastUpdated, "lastUpdated must not be null");
    Objects.requireNonNull(state, "state must not be null");
  }

  /** Creates an initial state at version {@code 0} in {@link State#ACTIVE}. */
  public static BrokerState initializeAsActive() {
    return new BrokerState(0, Instant.MIN, State.ACTIVE);
  }

  /** Creates an initial state at version {@code 0} in {@link State#UNINITIALIZED}. */
  public static BrokerState uninitialized() {
    return new BrokerState(0, Instant.MIN, State.UNINITIALIZED);
  }

  /**
   * Returns a new state with the given lifecycle state and an incremented version, or {@code this}
   * if the state is unchanged.
   */
  public BrokerState setState(final State state) {
    if (this.state == state) {
      return this;
    }
    return new BrokerState(version + 1, Instant.now(), state);
  }

  /**
   * Returns a new {@link BrokerState} after merging this and {@code other}. Does not mutate either
   * operand.
   *
   * <p>The state is always updated by a member for itself, so the highest version is guaranteed to
   * be the latest state and wins. Two states at the same version must be identical; otherwise the
   * inputs are in conflict and cannot be reconciled.
   *
   * @param other the state to merge with, may be {@code null}
   * @return the merged state
   */
  BrokerState merge(final @Nullable BrokerState other) {
    if (other == null) {
      return this;
    }

    if (version == other.version && !equals(other)) {
      throw new IllegalStateException(
          String.format(
              "Expected to find same BrokerState at same version, but found %s and %s",
              this, other));
    }

    return version >= other.version ? this : other;
  }

  /**
   * The lifecycle state of a broker within the cluster. Recovery is tracked per group, not here.
   */
  public enum State {
    UNINITIALIZED,
    JOINING,
    ACTIVE,
    LEAVING,
    LEFT
  }
}
