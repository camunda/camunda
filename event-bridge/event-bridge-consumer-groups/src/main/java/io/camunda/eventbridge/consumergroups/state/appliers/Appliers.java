/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.appliers;

import io.camunda.eventbridge.consumergroups.state.mutable.MutableConsumerGroupState;

/** Small shared mutation helpers for the consumer-group appliers. */
final class Appliers {

  private Appliers() {}

  /**
   * Moves a group's entry in the due-ordered rebalance index from {@code oldDueAt} to {@code
   * newDueAt} (a {@code 0} on either side means "not indexed"). A no-op when the deadline is
   * unchanged, so re-applying a join/leave that kept the same window touches nothing.
   */
  static void reindexRebalanceDue(
      final MutableConsumerGroupState state,
      final String groupId,
      final long oldDueAt,
      final long newDueAt) {
    if (oldDueAt == newDueAt) {
      return;
    }
    if (oldDueAt != 0) {
      state.untrackRebalanceDue(groupId, oldDueAt);
    }
    if (newDueAt != 0) {
      state.trackRebalanceDue(groupId, newDueAt);
    }
  }
}
