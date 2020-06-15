/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import io.zeebe.tasklist.zeebeimport.ImportBatch;
import io.zeebe.tasklist.zeebeimport.ImportListener;

@Component
public class TestImportListener implements ImportListener {

  private AtomicInteger imported = new AtomicInteger(0);
  private AtomicInteger failed = new AtomicInteger(0);

  public void resetCounters() {
    imported = new AtomicInteger(0);
    failed = new AtomicInteger(0);
  }

  @Override
  public void finished(ImportBatch importBatch) {
    imported.addAndGet(importBatch.getRecordsCount());
  }

  @Override
  public void failed(ImportBatch importBatch) {
    failed.addAndGet(importBatch.getRecordsCount());
  }

  public int getImported() {
    return imported.get();
  }

  public int getFailed() {
    return failed.get();
  }

}
