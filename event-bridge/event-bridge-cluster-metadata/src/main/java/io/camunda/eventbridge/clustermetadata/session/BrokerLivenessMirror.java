/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.session;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe holder of per-broker last-heartbeat times, shared across actors: the leader's {@code
 * BrokerHeartbeatHandler} publishes (on its actor) and the off-actor {@code BrokerEvictionTask}
 * reads. It carries the ephemeral broker liveness the eviction loop needs but which — unlike the
 * broker registry — is deliberately not replicated (heartbeats are cheap, leader-local liveness).
 * The broker counterpart of consumer-groups' {@code MemberLivenessMirror}; constructed at stream
 * startup so the task (built with the record processor) and the handler (built on leader
 * activation) share one instance. Cleared when the node stops leading.
 */
public final class BrokerLivenessMirror {

  private final Map<Integer, Instant> lastHeartbeat = new ConcurrentHashMap<>();

  /** Records a broker's last contact (registration or heartbeat). */
  public void touch(final int brokerId, final Instant now) {
    lastHeartbeat.put(brokerId, now);
  }

  /** Drops a broker's liveness (e.g. once it has been fenced or deregistered). */
  public void remove(final int brokerId) {
    lastHeartbeat.remove(brokerId);
  }

  /** The brokers currently being tracked — the eviction task's bounded work set. */
  public Set<Integer> brokerIds() {
    return Set.copyOf(lastHeartbeat.keySet());
  }

  /** The broker's last-contact time, or {@code null} if it is not tracked. */
  public Instant lastSeen(final int brokerId) {
    return lastHeartbeat.get(brokerId);
  }

  /**
   * Clears all liveness — used when the node stops leading and the ephemeral state is abandoned.
   */
  public void clear() {
    lastHeartbeat.clear();
  }
}
