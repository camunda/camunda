/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dataset.store.Cell;
import io.camunda.analytics.dataset.store.DatasetFetch;
import io.camunda.analytics.dataset.store.DatasetQueryClient;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Elasticsearch {@link DatasetQueryClient}: transforms a neutral {@link DatasetFetch} into a
 * filtered search over the cube's {@code dataset_<id>} index — a {@code term} on the tier, a {@code
 * range} on {@code window_start}, and a {@code term} per grain-column filter — and maps each hit's
 * source back into a {@link Cell} (grain {@link DimensionKey} + window + still-encoded meter
 * blobs). The executor does the merge/finalize, so this stays a filter-and-fetch.
 */
public final class ElasticsearchDatasetQueryClient implements DatasetQueryClient {

  private static final int MAX_HITS = 10_000;

  private final ElasticsearchClient client;

  public ElasticsearchDatasetQueryClient(final ElasticsearchClient client) {
    this.client = client;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Cell> fetch(final DatasetFetch fetch) {
    final CompiledDataset dataset = fetch.dataset();
    final List<DimensionColumn> grain = dataset.grain().columns();

    final List<Query> filters = new ArrayList<>();
    filters.add(
        Query.of(
            q -> q.term(t -> t.field("window_size").value(FieldValue.of(fetch.windowSize())))));
    filters.add(
        Query.of(
            q ->
                q.range(
                    r ->
                        r.number(
                            n ->
                                n.field("window_start")
                                    .gte((double) fetch.fromMs())
                                    .lt((double) fetch.toMs())))));
    for (final FilterPredicate filter : fetch.filters()) {
      final int index = dataset.grain().indexOf(filter.field());
      if (index < 0) {
        continue; // only grain fields are stored and thus filterable at read time
      }
      final FieldValue value = fieldValue(grain.get(index).type(), filter.value());
      filters.add(Query.of(q -> q.term(t -> t.field(EsNames.field(filter.field())).value(value))));
    }

    final String index = EsNames.datasetIndex(dataset.cubeId());
    try {
      final SearchResponse<Map> response =
          client.search(
              s -> s.index(index).size(MAX_HITS).query(q -> q.bool(b -> b.filter(filters))),
              Map.class);
      final List<Cell> cells = new ArrayList<>();
      for (final Hit<Map> hit : response.hits().hits()) {
        final Map<String, Object> source = (Map<String, Object>) hit.source();
        if (source != null) {
          cells.add(toCell(dataset, grain, fetch.meters(), source));
        }
      }
      return cells;
    } catch (final IOException e) {
      throw new IllegalStateException("failed to query " + dataset.name(), e);
    }
  }

  @Override
  public void close() {
    // the client is owned by the store
  }

  private static Cell toCell(
      final CompiledDataset dataset,
      final List<DimensionColumn> grain,
      final List<String> meters,
      final Map<String, Object> source) {
    final List<Object> values = new ArrayList<>(grain.size());
    for (final DimensionColumn column : grain) {
      values.add(coerce(column.type(), source.get(EsNames.field(column.name()))));
    }
    final DimensionKey key = DimensionKey.of(dataset.grain(), values);
    final long windowStart = ((Number) source.get("window_start")).longValue();

    final Map<String, byte[]> accumulators = new LinkedHashMap<>();
    for (final String meter : meters) {
      final Object encoded = source.get(EsNames.field(meter));
      if (encoded != null) {
        accumulators.put(meter, EsNames.decode(encoded.toString()));
      }
    }
    return new Cell(key, windowStart, accumulators);
  }

  private static FieldValue fieldValue(final DimensionType type, final String value) {
    return switch (type) {
      case STRING -> FieldValue.of(value);
      case LONG, INT -> FieldValue.of(Long.parseLong(value));
      case BOOLEAN -> FieldValue.of(Boolean.parseBoolean(value));
    };
  }

  private static Object coerce(final DimensionType type, final Object value) {
    if (value == null) {
      return null;
    }
    return switch (type) {
      case STRING -> value.toString();
      case LONG -> ((Number) value).longValue();
      case INT -> ((Number) value).intValue();
      case BOOLEAN -> value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString());
    };
  }
}
