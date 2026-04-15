/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import java.util.List;

/** PostgreSQL implementation using {@code pg_current_wal_lsn()} and {@code pg_stat_replication}. */
public final class PostgresReplicationLsnProvider implements ReplicationLsnProvider {

  private final ExporterPositionMapper mapper;

  public PostgresReplicationLsnProvider(final ExporterPositionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long getCurrentLsn() {
    return mapper.findCurrentLsnPostgres();
  }

  @Override
  public List<ReplicationStatusDto> getReplicationStatuses() {
    return mapper.getReplicationStatusesPostgres();
  }
}
