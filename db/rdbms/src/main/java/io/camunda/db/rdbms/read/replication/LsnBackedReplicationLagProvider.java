/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.replication;

import java.util.List;

/** Derives replication lag from an LSN provider, for databases that report both. */
public final class LsnBackedReplicationLagProvider implements ReplicationLagProvider {

  private final ReplicationLsnProvider lsnProvider;

  public LsnBackedReplicationLagProvider(final ReplicationLsnProvider lsnProvider) {
    this.lsnProvider = lsnProvider;
  }

  @Override
  public long getCurrentDbTime() {
    return lsnProvider.getCurrentDbTime();
  }

  @Override
  public List<ReplicationLagStatus> getReplicationStatuses() {
    return lsnProvider.getReplicationStatuses().stream()
        .map(
            status ->
                new ReplicationLagStatus(
                    status.replicaId(), status.replicationLagMs(), status.replicatedUntilMs()))
        .toList();
  }
}
