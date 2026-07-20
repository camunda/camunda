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

  /**
   * Called when a partition enters this consumer's <b>standby</b> target (event-bridge-streaming
   * ADR 0009 decision 6 / consumer-groups ADR 0006 decision 1) — the member should warm it (tail
   * its changelog) without processing the source. Unlike {@link #onPartitionsAssigned}, a standby
   * partition is never exclusively owned, so there is no cooperative revoke-before-assign handshake
   * to protect: the coordinator delivers its standby target as a full set on every heartbeat, and
   * the client reports only the delta against what it last reported here, mirroring {@link
   * #onPartitionsAssigned}.
   *
   * <p><b>Wire status:</b> nothing calls this yet in production. The coordinator already computes
   * and carries a member's standby target on its internal heartbeat response ({@code
   * HeartbeatResponse#getStandbyAssignment()}), but the client-facing gateway wire contract ({@code
   * ConsumerHeartbeatResponse}, event-bridge-api-proto) has no field for it, so a real heartbeat
   * cannot deliver it here today. This callback (and {@link #onStandbyPartitionsRevoked}) exists so
   * the runtime has a stable, unit-tested seam to react to once that wire field lands.
   */
  default void onStandbyPartitionsAssigned(final Collection<TopicPartition> assigned) {}

  /**
   * Called when a partition leaves this consumer's standby target — see {@link
   * #onStandbyPartitionsAssigned} for the delta/full-target semantics and the current wire-status
   * caveat.
   */
  default void onStandbyPartitionsRevoked(final Collection<TopicPartition> revoked) {}
}
