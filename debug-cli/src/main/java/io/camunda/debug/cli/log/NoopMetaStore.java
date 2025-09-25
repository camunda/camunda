/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.log;

import io.camunda.zeebe.journal.JournalMetaStore;

public class NoopMetaStore implements JournalMetaStore {

  @Override
  public void storeLastFlushedIndex(final long index) {}

  @Override
  public long loadLastFlushedIndex() {
    return 0;
  }

  @Override
  public void resetLastFlushedIndex() {}

  @Override
  public boolean hasLastFlushedIndex() {
    return false;
  }
}
