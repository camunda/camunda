/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.catalog;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.ActiveTable;
import io.camunda.analytics.serving.spi.MetadataStore;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * An in-JVM, versioned view of the datasets in the metadata plane — the seam that lets a running
 * stage pick up a newly-provisioned dataset without a pause (ADR 0005). It holds the compiled
 * active cubes and tables plus a monotonic {@code version}; {@link #refresh()} re-reads the
 * metadata plane and bumps the version <em>only when the set of datasets actually changed</em>, so
 * a redundant refresh is a no-op and never forces a needless rebuild.
 *
 * <p>The control plane calls {@link #refresh()} after admitting a dataset; each stage {@code Task}
 * compares {@link #version()} at its commit boundary (not per event) and rebuilds its topology from
 * {@link #cubes()}/{@link #tables()} when it moved. The snapshot is swapped atomically (a {@code
 * volatile} reference), so a reader always sees a consistent {version, cubes, tables} triple;
 * refresh is serialized ({@code synchronized}) with the control plane's single-threaded admission.
 */
public final class DatasetCatalog {

  /** An atomic, self-consistent view: the datasets at a given version. */
  public record Snapshot(long version, List<ActiveCube> cubes, List<ActiveTable> tables) {}

  private final MetadataStore metadataStore;
  private volatile Snapshot snapshot;

  public DatasetCatalog(final MetadataStore metadataStore) {
    this.metadataStore = metadataStore;
    snapshot = new Snapshot(0L, List.of(), List.of());
    refresh();
  }

  /**
   * Re-reads and recompiles the datasets from the metadata plane; bumps the version only if the set
   * of cube/table ids changed since the current snapshot. Safe to call redundantly.
   */
  public synchronized void refresh() {
    final List<ActiveCube> cubes = StandardDatasets.loadCubes(metadataStore);
    final List<ActiveTable> tables = StandardDatasets.loadTables(metadataStore);
    final Snapshot current = snapshot;
    if (cubeIds(cubes).equals(cubeIds(current.cubes()))
        && tableIds(tables).equals(tableIds(current.tables()))) {
      return;
    }
    snapshot = new Snapshot(current.version() + 1, cubes, tables);
  }

  /**
   * The current version; a stage rebuilds its topology when this moves past its applied version.
   */
  public long version() {
    return snapshot.version();
  }

  /** A self-consistent {version, cubes, tables} view — read this once per rebuild. */
  public Snapshot snapshot() {
    return snapshot;
  }

  public List<ActiveCube> cubes() {
    return snapshot.cubes();
  }

  public List<ActiveTable> tables() {
    return snapshot.tables();
  }

  private static Set<Long> cubeIds(final List<ActiveCube> cubes) {
    final Set<Long> ids = new TreeSet<>();
    cubes.forEach(cube -> ids.add(cube.registered().cubeId()));
    return ids;
  }

  private static Set<Long> tableIds(final List<ActiveTable> tables) {
    final Set<Long> ids = new TreeSet<>();
    tables.forEach(table -> ids.add(table.registered().cubeId()));
    return ids;
  }
}
