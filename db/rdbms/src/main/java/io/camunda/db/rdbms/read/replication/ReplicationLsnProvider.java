/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.replication;

import java.util.List;

/**
 * Provides replication log-sequence-number (LSN) status for databases that expose one: a WAL LSN
 * (PostgreSQL), a durable LSN (Aurora), or an Always On LSN (on-prem/containerized SQL Server). The
 * primary's current position is captured after each flush to know what has been committed. Replica
 * statuses are polled periodically to determine what each replica has applied.
 */
public interface ReplicationLsnProvider {

  /** Returns the primary's current replication position after the last commit. */
  long getCurrent();

  /**
   * Returns per-replica state: last replayed LSN, a stable unique identifier, and the DB-reported
   * replication lag in milliseconds. Returning one row per replica lets the caller apply
   * quorum-aware aggregation instead of collapsing to a single worst-case value in SQL.
   */
  List<ReplicationLsnStatus> getReplicationStatuses();
}
