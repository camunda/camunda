/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import java.util.HashMap;
import java.util.Map;

/**
 * A non-durable {@link MeterIdStore} for tests and single-process use. Holding one instance across
 * two {@link MeterIdRegistry} constructions simulates a restart: the second registry reloads the
 * same allocations, so ids stay stable.
 */
public final class InMemoryMeterIdStore implements MeterIdStore {

  private final Map<MeterKey, Integer> ids = new HashMap<>();

  @Override
  public Map<MeterKey, Integer> load() {
    return Map.copyOf(ids);
  }

  @Override
  public void persist(final MeterKey key, final int aggId) {
    ids.put(key, aggId);
  }
}
