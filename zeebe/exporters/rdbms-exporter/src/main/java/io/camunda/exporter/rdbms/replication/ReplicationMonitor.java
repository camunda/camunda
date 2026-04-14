/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.db.rdbms.write.ReplicationLsnProvider;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Periodically checks the replica's LSN and advances the confirmed position for all entries whose
 * LSN has been replicated. Designed to run as a background task via {@link
 * io.camunda.zeebe.exporter.common.tasks.SelfSchedulingTask}.
 */
public final class ReplicationMonitor {

  private final ReplicationLsnProvider lsnProvider;
  private final Queue<LsnPositionEntry> pendingEntries;
  private final AtomicLong confirmedPosition;
  private final Duration pollingInterval;

  public ReplicationMonitor(
      final ReplicationLsnProvider lsnProvider,
      final Queue<LsnPositionEntry> pendingEntries,
      final AtomicLong confirmedPosition,
      final Duration pollingInterval) {
    this.lsnProvider = lsnProvider;
    this.pendingEntries = pendingEntries;
    this.confirmedPosition = confirmedPosition;
    this.pollingInterval = pollingInterval;
  }

  /**
   * Queries the replica LSN and drains all pending entries whose LSN is at or below the replica's
   * position. Updates the confirmed position with the highest drained entry.
   *
   * @return the polling interval to use for the next check
   */
  public Duration checkReplication() {
    final long replicaLsn = lsnProvider.getReplicaLsn();

    LsnPositionEntry entry;
    while ((entry = pendingEntries.peek()) != null && entry.lsn() <= replicaLsn) {
      confirmedPosition.set(entry.position());
      pendingEntries.poll();
    }

    return pollingInterval;
  }
}
