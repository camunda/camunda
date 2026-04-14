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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bundles all async-replication dependencies. When async replication is disabled, callers pass
 * {@code null} instead of creating this record.
 */
public record ReplicationContext(
    ReplicationLsnProvider lsnProvider,
    Duration pollingInterval,
    BlockingQueue<LsnPositionEntry> pendingEntries,
    AtomicLong confirmedPosition) {

  public static final int DEFAULT_QUEUE_CAPACITY = 8192;

  public ReplicationContext(
      final ReplicationLsnProvider lsnProvider, final Duration pollingInterval) {
    this(
        lsnProvider,
        pollingInterval,
        new ArrayBlockingQueue<>(DEFAULT_QUEUE_CAPACITY),
        new AtomicLong(-1));
  }
}
