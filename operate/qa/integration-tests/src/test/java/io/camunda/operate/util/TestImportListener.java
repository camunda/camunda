/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.operate.zeebeimport.CountImportListener;
import io.camunda.operate.zeebeimport.ImportBatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class TestImportListener extends CountImportListener {

  private Runnable batchFinishedListener;

  @Override
  public void finished(ImportBatch importBatch) {
    super.finished(importBatch);
    if (batchFinishedListener != null) {
      batchFinishedListener.run();
    }
  }

  public void setBatchFinishedListener(Runnable batchFinishedListener) {
    this.batchFinishedListener = batchFinishedListener;
  }

  public void resetCounters() {
    importedCount = new AtomicInteger(0);
    scheduledCount = new AtomicInteger(0);
  }
}
