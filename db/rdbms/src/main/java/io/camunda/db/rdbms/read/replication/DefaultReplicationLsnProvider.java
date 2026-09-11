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

public final class DefaultReplicationLsnProvider implements ReplicationLsnProvider {

  private final ReplicationStatusMapper mapper;

  public DefaultReplicationLsnProvider(final ReplicationStatusMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long getCurrent() {
    return mapper.getCurrentLogStatus();
  }

  @Override
  public long getCurrentDbTime() {
    return mapper.getCurrentDbTime();
  }

  @Override
  public List<ReplicationLsnStatus> getReplicationStatuses() {
    return mapper.getReplicationStatus();
  }
}
