/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import java.util.concurrent.Callable;
import io.camunda.operate.Metrics;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractImportBatchProcessor implements ImportBatchProcessor {

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private Metrics metrics;

  @Override
  public void performImport(ImportBatch importBatch) throws PersistenceException {
    BulkRequest bulkRequest = new BulkRequest();
    try {
      withProcessingTimer(() -> {
        processZeebeRecords(importBatch, bulkRequest);
        return null;
      }, importBatch);

      withImportIndexQueryTimer(() -> {
        ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
        return null;
      }, importBatch);

    } catch (Exception e) {
      throw new PersistenceException(e);
    }
  }

  private void withProcessingTimer(final Callable<Void> callable, final ImportBatch importBatch) throws Exception {
    withTimer(callable, Metrics.TIMER_NAME_IMPORT_PROCESSING_DURATION, importBatch);
  }

  private void withImportIndexQueryTimer(Callable<Void> callable, final ImportBatch importBatch) throws Exception {
    withTimer(callable, Metrics.TIMER_NAME_IMPORT_INDEX_QUERY, importBatch);
  }

  private void withTimer(final Callable<Void> callable, String timerName, final ImportBatch importBatch) throws Exception {
    metrics.getTimer(timerName,
        Metrics.TAG_KEY_TYPE, importBatch.getImportValueType().name(),
        Metrics.TAG_KEY_PARTITION, String.valueOf(importBatch.getPartitionId()))
    .recordCallable(callable);
  }

  /**
   * Returns action to be performed (synchronously) after successful execution of bulk request.
   * @param importBatch
   * @param bulkRequest
   * @return
   * @throws PersistenceException
   */
  protected abstract void processZeebeRecords(ImportBatch importBatch, BulkRequest bulkRequest) throws PersistenceException;

}
