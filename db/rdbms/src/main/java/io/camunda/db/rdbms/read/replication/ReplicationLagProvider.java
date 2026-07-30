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
 * Provides replication lag status: a stable per-replica identifier and the DB-reported elapsed
 * replication lag, without requiring a log-sequence number. Implemented natively by databases that
 * only expose a lag (e.g. Azure SQL Database geo-replication), and by {@link
 * LsnBackedReplicationLagProvider} for any database that supports {@link ReplicationLsnProvider}
 * and happens to report a lag alongside its LSN. Replica statuses are polled periodically to
 * determine the current lag per replica.
 */
public interface ReplicationLagProvider {

  /**
   * Returns per-replica state: a stable unique identifier and the DB-reported replication lag in
   * milliseconds. Returning one row per replica lets the caller apply quorum-aware aggregation
   * instead of collapsing to a single worst-case value in SQL.
   */
  List<ReplicationLagStatus> getReplicationStatuses();
}
