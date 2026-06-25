/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.state.group.GroupLifecycle;
import io.camunda.eventbridge.consumergroups.state.group.GroupState;
import java.time.Duration;

/** Resolves the debounced rebalance deadline a membership processor stamps on its event. */
final class RebalanceDebounce {

  private RebalanceDebounce() {}

  /**
   * The due time for a group entering (or staying in) {@code PREPARING_REBALANCE}: if the group is
   * already pending a rebalance, its existing deadline is kept — so a burst of joins/leaves is one
   * fixed window measured from the first change, not a sliding one that churn could postpone
   * indefinitely. Otherwise the window opens now (the command's replicated processing time) plus
   * the debounce, so every replica resolves the same deadline.
   */
  static long dueAt(
      final GroupState current, final long commandTimestamp, final Duration debounce) {
    if (current != null
        && current.getState() == GroupLifecycle.PREPARING_REBALANCE
        && current.getRebalanceDueAt() != 0) {
      return current.getRebalanceDueAt();
    }
    return commandTimestamp + debounce.toMillis();
  }
}
