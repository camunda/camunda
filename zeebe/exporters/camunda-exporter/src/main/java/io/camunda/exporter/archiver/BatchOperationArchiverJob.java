/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.archiver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationArchiverJob extends AbstractArchiverJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationArchiverJob.class);

  private final ArchiverUtil archiver;

  // TODO: private BatchOperationTemplate batchOperationTemplate;
  // Temp: because BatchOperationTemplate is not available in the common webapps-schema.
  private final String batchOperationIndexName = "batch-operation";
  private final String batchOperationIndexId = "id";

  private final ArchiverRepository archiverRepository;

  public BatchOperationArchiverJob(
      final ArchiverUtil archiver,
      final ArchiverRepository archiverRepository,
      final ScheduledExecutorService executor) {
    super(executor);
    this.archiver = archiver;
    this.archiverRepository = archiverRepository;
  }

  @Override
  public CompletableFuture<Integer> archiveBatch(final ArchiveBatch archiveBatch) {
    final CompletableFuture<Integer> archiveBatchFuture;

    if (archiveBatch != null) {
      LOGGER.debug("Following batch operations are found for archiving: {}", archiveBatch);

      archiveBatchFuture = new CompletableFuture<>();
      archiver
          .moveDocuments(
              //  batchOperationTemplate.getFullQualifiedName(),
              // BatchOperationTemplate.ID,
              batchOperationIndexName, // temp
              batchOperationIndexId, // temp
              archiveBatch.getFinishDate(),
              archiveBatch.getIds())
          .whenComplete(
              (v, e) -> {
                if (e != null) {
                  archiveBatchFuture.completeExceptionally(e);
                  return;
                }
                archiveBatchFuture.complete(archiveBatch.getIds().size());
              });
    } else {
      LOGGER.debug("Nothing to archive");
      archiveBatchFuture = CompletableFuture.completedFuture(0);
    }

    return archiveBatchFuture;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getNextBatch() {
    return archiverRepository.getBatchOperationNextBatch();
  }
}
