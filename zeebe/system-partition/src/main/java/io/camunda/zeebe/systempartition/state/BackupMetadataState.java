/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition.state;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

/**
 * Phase 2 stub for backup metadata state. The real implementation, backed by the {@code
 * ZbColumnFamilies.BACKUP_METADATA} column family, lands in Phase 6.
 */
public final class BackupMetadataState {

  @SuppressWarnings("unused")
  public BackupMetadataState(
      final ZeebeDb<ZbColumnFamilies> db, final TransactionContext transactionContext) {
    // Phase 2 stub: no column family or in-memory state is created yet. See plan §6.
  }
}
