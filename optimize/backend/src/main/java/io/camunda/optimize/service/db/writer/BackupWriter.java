/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.util.SnapshotUtil.getSnapshotNameForImportIndices;
import static io.camunda.optimize.service.util.SnapshotUtil.getSnapshotNameForNonImportIndices;

import io.camunda.optimize.service.db.repository.MappingMetadataRepository;
import io.camunda.optimize.service.db.repository.SnapshotRepository;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackupWriter {
  private final MappingMetadataRepository mappingMetadataRepository;
  private final SnapshotRepository snapshotRepository;
  private final OptimizeIndexNameService indexNameService;

  public void triggerSnapshotCreation(final Long backupId) {
    final String snapshot1Name = getSnapshotNameForImportIndices(backupId);
    final String snapshot2Name = getSnapshotNameForNonImportIndices(backupId);
    CompletableFuture.runAsync(
        () -> {
          snapshotRepository.triggerSnapshot(
              snapshot1Name, getIndexAliasesWithImportIndexFlag(true));
          snapshotRepository.triggerSnapshot(
              snapshot2Name, getIndexAliasesWithImportIndexFlag(false));
        });
  }

  public void deleteOptimizeSnapshots(final Long backupId) {
    snapshotRepository.deleteOptimizeSnapshots(backupId);
  }

  private String[] getIndexAliasesWithImportIndexFlag(final boolean isImportIndex) {
    return mappingMetadataRepository.getAllMappings().stream()
        .filter(mapping -> isImportIndex == mapping.isImportIndex())
        .map(indexNameService::getOptimizeIndexAliasForIndex)
        .toArray(String[]::new);
  }
}
