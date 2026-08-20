/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.replication;

import io.camunda.db.rdbms.sql.ReplicationStatusMapper;
import java.util.List;

/**
 * AWS Aurora Global Database implementation using {@code aurora_global_db_instance_status()}.
 *
 * <p>The primary's position is read as the {@code durable_lsn} of the row with {@code session_id =
 * 'MASTER_SESSION_ID'}. Each secondary region contributes one row whose {@code durable_lsn}
 * represents how far that instance has replicated data from the primary. The {@code
 * visibility_lag_in_msec} column is surfaced as {@code replicationLagMs} for observability.
 *
 * <p>Aurora always presents itself as a PostgreSQL database to JDBC clients, so this provider is
 * selected at runtime by {@link ReplicationLogStatusProviderFactory} when the Aurora-specific
 * function is detected in {@code pg_proc}.
 */
public final class AuroraReplicationLogStatusProvider implements ReplicationLogStatusProvider {

  private final ReplicationStatusMapper mapper;

  public AuroraReplicationLogStatusProvider(final ReplicationStatusMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long getCurrent() {
    return mapper.getAuroraCurrentLogStatus();
  }

  @Override
  public List<ReplicationLogStatus> getReplicationStatuses() {
    return mapper.getAuroraReplicationStatus();
  }
}
