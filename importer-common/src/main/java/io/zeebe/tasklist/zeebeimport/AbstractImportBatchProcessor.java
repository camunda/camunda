/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport;

import java.util.concurrent.Callable;
import io.zeebe.tasklist.Metrics;
import io.zeebe.tasklist.exceptions.PersistenceException;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractImportBatchProcessor implements ImportBatchProcessor {

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private Metrics metrics;

  @Override
  public void performImport(ImportBatch importBatch) throws PersistenceException {
    BulkRequest bulkRequest = new BulkRequest();
    processZeebeRecords(importBatch, bulkRequest);
    try {
      withTimer(() -> {
        ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);
        return null;
      });
    } catch (Exception e) {
      throw new PersistenceException(e);
    }
  }

  private void withTimer(Callable<Void> callable) throws Exception {
    metrics.getTimer(Metrics.TIMER_NAME_IMPORT_INDEX_QUERY).recordCallable(callable);
  }

  protected abstract void processZeebeRecords(ImportBatch importBatch, BulkRequest bulkRequest) throws PersistenceException;

}
