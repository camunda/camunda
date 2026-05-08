/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.BackupMetadataState;
import io.camunda.zeebe.protocol.impl.record.value.backupmetadata.BackupMetadataRecord;

/**
 * Mutable backup-metadata state. Implementations persist rows in RocksDB and are intended to be
 * driven by deterministic event appliers running on the system partition.
 */
public interface MutableBackupMetadataState extends BackupMetadataState {

  /** Persist (insert or replace) a row keyed by {@code (checkpointId, partitionId)}. */
  void put(BackupMetadataRecord record);

  /** Remove the row keyed by {@code (checkpointId, partitionId)}, if any. */
  void delete(long checkpointId, int partitionId);
}
