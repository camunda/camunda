/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A non-persistent {@link BaseProjectionStore} backed by a hash map. Used to unit-test the fold
 * logic offline; the RocksDB-backed implementation is the production local cache.
 */
public final class InMemoryBaseProjectionStore implements BaseProjectionStore {

  private final Map<Long, ProcessInstanceProjection> byInstanceKey = new HashMap<>();
  private long consumedPosition = NO_POSITION;

  @Override
  public Optional<ProcessInstanceProjection> get(final long processInstanceKey) {
    return Optional.ofNullable(byInstanceKey.get(processInstanceKey));
  }

  @Override
  public void put(final ProcessInstanceProjection projection) {
    byInstanceKey.put(projection.processInstanceKey(), projection);
  }

  @Override
  public long getConsumedPosition() {
    return consumedPosition;
  }

  @Override
  public void setConsumedPosition(final long position) {
    consumedPosition = position;
  }
}
