/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import io.camunda.operate.zeebeimport.CountImportListener;
import java.util.concurrent.atomic.AtomicInteger;

import io.camunda.operate.zeebeimport.ImportBatch;
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
