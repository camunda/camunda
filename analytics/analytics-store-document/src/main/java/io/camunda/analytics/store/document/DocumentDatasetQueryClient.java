/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.meter.Agg;
import io.camunda.analytics.meter.PushdownColumn;
import io.camunda.analytics.meter.PushdownSpec;
import io.camunda.analytics.serving.spi.AggregatedFetch;
import io.camunda.analytics.serving.spi.AggregatedRow;
import io.camunda.analytics.serving.spi.Cell;
import io.camunda.analytics.serving.spi.DatasetFetch;
import io.camunda.analytics.serving.spi.DatasetQueryClient;
import io.camunda.analytics.serving.spi.TableFetch;
import io.camunda.analytics.serving.spi.TableRow;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchAggregatorBuilders;
import io.camunda.search.clients.aggregator.SearchCompositeAggregator;
import io.camunda.search.clients.aggregator.SearchDateHistogramAggregator.DateHistogramInterval;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.clients.core.RequestBuilders;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOptionsBuilders;
import io.camunda.search.sort.SortOrder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The document serving {@link DatasetQueryClient}: transforms a neutral {@link DatasetFetch} into a
 * filtered search over the cube's {@code dataset_<id>} index (term on the tier, range on {@code
 * window_start}, term per grain-column filter), then groups the per-meter documents back into
 * {@link Cell}s (grain {@link DimensionKey} + window + the requested meters' still-encoded
 * accumulators). The executor does the merge/finalize, so this stays a filter-and-fetch.
 */
public final class DocumentDatasetQueryClient implements DatasetQueryClient {

  private static final int MAX_HITS = 10_000;
  private static final int PAGE_SIZE = 1_000;

  private final DocumentBasedSearchClient searchClient;

  public DocumentDatasetQueryClient(final DocumentBasedSearchClient searchClient) {
    this.searchClient = searchClient;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Cell> fetch(final DatasetFetch fetch) {
    final CompiledDataset dataset = fetch.dataset();
    final List<DimensionColumn> grain = dataset.grain().columns();

    final List<SearchQuery> filters = new ArrayList<>();
    filters.add(SearchQueryBuilders.term(DocumentCubeNames.WINDOW_SIZE, fetch.windowSize()));
    filters.add(SearchQueryBuilders.gte(DocumentCubeNames.WINDOW_START, fetch.fromMs()));
    filters.add(SearchQueryBuilders.lt(DocumentCubeNames.WINDOW_START, fetch.toMs()));
    for (final FilterPredicate filter : fetch.filters()) {
      final int index = dataset.grain().indexOf(filter.field());
      if (index >= 0) {
        filters.add(
            term(DocumentCubeNames.field(filter.field()), grain.get(index).type(), filter.value()));
      }
    }
    final SearchQuery query = SearchQueryBuilders.and(filters);
    final String index = DocumentCubeNames.datasetIndex(dataset.cubeId());

    final SearchQueryResponse<Map> response =
        searchClient.search(
            RequestBuilders.searchRequest(r -> r.index(index).query(query).size(MAX_HITS)),
            Map.class);

    // Each hit is one meter-document; group them back into cells by (grain values, window).
    final Map<CellKey, Map<String, byte[]>> cells = new LinkedHashMap<>();
    final Map<CellKey, List<Object>> keyValues = new LinkedHashMap<>();
    for (final var hit : response.hits()) {
      final Map<String, Object> source = (Map<String, Object>) hit.source();
      if (source == null) {
        continue;
      }
      final String meter = String.valueOf(source.get(DocumentCubeNames.METER_NAME));
      if (!fetch.meters().contains(meter)) {
        continue;
      }
      final List<Object> values = new ArrayList<>(grain.size());
      for (final DimensionColumn column : grain) {
        values.add(column.type().coerce(source.get(DocumentCubeNames.field(column.name()))));
      }
      final long windowStart = ((Number) source.get(DocumentCubeNames.WINDOW_START)).longValue();
      final CellKey cellKey = new CellKey(values, windowStart);
      keyValues.putIfAbsent(cellKey, values);
      cells
          .computeIfAbsent(cellKey, k -> new LinkedHashMap<>())
          .put(
              meter,
              DocumentCubeNames.decode(String.valueOf(source.get(DocumentCubeNames.ACCUMULATOR))));
    }

    final List<Cell> result = new ArrayList<>(cells.size());
    cells.forEach(
        (cellKey, accumulators) ->
            result.add(
                new Cell(
                    DimensionKey.of(dataset.grain(), keyValues.get(cellKey)),
                    cellKey.windowStart(),
                    accumulators)));
    return result;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<TableRow> fetchRows(final TableFetch fetch) {
    final CompiledTable table = fetch.table();
    final List<DimensionColumn> columns = table.columns();

    final List<SearchQuery> filters = new ArrayList<>();
    for (final FilterPredicate filter : fetch.filters()) {
      final DimensionColumn column = column(columns, filter.field());
      if (column != null) {
        filters.add(term(DocumentCubeNames.field(filter.field()), column.type(), filter.value()));
      }
    }
    final SearchQuery query =
        filters.isEmpty() ? SearchQueryBuilders.matchAll() : SearchQueryBuilders.and(filters);
    final String index = DocumentCubeNames.rowIndex(table.cubeId());
    final int limit = fetch.limit();

    // A single search page caps at MAX_HITS; past that the client scrolls (paginates internally and
    // returns every match), which we then bound to the requested limit.
    // TODO(analytics): scroll materializes all matching rows before the truncation — fine for the
    // bounded reads today, but a very large limit over a large table should page with search_after
    // (SearchQueryRequest supports sort + searchAfter) to keep memory bounded to the limit.
    final SearchQueryResponse<Map> response =
        limit <= MAX_HITS
            ? searchClient.search(
                RequestBuilders.searchRequest(r -> r.index(index).query(query).size(limit)),
                Map.class)
            : searchClient.scroll(
                RequestBuilders.searchRequest(r -> r.index(index).query(query)), Map.class);

    final List<TableRow> rows = new ArrayList<>();
    for (final var hit : response.hits()) {
      if (rows.size() >= limit) {
        break;
      }
      final Map<String, Object> source = (Map<String, Object>) hit.source();
      if (source == null) {
        continue;
      }
      final Map<String, Object> values = new LinkedHashMap<>();
      for (final DimensionColumn column : columns) {
        values.put(
            column.name(),
            column.type().coerce(source.get(DocumentCubeNames.field(column.name()))));
      }
      rows.add(new TableRow(values));
    }
    return rows;
  }

  private static DimensionColumn column(final List<DimensionColumn> columns, final String name) {
    for (final DimensionColumn column : columns) {
      if (column.name().equals(name)) {
        return column;
      }
    }
    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void streamCells(final DatasetFetch fetch, final Consumer<Cell> sink) {
    final CompiledDataset dataset = fetch.dataset();
    final List<DimensionColumn> grain = dataset.grain().columns();

    final List<SearchQuery> filters = new ArrayList<>();
    filters.add(SearchQueryBuilders.term(DocumentCubeNames.WINDOW_SIZE, fetch.windowSize()));
    filters.add(SearchQueryBuilders.gte(DocumentCubeNames.WINDOW_START, fetch.fromMs()));
    filters.add(SearchQueryBuilders.lt(DocumentCubeNames.WINDOW_START, fetch.toMs()));
    for (final FilterPredicate filter : fetch.filters()) {
      final int index = dataset.grain().indexOf(filter.field());
      if (index >= 0) {
        filters.add(
            term(DocumentCubeNames.field(filter.field()), grain.get(index).type(), filter.value()));
      }
    }
    final SearchQuery query = SearchQueryBuilders.and(filters);
    final String index = DocumentCubeNames.datasetIndex(dataset.cubeId());
    final List<SearchSortOptions> sort =
        List.of(SortOptionsBuilders.sortOptions(DocumentCubeNames.DOC_KEY, SortOrder.ASC));

    // search_after paging (stateless): sort on the unique doc key, resume from the last page's sort
    // value. One document = one meter of one cell → emit a single-meter Cell; the executor merges
    // per (group, meter), so no cross-page grouping is needed.
    Object[] after = null;
    while (true) {
      final Object[] resumeFrom = after;
      final SearchQueryResponse<Map> response =
          searchClient.search(
              RequestBuilders.searchRequest(
                  r -> {
                    r.index(index).query(query).size(PAGE_SIZE).sort(sort);
                    if (resumeFrom != null) {
                      r.searchAfter(resumeFrom);
                    }
                    return r;
                  }),
              Map.class);
      final var hits = response.hits();
      if (hits.isEmpty()) {
        break;
      }
      for (final var hit : hits) {
        final Map<String, Object> source = (Map<String, Object>) hit.source();
        if (source == null) {
          continue;
        }
        final String meter = String.valueOf(source.get(DocumentCubeNames.METER_NAME));
        if (!fetch.meters().contains(meter)) {
          continue;
        }
        final List<Object> values = new ArrayList<>(grain.size());
        for (final DimensionColumn column : grain) {
          values.add(column.type().coerce(source.get(DocumentCubeNames.field(column.name()))));
        }
        final long windowStart = ((Number) source.get(DocumentCubeNames.WINDOW_START)).longValue();
        final byte[] accumulator =
            DocumentCubeNames.decode(String.valueOf(source.get(DocumentCubeNames.ACCUMULATOR)));
        sink.accept(
            new Cell(
                DimensionKey.of(dataset.grain(), values), windowStart, Map.of(meter, accumulator)));
      }
      after = hits.get(hits.size() - 1).sortValues();
      if (hits.size() < PAGE_SIZE) {
        break;
      }
    }
  }

  private static final String COMPOSITE_NAME = "cube";
  private static final String BUCKET_SOURCE = "wbucket";

  /**
   * The pushed-down / direct read on the document backend: one paged {@code composite} aggregation
   * per meter (documents are one-per-meter, so each meter is filtered by {@code meter_name} and
   * carries its own numeric fields). The composite sources are a {@code terms} per group-by
   * dimension plus a {@code date_histogram} on {@code window_start} at the granularity; the sub-
   * aggregations are the meter's {@code sum}/{@code min}/{@code max} columns (additive) or a {@code
   * sum} over its {@code _value} (a sketch's DIRECT scalar). Rows are unioned across meters on
   * {@code (group, bucket)} and each meter's columns recomposed into its result.
   */
  @Override
  @SuppressWarnings("unchecked")
  public List<AggregatedRow> fetchAggregated(final AggregatedFetch fetch) {
    final CompiledDataset dataset = fetch.dataset();
    final Map<GroupBucket, Map<String, Object>> merged = new LinkedHashMap<>();
    final Map<GroupBucket, List<Object>> groupValues = new LinkedHashMap<>();
    final String index = DocumentCubeNames.datasetIndex(dataset.cubeId());
    final List<String> sourceNames = sourceNames(fetch.groupBy());

    for (final String meter : fetch.meters()) {
      final Optional<PushdownSpec<?, ?>> spec = specFor(dataset, meter, fetch.windowSize());
      final SearchQuery query = SearchQueryBuilders.and(meterFilters(fetch, meter));
      final List<SearchAggregator> subAggs = subAggregations(spec);

      String after = null;
      while (true) {
        final String cursor = after;
        final SearchCompositeAggregator composite =
            SearchAggregatorBuilders.composite()
                .name(COMPOSITE_NAME)
                .size(PAGE_SIZE)
                .sources(compositeSources(fetch.groupBy(), fetch.granularityMs()))
                .aggregations(subAggs)
                .after(cursor)
                .build();
        final SearchQueryResponse<Map> response =
            searchClient.search(
                RequestBuilders.searchRequest(
                    r -> r.index(index).query(query).size(0).aggregations(composite)),
                Map.class);
        final AggregationResult result =
            response.aggregations() == null ? null : response.aggregations().get(COMPOSITE_NAME);
        final Map<String, AggregationResult> buckets =
            result == null ? null : result.aggregations();
        if (buckets == null || buckets.isEmpty()) {
          break;
        }
        buckets.forEach(
            (key, bucket) -> {
              final Map<String, String> keyValues =
                  SearchCompositeAggregator.splitKeyValues(key, sourceNames.toArray(new String[0]));
              final long bucketStart = Long.parseLong(keyValues.get(BUCKET_SOURCE));
              final List<Object> group = new ArrayList<>(fetch.groupBy().size());
              for (final String dim : fetch.groupBy()) {
                final int dimIndex = dataset.grain().indexOf(dim);
                group.add(
                    dataset
                        .grain()
                        .columns()
                        .get(dimIndex)
                        .type()
                        .coerce(keyValues.get(DocumentCubeNames.field(dim))));
              }
              final GroupBucket gb = new GroupBucket(group, bucketStart);
              groupValues.putIfAbsent(gb, group);
              merged
                  .computeIfAbsent(gb, g -> new LinkedHashMap<>())
                  .put(meter, measure(spec, bucket));
            });
        after = result.endCursor();
        if (after == null) {
          break;
        }
      }
    }

    final List<AggregatedRow> rows = new ArrayList<>(merged.size());
    merged.forEach(
        (gb, measures) -> rows.add(new AggregatedRow(groupValues.get(gb), gb.bucket(), measures)));
    return rows;
  }

  /** The base filters plus the per-meter {@code meter_name} term. */
  private static List<SearchQuery> meterFilters(final AggregatedFetch fetch, final String meter) {
    final CompiledDataset dataset = fetch.dataset();
    final List<DimensionColumn> grain = dataset.grain().columns();
    final List<SearchQuery> filters = new ArrayList<>();
    filters.add(SearchQueryBuilders.term(DocumentCubeNames.WINDOW_SIZE, fetch.windowSize()));
    filters.add(SearchQueryBuilders.gte(DocumentCubeNames.WINDOW_START, fetch.fromMs()));
    filters.add(SearchQueryBuilders.lt(DocumentCubeNames.WINDOW_START, fetch.toMs()));
    filters.add(SearchQueryBuilders.term(DocumentCubeNames.METER_NAME, meter));
    for (final FilterPredicate filter : fetch.filters()) {
      final int index = dataset.grain().indexOf(filter.field());
      if (index >= 0) {
        filters.add(
            term(DocumentCubeNames.field(filter.field()), grain.get(index).type(), filter.value()));
      }
    }
    return filters;
  }

  /** The composite sources: a terms per group-by dimension, then the time bucket. */
  private static List<SearchAggregator> compositeSources(
      final List<String> groupBy, final long granularityMs) {
    final List<SearchAggregator> sources = new ArrayList<>();
    for (final String dim : groupBy) {
      final String field = DocumentCubeNames.field(dim);
      sources.add(SearchAggregatorBuilders.terms(field, field));
    }
    sources.add(
        SearchAggregatorBuilders.dateHistogram(
            BUCKET_SOURCE,
            DocumentCubeNames.WINDOW_START,
            new DateHistogramInterval.Fixed(Duration.ofMillis(granularityMs))));
    return sources;
  }

  /**
   * The per-column {@code sum}/{@code min}/{@code max} (additive) or {@code sum(_value)} (sketch).
   */
  private static List<SearchAggregator> subAggregations(final Optional<PushdownSpec<?, ?>> spec) {
    final List<SearchAggregator> subs = new ArrayList<>();
    if (spec.isPresent()) {
      for (final PushdownColumn column : spec.get().columns()) {
        final String field = DocumentCubeNames.pushdownField(column.suffix());
        subs.add(metricAgg(subAggName(field), field, column.agg()));
      }
    } else {
      subs.add(
          SearchAggregatorBuilders.sum(
              subAggName(DocumentCubeNames.VALUE), DocumentCubeNames.VALUE));
    }
    return subs;
  }

  private Object measure(final Optional<PushdownSpec<?, ?>> spec, final AggregationResult bucket) {
    if (spec.isPresent()) {
      final List<Object> columns = new ArrayList<>();
      for (final PushdownColumn column : spec.get().columns()) {
        columns.add(subValue(bucket, DocumentCubeNames.pushdownField(column.suffix())));
      }
      return recompose(spec.get(), columns);
    }
    final Object value = subValue(bucket, DocumentCubeNames.VALUE);
    return value == null ? 0.0 : ((Number) value).doubleValue();
  }

  private static Object subValue(final AggregationResult bucket, final String field) {
    if (bucket.aggregations() == null) {
      return null;
    }
    final AggregationResult metric = bucket.aggregations().get(subAggName(field));
    return metric == null ? null : metric.docCount();
  }

  private static Object recompose(final PushdownSpec<?, ?> spec, final List<Object> columns) {
    return spec.recompose().apply(columns);
  }

  private static String subAggName(final String field) {
    return "agg_" + field;
  }

  private static SearchAggregator metricAgg(final String name, final String field, final Agg agg) {
    return switch (agg) {
      case SUM -> SearchAggregatorBuilders.sum(name, field);
      case MIN -> SearchAggregatorBuilders.min(name, field);
      case MAX -> SearchAggregatorBuilders.max(name, field);
    };
  }

  private static List<String> sourceNames(final List<String> groupBy) {
    final List<String> names = new ArrayList<>();
    for (final String dim : groupBy) {
      names.add(DocumentCubeNames.field(dim));
    }
    names.add(BUCKET_SOURCE);
    return names;
  }

  private static Optional<PushdownSpec<?, ?>> specFor(
      final CompiledDataset dataset, final String meter, final long windowSize) {
    // Tier-independent (ADR 0009): a meter's pushdown spec is the same at every tier; windowSize
    // only selected which tier's documents were read.
    for (final CompiledMeter compiled : dataset.meters()) {
      if (compiled.meterName().equals(meter)) {
        return compiled.pushdown();
      }
    }
    throw new IllegalStateException(
        "no compiled meter '" + meter + "' in '" + dataset.name() + "'");
  }

  @Override
  public void close() {
    // the client is owned by the store
  }

  /** A value-equal (group values, time bucket) key to union rows across per-meter composites. */
  private record GroupBucket(List<Object> group, long bucket) {}

  private static SearchQuery term(
      final String field, final DimensionType type, final String value) {
    return switch (type) {
      case STRING, TEXT -> SearchQueryBuilders.term(field, value);
      case LONG, INT -> SearchQueryBuilders.term(field, Long.parseLong(value));
      case BOOLEAN -> SearchQueryBuilders.term(field, Boolean.parseBoolean(value));
    };
  }

  /** A value-equal grouping key over the grain values and the window, to reassemble cells. */
  private record CellKey(List<Object> dimensions, long windowStart) {}
}
