/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin.backup;

public class BackupAlreadyExistException extends RuntimeException {

  public BackupAlreadyExistException(final long expectedBackupId, final long latestBackupId) {
    super(
        "Requested backup has id %d. The latest backup has id %d. A backupId is an integer and must be greater than the ID of previous backups that are completed, failed, or deleted. Zeebe does not take two backups with the same ids."
            .formatted(expectedBackupId, latestBackupId));
  }
}
