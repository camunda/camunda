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
 * Applies the CONFIRMED_BACKUP_DELETION event during replay. This confirms that a backup deletion
 * has completed. State clearing happens on the DELETING_BACKUP event to ensure we always update the
 * state even if the backup store fails to delete.
 */
public class CheckpointBackupDeletionConfirmedApplier {

  public void apply(final CheckpointRecord checkpointRecord) {
    // State clearing happens on DELETING_BACKUP event, not here
  }
}
