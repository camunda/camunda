/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

/**
 * Provides LSN (Log Sequence Number) information for tracking async replication state. The primary
 * LSN is captured after each flush to know what the primary has committed. The replica LSN is
 * polled periodically to know what the replica has applied.
 *
 * <p>Implementations are database-specific (Aurora MySQL, Aurora PostgreSQL, standalone
 * PostgreSQL).
 */
public interface ReplicationLsnProvider {

  /** Returns the primary's current WAL/binlog position after the last commit. */
  long getCurrentLsn();

  /** Returns the replica's last replayed position, queried from the primary. */
  long getReplicaLsn();
}
