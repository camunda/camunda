/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import java.time.Duration;
import java.util.List;

/**
 * Aurora (MySQL/PostgreSQL) implementation using {@code aurora_global_db_instance_status()} to
 * track durable LSN across global database instances.
 */
public final class AuroraReplicationLogStatusProvider implements ReplicationLogStatusProvider {

  private final ExporterPositionMapper mapper;

  public AuroraReplicationLogStatusProvider(final ExporterPositionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long getCurrent() {
    return mapper.findCurrentLsnAurora();
  }

  @Override
  public List<ReplicationStatusDto> getReplicationStatuses() {
    return mapper.getReplicationStatusesAurora();
  }

  @Override
  public Duration getReplicationLag() {
    // TODO: implement via a DB query against aurora_global_db_instance_status()
    return Duration.ZERO;
  }
}
