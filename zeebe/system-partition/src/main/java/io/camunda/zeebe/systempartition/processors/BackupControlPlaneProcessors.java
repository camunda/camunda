/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition.processors;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.systempartition.state.BackupMetadataState;

/**
 * Phase 2 stub for the backup control-plane processor factory. The real implementation, which
 * registers {@code BackupMetadataRecordProcessor} and friends, lands in Phase 6.
 */
public final class BackupControlPlaneProcessors {

  private BackupControlPlaneProcessors() {}

  @SuppressWarnings("unused")
  public static void register(
      final TypedRecordProcessors processors,
      final BackupMetadataState state,
      final KeyGenerator keys) {
    // Phase 2 stub: no processors registered yet.
  }
}
