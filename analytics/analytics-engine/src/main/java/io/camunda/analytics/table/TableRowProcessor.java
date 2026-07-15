/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.table;

import io.camunda.analytics.dataset.CompiledFilter;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.Utf8View;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.serving.spi.DatasetWriter;
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
 *
 * <p>A table declaring eviction predicates is a live working set: a fact satisfying <em>all</em> of
 * them deletes the row for its key instead of upserting one. Eviction is checked before the row
 * filters — the evicting fact describes a row's end (e.g. a completion), so it deliberately does
 * not have to match the filters that admit rows (e.g. {@code transition = ACTIVATED}). Deletes are
 * as idempotent as upserts (removing an absent row is a no-op), so replays stay safe.
 */
public final class TableRowProcessor implements Processor<Fact, Void> {

  private final FactType factType;
  private final RegisteredDataset dataset;
  private final List<CompiledFilter> filters;
  private final List<CompiledFilter> evictionFilters;
  private final CompiledTable table;
  private final DatasetWriter writer;

  public TableRowProcessor(
      final RegisteredDataset dataset, final CompiledTable table, final DatasetWriter writer) {
    factType = table.factBinding().factType();
    this.dataset = dataset;
    filters = table.factBinding().filters().stream().map(CompiledFilter::new).toList();
    evictionFilters = table.evictionFilters().stream().map(CompiledFilter::new).toList();
    this.table = table;
    this.writer = writer;
  }

  /** The bound fact type this table keeps — the stage routes only matching facts here. */
  public FactType factType() {
    return factType;
  }

  @Override
  public void process(final Fact fact) {
    if (fact.factType() != factType
        || !dataset.admits(fact.sourcePartition(), fact.sourcePosition(), fact.eventTime())) {
      return;
    }
    if (evicts(fact)) {
      final Object key = fact.get(table.keyField());
      if (key != null) {
        writer.deleteRow(table, String.valueOf(key));
      }
      return;
    }
    if (!matchesFilters(fact)) {
      return;
    }
    final Object key = fact.get(table.keyField());
    if (key == null) {
      return;
    }
    final List<Object> values = new ArrayList<>(table.columns().size());
    for (final DimensionColumn column : table.columns()) {
      values.add(text(fact.get(column.name())));
    }
    writer.upsertRow(table, String.valueOf(key), values);
  }

  /** Whether the fact satisfies the whole eviction conjunction (an empty one never evicts). */
  private boolean evicts(final Fact fact) {
    if (evictionFilters.isEmpty()) {
      return false;
    }
    for (final CompiledFilter filter : evictionFilters) {
      if (!filter.matches(fact)) {
        return false;
      }
    }
    return true;
  }

  /** Projected rows are a mandatory String edge: a UTF-8 view materializes here, once per row. */
  private static Object text(final Object value) {
    return value instanceof final Utf8View view ? view.toString() : value;
  }

  private boolean matchesFilters(final Fact fact) {
    for (final CompiledFilter filter : filters) {
      if (!filter.matches(fact)) {
        return false;
      }
    }
    return true;
  }
}
