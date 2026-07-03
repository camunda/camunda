/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The set of admitted cubes and their <b>fixed activation vectors</b> — the replay-deterministic
 * replacement for the MVP's 5-second wall-clock table poll. Admitting a dataset assigns it a stable
 * {@code cubeId} and freezes the per-source-partition activation position vector supplied by the
 * control plane (the source high-watermark at admission); it never changes afterward. Stage 1 reads
 * the {@link #active()} cubes and folds a fact into one only when {@link RegisteredDataset#admits}.
 *
 * <p>Because the registry is recovered deterministically ({@link #snapshot()} / {@link #restore}),
 * a cold replay reproduces the exact same cube membership as the live run — no cube retroactively
 * absorbs history it never saw live. Not thread-safe; the control plane admits single-threaded.
 */
public final class DatasetRegistry {

  private final Map<Long, RegisteredDataset> byId = new LinkedHashMap<>();
  private long nextCubeId;

  public DatasetRegistry() {
    this(List.of());
  }

  private DatasetRegistry(final Collection<RegisteredDataset> restored) {
    long maxCubeId = 0;
    for (final RegisteredDataset dataset : restored) {
      byId.put(dataset.cubeId(), dataset);
      maxCubeId = Math.max(maxCubeId, dataset.cubeId());
    }
    nextCubeId = maxCubeId + 1;
  }

  /** Rebuilds a registry from a {@link #snapshot()} — the deterministic recovery path. */
  public static DatasetRegistry restore(final Collection<RegisteredDataset> snapshot) {
    return new DatasetRegistry(snapshot);
  }

  /**
   * Admits a dataset with the activation vector captured at admission (frozen), assigning a stable
   * {@code cubeId}; returns the registered cube.
   */
  public RegisteredDataset admit(
      final DatasetDeclaration declaration, final Map<Integer, Long> activation) {
    final RegisteredDataset dataset =
        new RegisteredDataset(nextCubeId++, declaration, activation, 1);
    byId.put(dataset.cubeId(), dataset);
    return dataset;
  }

  public Optional<RegisteredDataset> get(final long cubeId) {
    return Optional.ofNullable(byId.get(cubeId));
  }

  /** All admitted cubes, in admission order. */
  public Collection<RegisteredDataset> active() {
    return List.copyOf(byId.values());
  }

  /** The admitted cubes, to be persisted so {@link #restore} recovers identical activation. */
  public List<RegisteredDataset> snapshot() {
    return new ArrayList<>(byId.values());
  }
}
