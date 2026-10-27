/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.db.repository.MappingMetadataRepository;
import io.camunda.optimize.service.db.repository.SnapshotRepository;
import io.camunda.optimize.service.db.schema.BackupPriority;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BackupWriterTest {

  private static final String[] PRIORITY1_INDICES = new String[] {"job-registry"};
  private static final String[] PRIORITY2_INDICES = new String[] {"process-instance"};

  private SnapshotRepository snapshotRepository;
  private BackupWriter backupWriter;

  @BeforeEach
  void setUp() {
    final MappingMetadataRepository mappingMetadataRepository =
        mock(MappingMetadataRepository.class);
    snapshotRepository = mock(SnapshotRepository.class);
    when(mappingMetadataRepository.getIndexAliasesWithBackupPriority(BackupPriority.PRIORITY1))
        .thenReturn(PRIORITY1_INDICES);
    when(mappingMetadataRepository.getIndexAliasesWithBackupPriority(BackupPriority.PRIORITY2))
        .thenReturn(PRIORITY2_INDICES);
    backupWriter = new BackupWriter(mappingMetadataRepository, snapshotRepository);
  }

  @Test
  void shouldNotTriggerPriority2SnapshotUntilPriority1SnapshotFutureCompletes() {
    // given
    final CompletableFuture<Void> importSnapshotFuture = new CompletableFuture<>();
    when(snapshotRepository.triggerSnapshot(any(), eq(PRIORITY1_INDICES)))
        .thenReturn(importSnapshotFuture);
    when(snapshotRepository.triggerSnapshot(any(), eq(PRIORITY2_INDICES)))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final CompletableFuture<Void> result = backupWriter.triggerSnapshotCreation(1L);

    // then - the PRIORITY2 snapshot must not fire while the PRIORITY1 snapshot is still pending
    verify(snapshotRepository, never()).triggerSnapshot(any(), eq(PRIORITY2_INDICES));

    // when the import snapshot resolves
    importSnapshotFuture.complete(null);
    result.join();

    // then the non-import snapshot is triggered afterward
    verify(snapshotRepository, times(1)).triggerSnapshot(any(), eq(PRIORITY2_INDICES));
  }
}
