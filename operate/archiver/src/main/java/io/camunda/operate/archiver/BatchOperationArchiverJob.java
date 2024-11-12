/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
public class BatchOperationArchiverJob extends AbstractArchiverJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationArchiverJob.class);

  private final Archiver archiver;

  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Autowired private OperateProperties operateProperties;

  @Autowired private Metrics metrics;

  @Autowired private ArchiverRepository archiverRepository;

  public BatchOperationArchiverJob(final Archiver archiver) {
    this.archiver = archiver;
  }

  @Override
  public CompletableFuture<Integer> archiveBatch(final ArchiveBatch archiveBatch) {
    final CompletableFuture<Integer> archiveBatchFuture;

    if (archiveBatch != null) {
      LOGGER.debug("Following batch operations are found for archiving: {}", archiveBatch);

      archiveBatchFuture = new CompletableFuture<>();
      archiver
          .moveDocuments(
              batchOperationTemplate.getFullQualifiedName(),
              BatchOperationTemplate.ID,
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
