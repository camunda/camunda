/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.util;

import io.camunda.zeebe.journal.JournalMetaStore.InMemory;

public final class MockJournalMetastore extends InMemory {
  private volatile Runnable onStoreFlushedIndex;

  @Override
  public void storeLastFlushedIndex(final long index) {
    if (onStoreFlushedIndex != null) {
      onStoreFlushedIndex.run();
    }

    super.storeLastFlushedIndex(index);
  }

  public void setOnStoreFlushedIndex(final Runnable onStoreFlushedIndex) {
    this.onStoreFlushedIndex = onStoreFlushedIndex;
  }
}
