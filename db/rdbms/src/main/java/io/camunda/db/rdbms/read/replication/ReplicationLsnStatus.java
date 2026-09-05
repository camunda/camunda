/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.replication;

/**
 * Per-replica replication state reported by a {@link ReplicationLsnProvider}: log-sequence number,
 * replica id, replication lag in milliseconds, and {@code replicatedUntilMs}
 */
public record ReplicationLsnStatus(
    Long logStatus, String replicaId, Long replicationLagMs, Long replicatedUntilMs)
    implements ReplicationStatus {

  public ReplicationLsnStatus(
      final Long logStatus, final String replicaId, final Long replicationLagMs) {
    this(logStatus, replicaId, replicationLagMs, null);
  }
}
