/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import io.camunda.analytics.dataset.store.DatasetWriter;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.eventbridge.streaming.aggregate.Aggregation;
import java.util.ArrayList;
import java.util.List;

/**
 * A projected (raw) dataset as a Stage-1 {@link Aggregation}: it gates the shared fact stream down
 * to the facts this dataset should keep (matching {@link FactType}, at/after activation via {@link
 * RegisteredDataset#admits}, satisfying every filter), then upserts one row per fact keyed by the
 * declared key field straight into the serving store. There is no windowed aggregation and no
 * shuffle — the upsert is idempotent by primary key, so a replay of the same facts re-writes the
 * same rows. Facts with no key value are skipped.
 *
 * <p>Because rows are written synchronously as facts arrive (before the shard's offset advances),
 * the write is naturally produce-before-commit; the aggregation keeps no durable RocksDB state, so
 * its lifecycle callbacks are no-ops and it does not constrain the shard's commit watermark.
 */
public final class ProjectionRowAggregation implements Aggregation<Fact> {

  private final FactType factType;
  private final RegisteredDataset dataset;
  private final List<FilterPredicate> filters;
  private final CompiledProjection projection;
  private final DatasetWriter writer;

  public ProjectionRowAggregation(
      final RegisteredDataset dataset,
      final CompiledProjection projection,
      final DatasetWriter writer) {
    this.factType = projection.factBinding().factType();
    this.dataset = dataset;
    this.filters = List.copyOf(projection.factBinding().filters());
    this.projection = projection;
    this.writer = writer;
  }

  @Override
  public void accept(final Fact fact) {
    if (fact.factType() != factType
        || !dataset.admits(fact.sourcePartition(), fact.sourcePosition())
        || !matchesFilters(fact)) {
      return;
    }
    final Object key = fact.get(projection.keyField());
    if (key == null) {
      return;
    }
    final List<Object> values = new ArrayList<>(projection.columns().size());
    for (final DimensionColumn column : projection.columns()) {
      values.add(fact.get(column.name()));
    }
    writer.upsertRow(projection, String.valueOf(key), values);
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

  @Override
  public void flush() {
    // rows are written eagerly on accept
  }

  @Override
  public void close() {
    // no resources of its own; the store's DataSource is owned by the shard
  }
}
