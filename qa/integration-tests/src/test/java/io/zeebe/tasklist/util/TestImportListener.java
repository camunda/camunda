/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import org.springframework.stereotype.Component;
import io.zeebe.tasklist.zeebeimport.ImportBatch;
import io.zeebe.tasklist.zeebeimport.ImportListener;

@Component
public class TestImportListener implements ImportListener {

  private int imported;
  private int failed;

  public void resetCounters() {
    imported = 0;
    failed = 0;
  }

  @Override
  public void finished(ImportBatch importBatch) {
    imported += importBatch.getRecordsCount();
  }

  @Override
  public void failed(ImportBatch importBatch) {
    failed += importBatch.getRecordsCount();
  }

  public int getImported() {
    return imported;
  }

  public void setImported(int imported) {
    this.imported = imported;
  }

  public int getFailed() {
    return failed;
  }

  public void setFailed(int failed) {
    this.failed = failed;
  }
}
