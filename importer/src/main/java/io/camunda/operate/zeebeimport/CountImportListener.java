/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class CountImportListener implements ImportListener {

  protected AtomicInteger importedCount = new AtomicInteger();
  protected AtomicInteger scheduledCount = new AtomicInteger();

  @Override
  public void scheduled(final ImportBatch importBatch) {
    scheduledCount.addAndGet(importBatch.getHits().size());
  }

  @Override
  public void finished(final ImportBatch importBatch) {
    importedCount.addAndGet(importBatch.getHits().size());
  }

  public int getImportedCount() {
    return importedCount.get();
  }

  public int getScheduledCount() {
    return scheduledCount.get();
  }

}
