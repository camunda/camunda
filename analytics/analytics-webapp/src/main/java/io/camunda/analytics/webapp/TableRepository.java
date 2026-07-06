/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import io.camunda.analytics.query.TableQuery;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.serving.spi.TableRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Reads raw {@link io.camunda.analytics.dataset.DatasetKind#TABLE table} rows through the neutral
 * serving executor for the API — the table counterpart of the cube reads in {@link
 * AnalyticsRepository}. A table is served as its stored rows (no windows, meters, or reduction), so
 * this stays a thin list-and-fetch.
 */
@Repository
public class TableRepository {

  private static final int MAX_LIMIT = 1000;

  private final TableQueryExecutor executor;
  private final TableCatalog catalog;

  public TableRepository(final TableQueryExecutor executor, final TableCatalog catalog) {
    this.executor = executor;
    this.catalog = catalog;
  }

  /** The names of the tables the serving store can answer. */
  public List<String> listTables() {
    return new ArrayList<>(catalog.names());
  }

  public boolean hasTable(final String name) {
    return catalog.has(name);
  }

  /** Up to {@code limit} rows of {@code name}, each as its declared column values. */
  public List<Map<String, Object>> rows(final String name, final int limit) {
    final int capped = limit <= 0 ? MAX_LIMIT : Math.min(limit, MAX_LIMIT);
    return executor.execute(new TableQuery(List.of(), capped), catalog.require(name)).stream()
        .map(TableRow::values)
        .toList();
  }
}
