/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import io.camunda.analytics.dataset.CompiledDataset;
import java.util.Map;

/**
 * The compiled standard dashboard cubes, keyed by dataset name — a thin holder so the read layer
 * looks a cube up by name without Spring collecting a raw {@code Map<String, CompiledDataset>} as a
 * bean-by-name map. Loaded once from the metadata plane at startup.
 */
public final class DatasetCatalog {

  private final Map<String, CompiledDataset> byName;

  public DatasetCatalog(final Map<String, CompiledDataset> byName) {
    this.byName = Map.copyOf(byName);
  }

  /** The compiled cube for {@code name}; throws if the metadata plane holds no such dataset. */
  public CompiledDataset require(final String name) {
    final CompiledDataset dataset = byName.get(name);
    if (dataset == null) {
      throw new IllegalStateException(
          "no analytics dataset '" + name + "'; known: " + byName.keySet());
    }
    return dataset;
  }

  public boolean has(final String name) {
    return byName.containsKey(name);
  }

  public Map<String, CompiledDataset> byName() {
    return byName;
  }
}
