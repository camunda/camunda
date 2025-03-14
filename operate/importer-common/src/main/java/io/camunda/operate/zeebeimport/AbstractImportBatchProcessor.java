/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.Metrics;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.store.BatchRequest;
import java.util.concurrent.Callable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractImportBatchProcessor implements ImportBatchProcessor {

  @Autowired private BeanFactory beanFactory;

  @Autowired private OperateProperties operateProperties;

  @Autowired private Metrics metrics;

  @Override
  public void performImport(final ImportBatch importBatch) throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    try {
      withProcessingTimer(
          () -> {
            processZeebeRecords(importBatch, batchRequest);
            return null;
          },
          importBatch);

      withImportIndexQueryTimer(
          () -> {
            batchRequest.execute();
            return null;
          },
          importBatch);

    } catch (final Exception e) {
      throw new PersistenceException(e);
    }
  }

  private void withProcessingTimer(final Callable<Void> callable, final ImportBatch importBatch)
      throws Exception {
    withTimer(callable, Metrics.TIMER_NAME_IMPORT_PROCESSING_DURATION, importBatch);
  }

  private void withImportIndexQueryTimer(
      final Callable<Void> callable, final ImportBatch importBatch) throws Exception {
    withTimer(callable, Metrics.TIMER_NAME_IMPORT_INDEX_QUERY, importBatch);
  }

  private void withTimer(
      final Callable<Void> callable, final String timerName, final ImportBatch importBatch)
      throws Exception {
    metrics
        .getTimer(
            timerName,
            Metrics.TAG_KEY_TYPE,
            importBatch.getImportValueType().name(),
            Metrics.TAG_KEY_PARTITION,
            String.valueOf(importBatch.getPartitionId()))
        .recordCallable(callable);
  }

  /**
   * Returns action to be performed (synchronously) after successful execution of bulk request.
   *
   * @param importBatch
   * @param batchRequest
   * @throws PersistenceException
   */
  protected abstract void processZeebeRecords(ImportBatch importBatch, BatchRequest batchRequest)
      throws PersistenceException;
}
