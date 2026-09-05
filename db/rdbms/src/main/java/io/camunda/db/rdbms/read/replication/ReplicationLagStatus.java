/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.replication;

/**
 * Per-replica replication state reported by a {@link ReplicationLagProvider}: replica id,
 * replication lag in milliseconds, and {@code replicatedUntilMs}
 */
public record ReplicationLagStatus(String replicaId, Long replicationLagMs, Long replicatedUntilMs)
    implements ReplicationStatus {

  public ReplicationLagStatus(final String replicaId, final Long replicationLagMs) {
    this(replicaId, replicationLagMs, null);
  }
}
