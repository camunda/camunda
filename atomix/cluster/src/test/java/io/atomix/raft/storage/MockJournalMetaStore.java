/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.storage;

import io.camunda.zeebe.journal.JournalMetaStore;

public class MockJournalMetaStore implements JournalMetaStore {

  long index;

  @Override
  public void storeLastFlushedIndex(final long index) {
    this.index = index;
  }

  @Override
  public long loadLastFlushedIndex() {
    return index;
  }
}
