/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import java.util.Map;

/**
 * The durable backing of the {@link MeterIdRegistry}'s {@code aggId} allocations. Loading the full
 * map on start and persisting each new allocation is what makes an {@code aggId} stable across
 * restarts — the property on-disk rollup data and the shuffle depend on. Phase 1 ships an in-memory
 * implementation; the durable dataset registry (a later phase) owns the persisted one.
 */
public interface MeterIdStore {

  /** All previously allocated meter → {@code aggId} mappings; empty on a fresh store. */
  Map<MeterKey, Integer> load();

  /** Durably records a newly allocated {@code aggId} for {@code key}. */
  void persist(MeterKey key, int aggId);
}
