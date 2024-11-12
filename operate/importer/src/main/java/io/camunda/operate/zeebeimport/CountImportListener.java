/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
