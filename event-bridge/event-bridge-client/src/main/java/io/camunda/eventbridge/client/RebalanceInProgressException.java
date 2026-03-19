/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import java.util.List;

/**
 * Thrown by {@link Consumer#poll(int, java.time.Duration)} when any partition signals a {@code
 * REBALANCE_IN_PROGRESS} response. All events collected before the rebalance detection are
 * discarded; the caller must catch this exception, update its partition set from {@link
 * #getNewAssignment()}, and retry {@code poll()}.
 *
 * <p>This exception is unchecked. The {@link Consumer} handle remains valid after it is thrown.
 */
public final class RebalanceInProgressException extends EventBridgeException {

  private final List<Integer> newAssignment;

  public RebalanceInProgressException(final List<Integer> newAssignment) {
    super("Rebalance in progress; new assignment: " + newAssignment);
    this.newAssignment = List.copyOf(newAssignment);
  }

  /** Returns the consumer's new full partition assignment after the rebalance. */
  public List<Integer> getNewAssignment() {
    return newAssignment;
  }
}
