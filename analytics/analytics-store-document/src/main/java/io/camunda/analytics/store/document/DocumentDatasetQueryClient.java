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
import io.camunda.analytics.serving.spi.SnapshotPoint;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The document serving {@link DatasetQueryClient}: transforms a neutral {@link DatasetFetch} into a
 * filtered search over the cube's {@code dataset_<id>} index (term on the tier, range on {@code
 * window_start}, term per grain-column filter). A document is one whole cell (ADR 0009), so a hit
 * maps straight to a {@link Cell} (grain {@link DimensionKey} + window + the requested meters'
 * still-encoded accumulators). The executor does the merge/finalize, so this stays a
 * filter-and-fetch.
 */
public final class DocumentDatasetQueryClient implements DatasetQueryClient {

  private static final int MAX_HITS = 10_000;
  private static final int PAGE_SIZE = 1_000;

  private final DocumentBasedSearchClient searchClient;

  public DocumentDatasetQueryClient(final DocumentBasedSearchClient searchClient) {
    this.searchClient = searchClient;
  }

  @Override
  public List<Cell> fetch(final DatasetFetch fetch) {
    final CompiledDataset dataset = fetch.dataset();
    final SearchQuery query = SearchQueryBuilders.and(cellFilters(fetch));
    final String index = DocumentCubeNames.datasetIndex(dataset.cubeId());

    final SearchQueryResponse<Map> response =
        searchClient.search(
            RequestBuilders.searchRequest(r -> r.index(index).query(query).size(MAX_HITS)),
            Map.class);

    final List<Cell> cells = new ArrayList<>(response.hits().size());
    for (final var hit : response.hits()) {
      final Map<String, Object> source = source(hit.source());
      if (source != null) {
        cells.add(toCell(dataset, fetch.meters(), source));
      }
    }
    return cells;
  }

  @Override
  public void streamCells(final DatasetFetch fetch, final Consumer<Cell> sink) {
    final CompiledDataset dataset = fetch.dataset();
    final SearchQuery query = SearchQueryBuilders.and(cellFilters(fetch));
    final String index = DocumentCubeNames.datasetIndex(dataset.cubeId());

    // search_after paging (stateless): sort on the unique doc key, resume from the last page's
    // sort value. One document = one whole cell, so each hit emits one complete Cell.
    forEachPage(index, query, source -> sink.accept(toCell(dataset, fetch.meters(), source)));
  }

  private static final String COMPOSITE_NAME = "cube";
  private static final String BUCKET_SOURCE = "wbucket";

  /**
   * The pushed-down / direct read on the document backend (mirroring the RDBMS client): {@code
   * PUSH_DOWN} runs one paged {@code composite} aggregation — a {@code terms} source per group-by
   * dimension (with {@code missing_bucket}, so a null-dimension group surfaces like SQL's NULL
   * group) plus a {@code date_histogram} on {@code window_start} at the granularity — with the
   * meters' {@code sum}/{@code min}/{@code max} column sub-aggregations. {@code DIRECT} fetches the
   * cell documents themselves with no aggregation — one row per cell, a sketch meter serving its
   * denormalized finalized value. Each meter's columns are recomposed into its read-facing result.
   */
  @Override
  public List<AggregatedRow> fetchAggregated(final AggregatedFetch fetch) {
    return switch (fetch.strategy()) {
      case PUSH_DOWN -> pushDown(fetch);
      case DIRECT -> direct(fetch);
      case STREAM_MERGE ->
          throw new IllegalArgumentException("STREAM_MERGE streams cells, not an AggregatedFetch");
    };
  }

  private List<AggregatedRow> pushDown(final AggregatedFetch fetch) {
    final CompiledDataset dataset = fetch.dataset();
    final String index = DocumentCubeNames.datasetIndex(dataset.cubeId());
    final SearchQuery query = SearchQueryBuilders.and(aggregatedFilters(fetch));

    // One composite pass serves every meter: a cell document carries all meter columns (ADR 0009),
    // so the sub-aggregations are simply the union of the requested meters' columns.
    final List<SearchAggregator> subAggs = new ArrayList<>();
    for (final String meter : fetch.meters()) {
      final PushdownSpec<?, ?> spec = requireSpec(dataset, meter, fetch.windowSize());
      for (final PushdownColumn column : spec.columns()) {
        final String field = DocumentCubeNames.pushdownField(meter, column.suffix());
        subAggs.add(metricAgg(subAggName(field), field, column.agg()));
      }
    }

    final List<AggregatedRow> rows = new ArrayList<>();
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
      final Map<String, AggregationResult> buckets = result == null ? null : result.aggregations();
      if (buckets == null || buckets.isEmpty()) {
        break;
      }
      for (final AggregationResult bucket : buckets.values()) {
        // The composite bucket's structured key carries each source's raw value — the time
        // bucket as a number and each dimension as its own entry (null for a missing bucket) —
        // so nothing is parsed out of a joined string.
        final Map<String, Object> keyValues = bucket.keyValues();
        final long bucketStart = ((Number) keyValues.get(BUCKET_SOURCE)).longValue();
        final List<Object> group = new ArrayList<>(fetch.groupBy().size());
        for (final String dim : fetch.groupBy()) {
          final int dimIndex = dataset.grain().indexOf(dim);
          final DimensionType type = dataset.grain().columns().get(dimIndex).type();
          group.add(type.coerce(keyValues.get(DocumentCubeNames.field(dim))));
        }
        final Map<String, Object> measures = new LinkedHashMap<>();
        for (final String meter : fetch.meters()) {
          final PushdownSpec<?, ?> spec = requireSpec(dataset, meter, fetch.windowSize());
          final List<Object> columns = new ArrayList<>(spec.columns().size());
          for (final PushdownColumn column : spec.columns()) {
            columns.add(subValue(bucket, DocumentCubeNames.pushdownField(meter, column.suffix())));
          }
          measures.put(meter, recompose(spec, columns));
        }
        rows.add(new AggregatedRow(group, bucketStart, measures));
      }
      after = result.endCursor();
      if (after == null) {
        break;
      }
    }
    return rows;
  }

  private List<AggregatedRow> direct(final AggregatedFetch fetch) {
    final CompiledDataset dataset = fetch.dataset();
    final String index = DocumentCubeNames.datasetIndex(dataset.cubeId());
    final SearchQuery query = SearchQueryBuilders.and(aggregatedFilters(fetch));

    // No aggregation: one row per cell document, the bucket derived like the executor derives it.
    final List<AggregatedRow> rows = new ArrayList<>();
    forEachPage(
        index,
        query,
        source -> {
          final long windowStart = longField(source, DocumentCubeNames.WINDOW_START);
          final long bucket = windowStart - Math.floorMod(windowStart, fetch.granularityMs());
          final List<Object> group = new ArrayList<>(fetch.groupBy().size());
          for (final String dim : fetch.groupBy()) {
            final int dimIndex = dataset.grain().indexOf(dim);
            final DimensionType type = dataset.grain().columns().get(dimIndex).type();
            group.add(type.coerce(source.get(DocumentCubeNames.field(dim))));
          }
          final Map<String, Object> measures = new LinkedHashMap<>();
          for (final String meter : fetch.meters()) {
            final Optional<PushdownSpec<?, ?>> spec = specFor(dataset, meter, fetch.windowSize());
            if (spec.isPresent()) {
              final List<Object> columns = new ArrayList<>(spec.get().columns().size());
              for (final PushdownColumn column : spec.get().columns()) {
                columns.add(source.get(DocumentCubeNames.pushdownField(meter, column.suffix())));
              }
              measures.put(meter, recompose(spec.get(), columns));
            } else {
              // DIRECT on a non-pushable meter: serve the denormalized scalar. A null scalar
              // means "no observations": the measure stays absent — never 0, a legitimate value.
              final Object value = source.get(DocumentCubeNames.valueField(meter));
              if (value != null) {
                measures.put(meter, ((Number) value).doubleValue());
              }
            }
          }
          rows.add(new AggregatedRow(group, bucket, measures));
        });
    return rows;
  }

  @Override
  public List<SnapshotPoint> snapshotBaseline(final CompiledDataset dataset, final long atMs) {
    final List<DimensionColumn> grain = dataset.grain().columns();
    final String index = DocumentCubeNames.snapshotIndex(dataset.cubeId());
    final SearchQuery query = SearchQueryBuilders.lte(DocumentCubeNames.SAMPLE_TIME, atMs);

    if (grain.isEmpty()) {
      // One global key: its baseline is simply the newest row at-or-before the time.
      final SearchQueryResponse<Map> response =
          searchClient.search(
              RequestBuilders.searchRequest(
                  r ->
                      r.index(index)
                          .query(query)
                          .size(1)
                          .sort(
                              List.of(
                                  SortOptionsBuilders.sortOptions(
                                      DocumentCubeNames.SAMPLE_TIME, SortOrder.DESC)))),
              Map.class);
      final List<SnapshotPoint> points = new ArrayList<>(1);
      for (final var hit : response.hits()) {
        final Map<String, Object> source = source(hit.source());
        if (source != null) {
          points.add(toSnapshotPoint(dataset, source));
        }
      }
      return points;
    }

    // Per key, the newest sample_time <= atMs — O(keys), never O(history): a paged composite over
    // the grain (missing_bucket, so null-dimension keys survive) with a max(sample_time)
    // sub-aggregation, then one bounded id-lookup per page for the actual rows (the snapshot doc
    // id is deterministic over key + boundary, so the winning rows are addressable directly).
    final List<SearchAggregator> subAggs =
        List.of(
            SearchAggregatorBuilders.max(
                subAggName(DocumentCubeNames.SAMPLE_TIME), DocumentCubeNames.SAMPLE_TIME));
    final List<SearchAggregator> sources = new ArrayList<>(grain.size());
    for (final DimensionColumn column : grain) {
      final String field = DocumentCubeNames.field(column.name());
      sources.add(SearchAggregatorBuilders.terms(field, field, true));
    }

    final List<SnapshotPoint> points = new ArrayList<>();
    String after = null;
    while (true) {
      final String cursor = after;
      final SearchCompositeAggregator composite =
          SearchAggregatorBuilders.composite()
              .name(COMPOSITE_NAME)
              .size(PAGE_SIZE)
              .sources(sources)
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
      final Map<String, AggregationResult> buckets = result == null ? null : result.aggregations();
      if (buckets == null || buckets.isEmpty()) {
        break;
      }
      final List<String> docIds = new ArrayList<>(buckets.size());
      for (final AggregationResult bucket : buckets.values()) {
        final Map<String, Object> keyValues = bucket.keyValues();
        final List<Object> values = new ArrayList<>(grain.size());
        for (final DimensionColumn column : grain) {
          values.add(column.type().coerce(keyValues.get(DocumentCubeNames.field(column.name()))));
        }
        final long newest = metricLong(bucket, subAggName(DocumentCubeNames.SAMPLE_TIME));
        docIds.add(
            DocumentCubeNames.snapshotDocId(DimensionKey.of(dataset.grain(), values), newest));
      }
      final SearchQueryResponse<Map> winners =
          searchClient.search(
              RequestBuilders.searchRequest(
                  r -> r.index(index).query(SearchQueryBuilders.ids(docIds)).size(docIds.size())),
              Map.class);
      for (final var hit : winners.hits()) {
        final Map<String, Object> source = source(hit.source());
        if (source != null) {
          points.add(toSnapshotPoint(dataset, source));
        }
      }
      after = result.endCursor();
      if (after == null) {
        break;
      }
    }
    return points;
  }

  @Override
  public List<SnapshotPoint> snapshotRange(
      final CompiledDataset dataset, final long fromMs, final long toMs) {
    final String index = DocumentCubeNames.snapshotIndex(dataset.cubeId());
    final SearchQuery query =
        SearchQueryBuilders.and(
            SearchQueryBuilders.gt(DocumentCubeNames.SAMPLE_TIME, fromMs),
            SearchQueryBuilders.lte(DocumentCubeNames.SAMPLE_TIME, toMs));

    final List<SnapshotPoint> points = new ArrayList<>();
    forEachPage(index, query, source -> points.add(toSnapshotPoint(dataset, source)));
    // Ordered by key then time (the contract shared with the RDBMS backend, whose ORDER BY does
    // this in the store); nulls group first so a null-dimension key stays one contiguous run.
    points.sort(
        Comparator.comparing(SnapshotPoint::keyValues, DocumentDatasetQueryClient::compareKeys)
            .thenComparingLong(SnapshotPoint::sampleTime));
    return points;
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
      final Map<String, Object> source = source(hit.source());
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

  @Override
  public void close() {
    // the client is owned by the store
  }

  /** The tier + window-range + grain-filter query shared by the cell reads. */
  private static List<SearchQuery> cellFilters(final DatasetFetch fetch) {
    return cellFilters(
        fetch.dataset(), fetch.windowSize(), fetch.fromMs(), fetch.toMs(), fetch.filters());
  }

  private static List<SearchQuery> aggregatedFilters(final AggregatedFetch fetch) {
    return cellFilters(
        fetch.dataset(), fetch.windowSize(), fetch.fromMs(), fetch.toMs(), fetch.filters());
  }

  private static List<SearchQuery> cellFilters(
      final CompiledDataset dataset,
      final long windowSize,
      final long fromMs,
      final long toMs,
      final List<FilterPredicate> predicates) {
    final List<DimensionColumn> grain = dataset.grain().columns();
    final List<SearchQuery> filters = new ArrayList<>();
    filters.add(SearchQueryBuilders.term(DocumentCubeNames.WINDOW_SIZE, windowSize));
    filters.add(SearchQueryBuilders.gte(DocumentCubeNames.WINDOW_START, fromMs));
    filters.add(SearchQueryBuilders.lt(DocumentCubeNames.WINDOW_START, toMs));
    for (final FilterPredicate filter : predicates) {
      final int index = dataset.grain().indexOf(filter.field());
      if (index >= 0) {
        filters.add(
            term(DocumentCubeNames.field(filter.field()), grain.get(index).type(), filter.value()));
      }
    }
    return filters;
  }

  /** One whole cell from one document: coerced grain values, window, requested meter blobs. */
  private static Cell toCell(
      final CompiledDataset dataset, final List<String> meters, final Map<String, Object> source) {
    final List<DimensionColumn> grain = dataset.grain().columns();
    final List<Object> values = new ArrayList<>(grain.size());
    for (final DimensionColumn column : grain) {
      values.add(column.type().coerce(source.get(DocumentCubeNames.field(column.name()))));
    }
    final long windowStart = longField(source, DocumentCubeNames.WINDOW_START);
    // Only sketch/summary meters store a blob (the STREAM_MERGE representation); an additive
    // meter is pushdown-only, so there is nothing to decode for it here (as on the RDBMS backend).
    final Map<String, byte[]> accumulators = new LinkedHashMap<>();
    for (final String meter : meters) {
      final Object blob = source.get(DocumentCubeNames.blobField(meter));
      if (blob != null) {
        accumulators.put(meter, DocumentCubeNames.decode(blob.toString()));
      }
    }
    return new Cell(DimensionKey.of(dataset.grain(), values), windowStart, accumulators);
  }

  /** One snapshot row from one document (ADR 0010): key values, boundary, recomposed measures. */
  private static SnapshotPoint toSnapshotPoint(
      final CompiledDataset dataset, final Map<String, Object> source) {
    final List<DimensionColumn> grain = dataset.grain().columns();
    final List<Object> keyValues = new ArrayList<>(grain.size());
    for (final DimensionColumn column : grain) {
      keyValues.add(column.type().coerce(source.get(DocumentCubeNames.field(column.name()))));
    }
    final long sampleTime = longField(source, DocumentCubeNames.SAMPLE_TIME);
    final Map<String, Object> measures = new LinkedHashMap<>();
    for (final CompiledMeter meter : dataset.meters()) {
      final PushdownSpec<?, ?> spec = meter.pushdown().orElseThrow();
      final List<Object> columns = new ArrayList<>(spec.columns().size());
      for (final PushdownColumn column : spec.columns()) {
        columns.add(
            source.get(DocumentCubeNames.pushdownField(meter.meterName(), column.suffix())));
      }
      measures.put(meter.meterName(), recompose(spec, columns));
    }
    return new SnapshotPoint(keyValues, sampleTime, measures);
  }

  /** search_after pages over the unique doc key, feeding each hit's source to the consumer. */
  @SuppressWarnings("unchecked")
  private void forEachPage(
      final String index, final SearchQuery query, final Consumer<Map<String, Object>> consumer) {
    final List<SearchSortOptions> sort =
        List.of(SortOptionsBuilders.sortOptions(DocumentCubeNames.DOC_KEY, SortOrder.ASC));
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
        final Map<String, Object> source = source(hit.source());
        if (source != null) {
          consumer.accept(source);
        }
      }
      after = hits.get(hits.size() - 1).sortValues();
      if (hits.size() < PAGE_SIZE) {
        break;
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> source(final Object raw) {
    return (Map<String, Object>) raw;
  }

  private static long longField(final Map<String, Object> source, final String field) {
    return ((Number) source.get(field)).longValue();
  }

  /** A metric sub-aggregation's value as an exact long (e.g. a max over epoch-millis longs). */
  private static long metricLong(final AggregationResult bucket, final String name) {
    final AggregationResult metric = bucket.aggregations().get(name);
    return metric.value() != null ? (long) (double) metric.value() : metric.docCount();
  }

  /** A metric sub-aggregation's numeric value, preferring the exact double over the rounding. */
  private static Object subValue(final AggregationResult bucket, final String field) {
    if (bucket.aggregations() == null) {
      return null;
    }
    final AggregationResult metric = bucket.aggregations().get(subAggName(field));
    if (metric == null) {
      return null;
    }
    return metric.value() != null ? metric.value() : metric.docCount();
  }

  @SuppressWarnings("unchecked")
  private static Object recompose(final PushdownSpec<?, ?> spec, final List<Object> columns) {
    return ((PushdownSpec<Object, Object>) spec).recompose().apply(columns);
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

  /**
   * The composite sources: a terms per group-by dimension — with {@code missing_bucket}, so cells
   * whose dimension is null form their own group instead of silently vanishing (the RDBMS GROUP BY
   * returns the NULL group) — then the time bucket.
   */
  private static List<SearchAggregator> compositeSources(
      final List<String> groupBy, final long granularityMs) {
    final List<SearchAggregator> sources = new ArrayList<>();
    for (final String dim : groupBy) {
      final String field = DocumentCubeNames.field(dim);
      sources.add(SearchAggregatorBuilders.terms(field, field, true));
    }
    sources.add(
        SearchAggregatorBuilders.dateHistogram(
            BUCKET_SOURCE,
            DocumentCubeNames.WINDOW_START,
            new DateHistogramInterval.Fixed(Duration.ofMillis(granularityMs))));
    return sources;
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

  private static PushdownSpec<?, ?> requireSpec(
      final CompiledDataset dataset, final String meter, final long windowSize) {
    return specFor(dataset, meter, windowSize)
        .orElseThrow(
            () ->
                new IllegalArgumentException("meter '" + meter + "' is not pushable (PUSH_DOWN)"));
  }

  private static DimensionColumn column(final List<DimensionColumn> columns, final String name) {
    for (final DimensionColumn column : columns) {
      if (column.name().equals(name)) {
        return column;
      }
    }
    return null;
  }

  private static SearchQuery term(
      final String field, final DimensionType type, final String value) {
    return switch (type) {
      case STRING, TEXT -> SearchQueryBuilders.term(field, value);
      case LONG, INT -> SearchQueryBuilders.term(field, Long.parseLong(value));
      case BOOLEAN -> SearchQueryBuilders.term(field, Boolean.parseBoolean(value));
    };
  }

  /** Key-then-time ordering support: per-dimension natural order, a null dimension first. */
  private static int compareKeys(final List<Object> a, final List<Object> b) {
    for (int i = 0; i < a.size(); i++) {
      final int comparison = compareValues(a.get(i), b.get(i));
      if (comparison != 0) {
        return comparison;
      }
    }
    return 0;
  }

  @SuppressWarnings("unchecked")
  private static int compareValues(final Object a, final Object b) {
    if (a == null) {
      return b == null ? 0 : -1;
    }
    if (b == null) {
      return 1;
    }
    return ((Comparable<Object>) a).compareTo(b);
  }
}
