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
 * replication lag, without requiring a log-sequence number.
 */
public interface ReplicationLagProvider {

  /** Returns the primary's own clock, read directly from the database. */
  long getCurrentDbTime();

  /**
   * Returns per-replica state: a stable unique identifier and the DB-reported replication lag in
   * milliseconds.
   */
  List<ReplicationLagStatus> getReplicationStatuses();
}
