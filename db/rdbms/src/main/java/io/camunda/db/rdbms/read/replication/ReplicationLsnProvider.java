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
 * Provides LSN-based replication status: the primary's current log-sequence position, and each
 * replica's applied position, stable identifier, and reported replication lag.
 */
public interface ReplicationLsnProvider {

  /** Returns the primary's current replication position after the last commit. */
  long getCurrent();

  /** Returns the primary's own clock. */
  long getCurrentDbTime();

  /**
   * Returns per-replica state: last replayed position/timestamp, a stable unique identifier, and
   * the DB-reported replication lag in milliseconds.
   */
  List<ReplicationLsnStatus> getReplicationStatuses();
}
