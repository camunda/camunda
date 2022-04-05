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
import io.camunda.operate.util.ElasticsearchUtil;
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

  /**
   * Returns action to be performed (synchronously) after successful execution of bulk request.
   * @param importBatch
   * @param bulkRequest
   * @return
   * @throws PersistenceException
   */
  protected abstract void processZeebeRecords(ImportBatch importBatch, BulkRequest bulkRequest) throws PersistenceException;

}
