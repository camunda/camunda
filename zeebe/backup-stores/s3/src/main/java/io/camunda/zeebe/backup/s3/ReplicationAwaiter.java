/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.api.BackupStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.ReplicationStatus;

/**
 * Decides how the store takes the bucket's own replication into account: whether saving a backup
 * waits for its objects to be replicated, and how a backup's status is reported while its manifest
 * replicates. {@link #NONE} ignores replication.
 */
interface ReplicationAwaiter {

  /** Ignores replication: nothing is waited for, and statuses are reported as they are. */
  ReplicationAwaiter NONE =
      new ReplicationAwaiter() {
        @Override
        public Instant deadline() {
          return Instant.now();
        }

        @Override
        public CompletableFuture<Void> awaitReplication(
            final Collection<String> keys, final Instant deadline) {
          return CompletableFuture.completedFuture(null);
        }

        @Override
        public BackupStatus reportedStatus(
            final BackupStatus status, final Optional<ReplicationStatus> replicationStatus) {
          return status;
        }
      };

  static ReplicationAwaiter of(final S3AsyncClient client, final S3BackupConfig config) {
    return config.waitForReplication() ? new PollingReplicationAwaiter(client, config) : NONE;
  }

  /** Returns the point in time after which {@link #awaitReplication} gives up. */
  Instant deadline();

  /** Completes once every given object has been replicated. */
  CompletableFuture<Void> awaitReplication(Collection<String> keys, Instant deadline);

  /**
   * Returns the status to report for a backup whose manifest has the given status and the given
   * replication status, as returned by the bucket when the manifest was read.
   */
  BackupStatus reportedStatus(BackupStatus status, Optional<ReplicationStatus> replicationStatus);
}
