/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.eventbridge.analytics.projection.StateBackedProjectionStore;
import io.camunda.eventbridge.streaming.OffsetStore;
import java.util.Map;

/**
 * Runtime {@link OffsetStore} backed by the base projection store's consumed-position sub-store.
 * Because it is the <em>same</em> store the projection checkpoint flushes, the runtime writes the
 * offset before the task checkpoint so both land in one transaction.
 */
final class ProjectionOffsetStore implements OffsetStore {

  private final StateBackedProjectionStore store;

  ProjectionOffsetStore(final StateBackedProjectionStore store) {
    this.store = store;
  }

  @Override
  public Map<Integer, Long> restore() {
    return store.consumedPositions();
  }

  @Override
  public void store(final int partition, final long offset) {
    store.setConsumedPosition(partition, offset);
  }
}
