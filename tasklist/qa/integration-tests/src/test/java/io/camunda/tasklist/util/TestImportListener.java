/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import io.camunda.tasklist.zeebeimport.ImportBatch;
import io.camunda.tasklist.zeebeimport.ImportListener;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class TestImportListener implements ImportListener {

  private AtomicInteger imported = new AtomicInteger(0);
  private AtomicInteger failed = new AtomicInteger(0);

  public void resetCounters() {
    imported = new AtomicInteger(0);
    failed = new AtomicInteger(0);
  }

  @Override
  public void finished(ImportBatch importBatchElasticSearch) {
    imported.addAndGet(importBatchElasticSearch.getRecordsCount());
  }

  @Override
  public void failed(ImportBatch importBatchElasticSearch) {
    failed.addAndGet(importBatchElasticSearch.getRecordsCount());
  }

  public int getImported() {
    return imported.get();
  }

  public int getFailed() {
    return failed.get();
  }
}
