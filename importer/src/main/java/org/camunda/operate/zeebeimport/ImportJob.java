/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Import job for one batch of Zeebe data.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class ImportJob implements Callable<Boolean> {

  private static final Logger logger = LoggerFactory.getLogger(ImportJob.class);

  private ImportBatch importBatch;

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  @Autowired
  private ImportPositionHolder importPositionHolder;

  @Autowired
  private List<ImportListener> importListeners;

  public ImportJob(ImportBatch importBatch) {
    this.importBatch = importBatch;
  }

  @Override
  public Boolean call() {
    try {
      //do import
      elasticsearchBulkProcessor.persistZeebeRecords(importBatch);
      //record latest position
      final long lastProcessedPosition = importBatch.getRecords().get(importBatch.getRecordsCount() - 1).getPosition();
      importPositionHolder.recordLatestLoadedPosition(importBatch.getImportValueType().getAliasTemplate(),
          importBatch.getPartitionId(), lastProcessedPosition);
      importBatch.notifyImportListenersAsFinished(importListeners);
      return true;
    } catch (Throwable ex) {
      logger.error(ex.getMessage(), ex);
      importBatch.notifyImportListenersAsFailed(importListeners);
      return false;
    }
  }
}
