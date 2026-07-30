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
 * Exposes any {@link ReplicationLsnProvider} as a {@link ReplicationLagProvider} by dropping the
 * {@code logStatus} column and keeping only {@code replicaId}/{@code replicationLagMs}.
 *
 * <p>PostgreSQL, Aurora, and on-prem/containerized SQL Server Always On all already report a
 * DB-measured replication lag alongside their LSN (see {@link ReplicationLsnStatus}), so this lets
 * {@code TimeMonitoringReplicationController} run against any of them via a simpler, coarser lag
 * check instead of exact LSN-based position confirmation.
 */
public final class LsnBackedReplicationLagProvider implements ReplicationLagProvider {

  private final ReplicationLsnProvider lsnProvider;

  public LsnBackedReplicationLagProvider(final ReplicationLsnProvider lsnProvider) {
    this.lsnProvider = lsnProvider;
  }

  @Override
  public List<ReplicationLagStatus> getReplicationStatuses() {
    return lsnProvider.getReplicationStatuses().stream()
        .map(status -> new ReplicationLagStatus(status.replicaId(), status.replicationLagMs()))
        .toList();
  }
}
