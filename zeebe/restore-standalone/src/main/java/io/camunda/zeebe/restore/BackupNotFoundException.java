/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

public class BackupNotFoundException extends RuntimeException {

  public BackupNotFoundException(final long backupId) {
    super("Could not find a completed backup with id %d.".formatted(backupId));
  }

  public BackupNotFoundException(final long backupId, final int partitionId) {
    super(
        "Could not find a completed backup with id %d for partition %d."
            .formatted(backupId, partitionId));
  }
}
