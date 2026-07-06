/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.dataset.store.DatasetWriter;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.eventbridge.streaming.processor.Processor;
import java.util.ArrayList;
import java.util.List;

/**
 * A projected (raw) dataset as a Stage-1 {@link Processor} node: it gates the shared fact stream to
 * the facts this dataset keeps — matching {@link FactType}, at/after activation ({@link
 * RegisteredDataset#admits}), satisfying every filter — then upserts one row per fact keyed by the
 * declared key field straight into the serving store. No windowing and no shuffle: the upsert is
 * idempotent by primary key, so a replay re-writes the same rows. A terminal node ({@code Out =
 * Void}); rows are written synchronously on {@link #process}, before the offset advances, so the
 * write is naturally produce-before-commit and the node holds no durable state.
 */
public final class TableRowProcessor implements Processor<Fact, Void> {

  private final FactType factType;
  private final RegisteredDataset dataset;
  private final List<FilterPredicate> filters;
  private final CompiledTable table;
  private final DatasetWriter writer;

  public TableRowProcessor(
      final RegisteredDataset dataset, final CompiledTable table, final DatasetWriter writer) {
    factType = table.factBinding().factType();
    this.dataset = dataset;
    filters = List.copyOf(table.factBinding().filters());
    this.table = table;
    this.writer = writer;
  }

  @Override
  public void process(final Fact fact) {
    if (fact.factType() != factType
        || !dataset.admits(fact.sourcePartition(), fact.sourcePosition())
        || !matchesFilters(fact)) {
      return;
    }
    final Object key = fact.get(table.keyField());
    if (key == null) {
      return;
    }
    final List<Object> values = new ArrayList<>(table.columns().size());
    for (final DimensionColumn column : table.columns()) {
      values.add(fact.get(column.name()));
    }
    writer.upsertRow(table, String.valueOf(key), values);
  }

  private boolean matchesFilters(final Fact fact) {
    for (final FilterPredicate filter : filters) {
      final Object value = fact.get(filter.field());
      final boolean matches =
          filter.operator() == FilterPredicate.Operator.EQUALS
              && value != null
              && String.valueOf(value).equals(filter.value());
      if (!matches) {
        return false;
      }
    }
    return true;
  }
}
