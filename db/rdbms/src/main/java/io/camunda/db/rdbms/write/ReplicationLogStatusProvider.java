/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import java.util.List;

/**
 * Provides replication log status for tracking async replication state. The primary's current
 * position is captured after each flush to know what has been committed. Replica statuses are
 * polled periodically to determine what each replica has applied.
 *
 * <p>The position can represent different metrics depending on the database: a WAL LSN
 * (PostgreSQL), a durable LSN (Aurora), or a timestamp (Azure SQL). Implementations are
 * database-specific.
 */
public interface ReplicationLogStatusProvider {

  /** Returns the primary's current replication position after the last commit. */
  long getCurrent();

  /** Returns each replica's last replayed position/timestamp and stable unique identifier. */
  List<ReplicationStatusDto> getReplicationStatuses();
}
