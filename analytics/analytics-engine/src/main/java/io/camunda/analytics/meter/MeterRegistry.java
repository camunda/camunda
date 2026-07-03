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
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Allocates and remembers the stable {@code aggId} of every meter instance, replacing the
 * hand-assigned {@code AGG_*} int constants and their "never reorder" hazard. An {@code aggId} is
 * the routing key threaded through the shuffle ({@code Partial.aggId}), the reduce dispatch, and
 * the durable slot/rollup keys — so it must be:
 *
 * <ul>
 *   <li><b>stable</b> across restarts (same {@link MeterKey} → same id), backed by a {@link
 *       MeterIdStore};
 *   <li><b>unique</b> across all cubes/meters (monotonic allocation, no collisions);
 *   <li><b>never reused</b> — ids only grow, so retiring a meter never rebinds its id to another,
 *       keeping already-written on-disk data addressable.
 * </ul>
 *
 * Not thread-safe; the control plane that admits datasets allocates ids single-threaded.
 */
public final class MeterRegistry {

  private final MeterIdStore store;
  private final Map<MeterKey, Integer> ids;
  private int nextId;

  public MeterRegistry(final MeterIdStore store) {
    this.store = Objects.requireNonNull(store, "store");
    ids = new HashMap<>(store.load());
    nextId = ids.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
  }

  /** The stable {@code aggId} for a meter, allocating (and persisting) a fresh one on first use. */
  public int aggIdFor(final long cubeId, final String meterName) {
    return aggIdFor(new MeterKey(cubeId, meterName));
  }

  public int aggIdFor(final MeterKey key) {
    Objects.requireNonNull(key, "key");
    final Integer existing = ids.get(key);
    if (existing != null) {
      return existing;
    }
    final int aggId = nextId++;
    ids.put(key, aggId);
    store.persist(key, aggId);
    return aggId;
  }

  /** The already-allocated id for {@code key}, or empty if none — never allocates. */
  public OptionalInt existing(final MeterKey key) {
    final Integer id = ids.get(key);
    return id == null ? OptionalInt.empty() : OptionalInt.of(id);
  }

  public Map<MeterKey, Integer> snapshot() {
    return Map.copyOf(ids);
  }
}
