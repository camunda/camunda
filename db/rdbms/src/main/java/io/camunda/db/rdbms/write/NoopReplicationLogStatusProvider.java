/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import java.time.Duration;
import java.util.List;

/**
 * A no-op implementation that signals LSN-based replication checking is not available. The
 * replication controller will rely solely on the configured maxLag timeout for position
 * confirmation.
 */
public class NoopReplicationLogStatusProvider implements ReplicationLogStatusProvider {

  @Override
  public long getCurrent() {
    return -1;
  }

  @Override
  public List<ReplicationStatusDto> getReplicationStatuses() {
    return List.of();
  }

  @Override
  public Duration getReplicationLag() {
    return Duration.ZERO;
  }
}
