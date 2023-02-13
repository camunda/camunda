/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.util;

import io.camunda.zeebe.journal.JournalMetaStore;

public class MockJournalMetastore implements JournalMetaStore {

  long lastFlushedIndex = -1;

  @Override
  public void storeLastFlushedIndex(final long index) {
    lastFlushedIndex = index;
  }

  @Override
  public long loadLastFlushedIndex() {
    return lastFlushedIndex;
  }
}
