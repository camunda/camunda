/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import java.util.function.UnaryOperator;

public record PartitionState(State state, int priority, DynamicPartitionConfig config) {
  public static PartitionState active(
      final int priority, final DynamicPartitionConfig partitionConfig) {
    return new PartitionState(State.ACTIVE, priority, partitionConfig);
  }

  public static PartitionState joining(
      final int priority, final DynamicPartitionConfig partitionConfig) {
    return new PartitionState(State.JOINING, priority, partitionConfig);
  }

  public static PartitionState bootstrapping(
      final int priority, final DynamicPartitionConfig partitionConfig) {
    return new PartitionState(State.BOOTSTRAPPING, priority, partitionConfig);
  }

  public PartitionState toActive() {
    if (state == State.LEAVING) {
      throw new IllegalStateException(
          String.format("Cannot transition to ACTIVE when current state is %s", state));
    }
    return new PartitionState(State.ACTIVE, priority, config);
  }

  public PartitionState toLearner() {
    if (state != State.JOINING && state != State.LEARNER) {
      throw new IllegalStateException(
          String.format("Cannot transition to LEARNER when current state is %s", state));
    }
    return new PartitionState(State.LEARNER, priority, config);
  }

  public PartitionState toLeaving() {
    return new PartitionState(State.LEAVING, priority, config);
  }

  public PartitionState toRecovering() {
    return new PartitionState(State.RECOVERING, priority, config);
  }

  public PartitionState updateConfig(final DynamicPartitionConfig config) {
    return new PartitionState(state, priority, config);
  }

  public PartitionState updateConfig(final UnaryOperator<DynamicPartitionConfig> configUpdater) {
    return new PartitionState(state, priority, configUpdater.apply(config));
  }

  /**
   * Please note that when <a href="https://github.com/camunda/camunda/issues/14786">order of
   * priority in priority election</a> is changed, this method must be updated.
   */
  public boolean hasHigherPriority(final int priority) {
    return this.priority > priority;
  }

  public enum State {
    UNKNOWN,
    JOINING,
    ACTIVE,
    LEAVING,
    BOOTSTRAPPING,
    RECOVERING,
    /**
     * The member replicates the partition as a non-voting learner: it joined the replication group
     * in a first configuration change and is caught up on the log before a second configuration
     * change promotes it to {@link #ACTIVE}. Unlike {@link #JOINING}, this state survives a
     * restart: the partition is part of the member's partition distribution and is started on boot,
     * recovering its Raft state from disk, so a pending promotion can complete.
     *
     * <p>Rolling upgrades: a broker without this value decodes it as UNKNOWN and would conflict on
     * merge at an equal version. That merge is unreachable, because any configuration carrying a
     * LEARNER partition also carries a promote operation, pending or in the completed-change
     * history, which such a broker cannot decode - it rejects the whole gossiped configuration
     * before ever re-gossiping the UNKNOWN state.
     */
    LEARNER;

    /**
     * True for a member that currently, durably participates in this partition's Raft quorum: a
     * full voting member that is not on its way out. Use this wherever "how many replicas would
     * remain" or "who is a valid leader/primary candidate" matters.
     *
     * <p>{@link #LEARNER} and {@link #JOINING}/{@link #BOOTSTRAPPING} have not (yet) joined the
     * quorum as a voter; {@link #LEAVING} is in the process of leaving it and must not be counted
     * as durable redundancy or picked as a future leader. {@link #RECOVERING} is a fully-voting
     * member that has merely paused its own stream processing - an engine-layer concern, entirely
     * orthogonal to Raft membership - so it counts.
     *
     * <p>Deliberately an exhaustive switch: a new state forces its author to decide whether it
     * belongs here, rather than silently inheriting an unsafe default the way {@link #LEARNER} once
     * did at every one of this method's call sites before it existed.
     */
    public boolean isActiveReplica() {
      return switch (this) {
        case ACTIVE, RECOVERING -> true;
        case JOINING, LEAVING, BOOTSTRAPPING, LEARNER, UNKNOWN -> false;
      };
    }
  }
}
