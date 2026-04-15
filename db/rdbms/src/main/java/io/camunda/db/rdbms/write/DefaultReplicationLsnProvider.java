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

public class DefaultReplicationLsnProvider implements ReplicationLsnProvider {

  private final ExporterPositionMapper exporterPositionMapper;

  public DefaultReplicationLsnProvider(final ExporterPositionMapper exporterPositionMapper) {
    this.exporterPositionMapper = exporterPositionMapper;
  }

  @Override
  public long getCurrentLsn() {
    try {
      return exporterPositionMapper.findCurrentLsnPostgres();
    } catch (final Exception e) {
      return exporterPositionMapper.findCurrentLsnAurora();
    }
  }

  @Override
  public List<ReplicationStatusDto> getReplicationStatuses() {
    try {
      return exporterPositionMapper.getReplicationStatusesPostgres();
    } catch (final Exception e) {
      return exporterPositionMapper.getReplicationStatusesAurora();
    }
  }
}
