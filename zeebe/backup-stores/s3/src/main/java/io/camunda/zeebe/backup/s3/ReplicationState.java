/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import java.util.Optional;
import software.amazon.awssdk.services.s3.model.ReplicationStatus;

/** What an object's {@code x-amz-replication-status} says about its replication. */
enum ReplicationState {
  /** The object exists at the replica site. */
  REPLICATED,
  /** Replication is still in progress. */
  PENDING,
  /** Replication failed and will not complete on its own. */
  FAILED,
  /** The object has no replication status: no replication rule covers it. */
  UNTRACKED;

  static ReplicationState classify(final Optional<ReplicationStatus> status) {
    if (status.isEmpty()) {
      return UNTRACKED;
    }
    return switch (status.get()) {
      // Both spellings are in use across S3 and MinIO versions. A REPLICA was written at another
      // site and replicated here, so it exists there too.
      case COMPLETED, COMPLETE, REPLICA -> REPLICATED;
      case FAILED -> FAILED;
      // PENDING, or a status this SDK version does not know
      default -> PENDING;
    };
  }
}
