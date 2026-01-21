/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.os;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.zeebeimport.ImportBatch;
import io.camunda.tasklist.zeebeimport.ImportBatchProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class AbstractImportBatchProcessorOpenSearch implements ImportBatchProcessor {

  @Qualifier("tasklistOsClient")
  @Autowired
  private OpenSearchClient osClient;

  @Autowired private Metrics metrics;

  @Override
  public void performImport(final ImportBatch importBatch) throws PersistenceException {
    final List<BulkOperation> operations = new ArrayList<BulkOperation>();
    processZeebeRecords(importBatch, operations);
    try {
      withTimer(
          () -> {
            OpenSearchUtil.processBulkRequest(
                osClient, new BulkRequest.Builder().operations(operations).build());
            return null;
          });
    } catch (final Exception e) {
      throw new PersistenceException(e);
    }
  }

  private void withTimer(final Callable<Void> callable) throws Exception {
    metrics.getTimer(Metrics.TIMER_NAME_IMPORT_INDEX_QUERY).recordCallable(callable);
  }

  protected abstract void processZeebeRecords(
      ImportBatch importBatchElasticSearch, List<BulkOperation> operations)
      throws PersistenceException;
}
