/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.util.SnapshotUtil.getSnapshotName;

import io.camunda.optimize.service.db.repository.MappingMetadataRepository;
import io.camunda.optimize.service.db.repository.SnapshotRepository;
import io.camunda.optimize.service.db.schema.BackupPriority;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class BackupWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(BackupWriter.class);
  private final MappingMetadataRepository mappingMetadataRepository;
  private final SnapshotRepository snapshotRepository;

  public BackupWriter(
      final MappingMetadataRepository mappingMetadataRepository,
      final SnapshotRepository snapshotRepository) {
    this.mappingMetadataRepository = mappingMetadataRepository;
    this.snapshotRepository = snapshotRepository;
  }

  public CompletableFuture<Void> triggerSnapshotCreation(final Long backupId) {
    final String snapshot1Name = getSnapshotName(BackupPriority.PRIORITY1, backupId);
    final String snapshot2Name = getSnapshotName(BackupPriority.PRIORITY2, backupId);
    return CompletableFuture.supplyAsync(
            () -> getIndexAliasesWithBackupPriority(BackupPriority.PRIORITY1))
        .thenCompose(aliases -> snapshotRepository.triggerSnapshot(snapshot1Name, aliases))
        .thenComposeAsync(
            ignored ->
                CompletableFuture.supplyAsync(
                        () -> getIndexAliasesWithBackupPriority(BackupPriority.PRIORITY2))
                    .thenCompose(
                        aliases -> snapshotRepository.triggerSnapshot(snapshot2Name, aliases)));
  }

  public void deleteOptimizeSnapshots(final Long backupId) {
    snapshotRepository.deleteOptimizeSnapshots(backupId);
  }

  private String[] getIndexAliasesWithBackupPriority(final BackupPriority backupPriority) {
    return mappingMetadataRepository.getIndexAliasesWithBackupPriority(backupPriority);
  }
}
