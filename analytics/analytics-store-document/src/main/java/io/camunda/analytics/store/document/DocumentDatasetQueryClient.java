/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dataset.store.Cell;
import io.camunda.analytics.dataset.store.DatasetFetch;
import io.camunda.analytics.dataset.store.DatasetQueryClient;
import io.camunda.analytics.dataset.store.TableFetch;
import io.camunda.analytics.dataset.store.TableRow;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.core.RequestBuilders;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOptionsBuilders;
import io.camunda.search.sort.SortOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        values.add(coerce(column.type(), source.get(DocumentCubeNames.field(column.name()))));
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
            coerce(column.type(), source.get(DocumentCubeNames.field(column.name()))));
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
          values.add(coerce(column.type(), source.get(DocumentCubeNames.field(column.name()))));
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

  @Override
  public void close() {
    // the client is owned by the store
  }

  private static SearchQuery term(
      final String field, final DimensionType type, final String value) {
    return switch (type) {
      case STRING -> SearchQueryBuilders.term(field, value);
      case LONG, INT -> SearchQueryBuilders.term(field, Long.parseLong(value));
      case BOOLEAN -> SearchQueryBuilders.term(field, Boolean.parseBoolean(value));
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

  /** A value-equal grouping key over the grain values and the window, to reassemble cells. */
  private record CellKey(List<Object> dimensions, long windowStart) {}
}
