/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
public class BatchOperationArchiverJob extends AbstractArchiverJob {

  private static final Logger logger = LoggerFactory.getLogger(BatchOperationArchiverJob.class);

  private Archiver archiver;

  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Autowired private OperateProperties operateProperties;

  @Autowired private Metrics metrics;

  @Autowired private ArchiverRepository archiverRepository;

  public BatchOperationArchiverJob(Archiver archiver) {
    this.archiver = archiver;
  }

  @Override
  public CompletableFuture<Integer> archiveBatch(ArchiveBatch archiveBatch) {
    final CompletableFuture<Integer> archiveBatchFuture;

    if (archiveBatch != null) {
      logger.debug("Following batch operations are found for archiving: {}", archiveBatch);

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
      logger.debug("Nothing to archive");
      archiveBatchFuture = CompletableFuture.completedFuture(0);
    }

    return archiveBatchFuture;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getNextBatch() {
    return archiverRepository.getBatchOperationNextBatch();
  }
}
