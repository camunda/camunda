/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.query;

import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.serving.spi.DatasetQueryClient;
import io.camunda.analytics.serving.spi.TableFetch;
import io.camunda.analytics.serving.spi.TableRow;
import java.util.List;

/**
 * Runs a {@link TableQuery} against a {@link CompiledTable}: validates that every filter targets a
 * declared column, then hands a {@link TableFetch} to the {@link DatasetQueryClient}. Unlike a cube
 * there is nothing to plan, merge, or finalize — a table is served as its stored rows — so this is
 * the whole read path for a table (the counterpart of {@link DatasetQueryExecutor} for cubes).
 */
public final class TableQueryExecutor {

  private final DatasetQueryClient client;

  public TableQueryExecutor(final DatasetQueryClient client) {
    this.client = client;
  }

  public List<TableRow> execute(final TableQuery query, final CompiledTable table) {
    for (final FilterPredicate filter : query.filters()) {
      if (!isColumn(table, filter.field())) {
        throw new IllegalArgumentException(
            "filter field '"
                + filter.field()
                + "' is not a column of table '"
                + table.name()
                + "'");
      }
    }
    return client.fetchRows(new TableFetch(table, query.filters(), query.limit()));
  }

  private static boolean isColumn(final CompiledTable table, final String field) {
    for (final DimensionColumn column : table.columns()) {
      if (column.name().equals(field)) {
        return true;
      }
    }
    return false;
  }
}
