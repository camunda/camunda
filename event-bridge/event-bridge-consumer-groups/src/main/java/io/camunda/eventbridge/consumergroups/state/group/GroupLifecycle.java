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
 *   PREPARING_REBALANCE --GROUP_REBALANCED--> RECONCILING
 *   RECONCILING --MEMBER_RECONCILED (all members converged)--> STABLE
 *   STABLE/RECONCILING --MEMBER_JOINED/MEMBER_LEFT--> PREPARING_REBALANCE
 *   (any) --MEMBER_LEFT (last member)--> EMPTY --retention--> (group deleted)
 * </pre>
 */
public enum GroupLifecycle {
  /** Has members but no target is computed for the current epoch yet (the assignor is pending). */
  PREPARING_REBALANCE,
  /** Target committed, but not every member has confirmed reconciling to it yet. */
  RECONCILING,
  /** Has members and every member has reconciled to the current target. */
  STABLE,
  /** Exists with no members; retained (with its committed offsets) until retention elapses. */
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
