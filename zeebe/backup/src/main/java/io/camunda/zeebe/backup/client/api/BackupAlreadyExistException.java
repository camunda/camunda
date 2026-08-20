/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.client.api;

public class BackupAlreadyExistException extends RuntimeException {

  public BackupAlreadyExistException(final long expectedBackupId, final long latestBackupId) {
    super(
        ("Requested backup has ID %d. The latest backup has ID %d. The "
                + "backup ID must be greater than the ID of previous backups "
                + "that are completed, failed, or deleted.")
            .formatted(expectedBackupId, latestBackupId));
  }
}
