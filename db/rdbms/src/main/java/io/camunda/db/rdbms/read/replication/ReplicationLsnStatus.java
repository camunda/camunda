/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.replication;

/**
 * Per-replica replication state reported by a {@link ReplicationLsnProvider}: the replica's last
 * confirmed log-sequence number, its stable identifier, and the DB-reported replication lag in
 * milliseconds (for observability only — position confirmation is based on {@code logStatus}, not
 * this lag value).
 */
public record ReplicationLsnStatus(Long logStatus, String replicaId, Long replicationLagMs)
    implements ReplicationStatus {}
