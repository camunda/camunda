/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.es;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.zeebeimport.ImportBatch;
import io.camunda.tasklist.zeebeimport.ImportBatchProcessor;
import java.util.concurrent.Callable;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class AbstractImportBatchProcessorElasticSearch implements ImportBatchProcessor {

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired private Metrics metrics;

  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public void performImport(ImportBatch importBatchElasticSearch) throws PersistenceException {
    final BulkRequest bulkRequest = new BulkRequest();
    processZeebeRecords(importBatchElasticSearch, bulkRequest);
    try {
      withTimer(
          () -> {
            ElasticsearchUtil.processBulkRequest(
                esClient,
                bulkRequest,
                tasklistProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
            return null;
          });
    } catch (Exception e) {
      throw new PersistenceException(e);
    }
  }

  private void withTimer(Callable<Void> callable) throws Exception {
    metrics.getTimer(Metrics.TIMER_NAME_IMPORT_INDEX_QUERY).recordCallable(callable);
  }

  protected abstract void processZeebeRecords(
      ImportBatch importBatchElasticSearch, BulkRequest bulkRequest) throws PersistenceException;
}
