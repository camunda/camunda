/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.replication;

/**
 * Per-replica replication state reported by a {@link ReplicationLagProvider}: the replica's stable
 * identifier and the DB-reported replication lag in milliseconds. Unlike {@link
 * ReplicationLsnStatus}, there is no log-sequence number — databases exposing only a lag provider
 * don't support LSN-based tracking.
 */
public record ReplicationLagStatus(String replicaId, Long replicationLagMs)
    implements ReplicationStatus {}
