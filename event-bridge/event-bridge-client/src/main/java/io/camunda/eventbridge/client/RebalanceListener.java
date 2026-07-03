/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import java.util.Collection;

/**
 * Notified when a {@link Consumer}'s partition assignment changes, so a stateful caller can react
 * to partitions moving between members on a rebalance — release a revoked partition's state, and
 * recover a newly-assigned one.
 *
 * <p><b>Threading.</b> The callbacks fire from whichever thread applies the assignment change (the
 * background heartbeat), which is <em>not</em> the caller's poll/process thread. They run outside
 * the consumer's internal lock, so a callback may call back into the consumer (e.g. {@link
 * Consumer#seekToBeginning}); still, keep them cheap — the recommended pattern is to record the
 * delta and act on it from the processing thread.
 */
public interface RebalanceListener {

  /**
   * Called when partitions are revoked from this consumer (they moved to another member). Fires
   * before they stop being fetched; the caller should stop treating them as owned.
   */
  default void onPartitionsRevoked(final Collection<TopicPartition> revoked) {}

  /**
   * Called when partitions are newly assigned to this consumer. A caller that keeps per-partition
   * state must recover it before processing — e.g. {@link Consumer#seekToBeginning} to rebuild from
   * the source when no local state exists for the partition.
   */
  default void onPartitionsAssigned(final Collection<TopicPartition> assigned) {}
}
