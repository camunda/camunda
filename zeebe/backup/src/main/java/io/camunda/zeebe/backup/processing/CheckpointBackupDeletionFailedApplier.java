/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;

/**
 * Applies the FAILED_BACKUP_DELETION event during replay. This indicates that a backup deletion
 * failed.
 */
public class CheckpointBackupDeletionFailedApplier {

  public void apply(final CheckpointRecord checkpointRecord) {
    // No state changes needed - state was cleared on DELETING_BACKUP
  }
}
