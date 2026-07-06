/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import io.camunda.analytics.dataset.CompiledTable;
import java.util.Map;
import java.util.Set;

/**
 * The compiled standard tables, keyed by dataset name — the table counterpart of {@link
 * DatasetCatalog}, so the read layer looks a table up by name. Loaded once from the metadata plane
 * at startup.
 */
public final class TableCatalog {

  private final Map<String, CompiledTable> byName;

  public TableCatalog(final Map<String, CompiledTable> byName) {
    this.byName = Map.copyOf(byName);
  }

  /** The compiled table for {@code name}; throws if the metadata plane holds no such table. */
  public CompiledTable require(final String name) {
    final CompiledTable table = byName.get(name);
    if (table == null) {
      throw new IllegalStateException(
          "no analytics table '" + name + "'; known: " + byName.keySet());
    }
    return table;
  }

  public boolean has(final String name) {
    return byName.containsKey(name);
  }

  public Set<String> names() {
    return byName.keySet();
  }
}
