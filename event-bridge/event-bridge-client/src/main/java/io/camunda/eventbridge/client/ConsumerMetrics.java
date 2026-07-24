/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

/**
 * Observability seam for a {@link Consumer}'s group-membership lifecycle: owned-partition changes,
 * heartbeat failures, fenced rejoins, and the member's fencing epoch. Every default method is a
 * no-op, so the client never depends on a metrics backend (mirroring {@code JobWorkerMetrics} in
 * the Camunda Java client) — set an implementation via {@link Consumer#metrics(ConsumerMetrics)}
 * only if you want the callbacks; a Micrometer-backed adapter lives in event-bridge-streaming (this
 * module intentionally has no Micrometer dependency).
 *
 * <p><b>Threading.</b> Every callback fires from whichever thread drives the underlying event —
 * usually the background heartbeat scheduler, not the caller's poll/process thread (the same
 * threading note as {@link RebalanceListener}). Keep implementations cheap and non-blocking.
 */
public interface ConsumerMetrics {

  /**
   * Called whenever this consumer's owned partitions change (see {@link
   * RebalanceListener#onPartitionsRevoked} / {@link RebalanceListener#onPartitionsAssigned} for the
   * same delta). Both counts are {@code 0} only when this fires with no actual change, which does
   * not happen — the caller sites only call it on a non-empty delta.
   *
   * @param revoked how many partitions were revoked in this change
   * @param assigned how many partitions were newly assigned in this change
   */
  default void onRebalance(final int revoked, final int assigned) {}

  /** Called whenever a heartbeat attempt fails (transport error, non-2xx, or an unusable reply). */
  default void onHeartbeatFailure() {}

  /**
   * Called when a heartbeat is fenced or the membership is unknown (HTTP 409) and this consumer
   * decides to rejoin. Fires once per genuine fencing event, not per stale/discarded 409 (a 409
   * whose membership snapshot no longer matches the current one is superseded noise, not a fresh
   * fencing of the live member).
   */
  default void onFencedRejoin() {}

  /**
   * Called when the rejoin request itself is rejected with HTTP 409 — counted separately from
   * {@link #onFencedRejoin()} so a rejoin storm (many members losing the join race for the same
   * static instance id) is distinguishable from ordinary fenced-then-recovered churn.
   */
  default void onRejoinRejected() {}

  /**
   * Called whenever this consumer's fencing {@code memberEpoch} changes — on the initial
   * join/rejoin response and on every heartbeat-carried full reconciliation. A strictly higher
   * value than the last call is the observable proof that this consumer's identity advanced (e.g. a
   * static-membership takeover on the coordinator); it never decreases.
   *
   * @param memberEpoch the new epoch
   */
  default void onEpochChanged(final long memberEpoch) {}

  /** An implementation that does nothing — the default when no metrics are configured. */
  static ConsumerMetrics noop() {
    return new ConsumerMetrics() {};
  }
}
