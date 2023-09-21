/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver.opensearch;

import io.camunda.operate.Metrics;
import io.camunda.operate.archiver.AbstractArchiverJob;
import io.camunda.operate.archiver.ArchiveBatch;
import io.camunda.operate.archiver.BatchOperationArchiverJob;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Conditional(OpensearchCondition.class)
@Component
@Scope(SCOPE_PROTOTYPE)
public class OpensearchBatchOperationArchiverJob extends AbstractArchiverJob implements BatchOperationArchiverJob {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchBatchOperationArchiverJob.class);

  private OpensearchArchiver archiver;

  @Autowired
  private BatchOperationTemplate batchOperationTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private Metrics metrics;

  public OpensearchBatchOperationArchiverJob(OpensearchArchiver archiver){
    this.archiver = archiver;
  }

  @Override
  public CompletableFuture<Integer> archiveBatch(ArchiveBatch archiveBatch) {
    final CompletableFuture<Integer> archiveBatchFuture;

    if (archiveBatch != null) {
      logger.debug("Following batch operations are found for archiving: {}", archiveBatch);

      archiveBatchFuture = new CompletableFuture<>();
      archiver.moveDocuments(batchOperationTemplate.getFullQualifiedName(),
          BatchOperationTemplate.ID,
          archiveBatch.getFinishDate(),
          archiveBatch.getIds())
        .whenComplete((v, e) -> {
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
    throw new UnsupportedOperationException();
  }

  private Timer getArchiverQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY);
  }

}
