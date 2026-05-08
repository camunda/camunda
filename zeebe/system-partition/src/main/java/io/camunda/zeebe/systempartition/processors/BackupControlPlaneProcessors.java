/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition.processors;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.BackupMetadataState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BackupMetadataIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Factory that registers the backup control-plane command processors (RECORD / MARK_FAILED /
 * DELETE) into a {@link TypedRecordProcessors} bundle.
 *
 * <p>The companion {@code BackupMetadataStateApplier} is registered with the engine's {@code
 * EventApplier} pipeline separately — see {@code EventAppliers#registerEventAppliers}.
 */
public final class BackupControlPlaneProcessors {

  private BackupControlPlaneProcessors() {}

  public static void register(
      final TypedRecordProcessors processors,
      final BackupMetadataState state,
      final Writers writers,
      final KeyGenerator keys) {
    final var processor = new BackupMetadataRecordProcessor(state, writers, keys);
    processors.onCommand(ValueType.BACKUP_METADATA, BackupMetadataIntent.RECORD, processor);
    processors.onCommand(ValueType.BACKUP_METADATA, BackupMetadataIntent.MARK_FAILED, processor);
    processors.onCommand(ValueType.BACKUP_METADATA, BackupMetadataIntent.DELETE, processor);
  }
}
