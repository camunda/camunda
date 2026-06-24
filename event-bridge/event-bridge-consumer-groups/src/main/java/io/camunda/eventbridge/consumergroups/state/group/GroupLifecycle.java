/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.group;

/**
 * The lifecycle state of a consumer group — first-class <em>replicated</em> state stored on {@link
 * GroupState} and transitioned by the appliers as they apply membership/rebalance events, so the
 * state machine lives in the log (consistent across replicas, survives failover) rather than being
 * derived at read time.
 *
 * <p>Transitions (all driven by stream events):
 *
 * <pre>
 *   (create) --MEMBER_JOINED--> PREPARING_REBALANCE
 *   PREPARING_REBALANCE --GROUP_REBALANCED--> STABLE
 *   STABLE --MEMBER_JOINED/MEMBER_LEFT--> PREPARING_REBALANCE
 *   PREPARING_REBALANCE --MEMBER_LEFT (last member)--> (group deleted)
 * </pre>
 */
public enum GroupLifecycle {
  /** Has members but its target assignment is stale ({@code assignmentEpoch < groupEpoch}). */
  PREPARING_REBALANCE,
  /** Has members and the committed target reflects the current group epoch. */
  STABLE,
  /**
   * Exists with no members (not normally persisted — the group is deleted when the last leaves).
   */
  EMPTY,
  /**
   * The group does not exist (deleted, or never created) — never stored, only reported by reads.
   */
  DEAD;

  public static GroupLifecycle fromName(final String name) {
    if (name == null || name.isEmpty()) {
      return EMPTY;
    }
    try {
      return valueOf(name);
    } catch (final IllegalArgumentException e) {
      return EMPTY;
    }
  }
}
