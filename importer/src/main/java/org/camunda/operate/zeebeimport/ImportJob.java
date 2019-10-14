/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

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

  @Autowired(required = false)
  private List<ImportListener> importListeners;

  public ImportJob(ImportBatch importBatch) {
    this.importBatch = importBatch;
  }

  @Override
  public Boolean call() {
    try {
      elasticsearchBulkProcessor.persistZeebeRecords(importBatch);
      importPositionHolder.recordLatestLoadedPosition(importBatch);
      notifyImportListenersAsFinished(importBatch);
      return true;
    } catch (Throwable ex) {
      logger.error(ex.getMessage(), ex);
      notifyImportListenersAsFailed(importBatch);
      return false;
    }
  }
  
  protected void notifyImportListenersAsFinished(ImportBatch importBatch) {
    if (importListeners != null) {
      for (ImportListener importListener : importListeners) {
        importListener.finished(importBatch);
      }
    }
  }

  protected void notifyImportListenersAsFailed(ImportBatch importBatch) {
    if (importListeners != null) {
      for (ImportListener importListener : importListeners) {
        importListener.failed(importBatch);
      }
    }
  }
}
