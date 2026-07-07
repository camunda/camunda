/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.serving.spi.Cell;
import io.camunda.analytics.serving.spi.DatasetFetch;
import io.camunda.analytics.serving.spi.TableFetch;
import io.camunda.analytics.serving.spi.TableRow;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchGetResponse;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

/**
 * Pins the read behavior of {@link DocumentDatasetQueryClient} against a canned {@link
 * DocumentBasedSearchClient}: how per-meter documents are reassembled into {@link Cell}s, how
 * source values are coerced to the declared dimension types, the search_after paging of {@code
 * streamCells}, and the search-vs-scroll choice (plus the limit bound) of {@code fetchRows} —
 * mirroring the query-client coverage of the RDBMS store test.
 */
final class DocumentDatasetQueryClientTest {

  private static final long MINUTE = 60_000L;
  private static final int PAGE_SIZE = 1_000;
  private static final int MAX_HITS = 10_000;

  private final FakeSearchClient searchClient = new FakeSearchClient();
  private final DocumentDatasetQueryClient client = new DocumentDatasetQueryClient(searchClient);
  private final CompiledDataset dataset =
      compiler()
          .compile(
              1L,
              DatasetDeclaration.builder("pi-count", FactType.PROCESS_INSTANCE)
                  .dimension("bpmnProcessId", DimensionType.STRING)
                  .dimension("version", DimensionType.LONG)
                  .meter(Meter.of("count", MeterCatalog.COUNT))
                  .window(MINUTE)
                  .build());

  private static DatasetCompiler compiler() {
    return new DatasetCompiler(
        MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()));
  }

  @Test
  void shouldReassembleMeterDocumentsIntoCells() {
    // given one document per (cell, meter): two meters of the same cell, a second window of one
    // grain value, a second grain value, and a document of a meter the fetch does not request
    searchClient.onSearch(
        response(
            hit(meterDoc("orders", 3, 0L, "count", new byte[] {1})),
            hit(meterDoc("orders", 3, 0L, "level", new byte[] {2})),
            hit(meterDoc("orders", 3, MINUTE, "count", new byte[] {3})),
            hit(meterDoc("ship", 7, 0L, "count", new byte[] {4})),
            hit(meterDoc("orders", 3, 0L, "other", new byte[] {9}))));

    // when fetched over the whole range
    final List<Cell> cells =
        client.fetch(
            new DatasetFetch(
                dataset, MINUTE, 0L, 2 * MINUTE, List.of(), List.of("count", "level")));

    // then the per-meter documents regroup into one cell per (grain values, window), the requested
    // meters' accumulators are Base64-decoded, and the unrequested meter is dropped
    assertThat(cells)
        .extracting(
            c -> c.key().get("bpmnProcessId"), c -> c.key().get("version"), Cell::windowStart)
        .containsExactlyInAnyOrder(
            Tuple.tuple("orders", 3L, 0L),
            Tuple.tuple("orders", 3L, MINUTE),
            Tuple.tuple("ship", 7L, 0L));
    final Cell merged =
        cells.stream()
            .filter(c -> c.windowStart() == 0L && "orders".equals(c.key().get(0)))
            .findFirst()
            .orElseThrow();
    assertThat(merged.accumulators())
        .containsOnlyKeys("count", "level")
        .satisfies(
            accumulators -> {
              assertThat(accumulators.get("count")).containsExactly(1);
              assertThat(accumulators.get("level")).containsExactly(2);
            });
    assertThat(searchClient.searchRequests).hasSize(1);
    assertThat(searchClient.searchRequests.get(0).index()).containsExactly("dataset_1");
  }

  @Test
  void shouldCoerceGrainValuesToDeclaredTypes() {
    // given a document whose numeric grain value deserialized as an Integer (as JSON numbers do)
    searchClient.onSearch(response(hit(meterDoc("orders", 3, 0L, "count", new byte[] {1}))));

    // when fetched
    final List<Cell> cells =
        client.fetch(new DatasetFetch(dataset, MINUTE, 0L, MINUTE, List.of(), List.of("count")));

    // then the LONG dimension comes back as a Long, not the raw Integer
    assertThat(cells)
        .singleElement()
        .satisfies(c -> assertThat(c.key().get("version")).isEqualTo(3L));
  }

  @Test
  void shouldStreamCellsAcrossSearchAfterPages() {
    // given a full first page (PAGE_SIZE hits) and a final short page
    final List<SearchQueryHit<Map>> firstPage = new ArrayList<>();
    for (int i = 0; i < PAGE_SIZE; i++) {
      firstPage.add(
          hit(meterDoc("orders", 3, i * MINUTE, "count", new byte[] {(byte) i}), "key" + i));
    }
    searchClient.onSearch(response(firstPage));
    searchClient.onSearch(response(hit(meterDoc("ship", 7, 0L, "count", new byte[] {42}), "last")));

    // when streamed
    final List<Cell> cells = new ArrayList<>();
    client.streamCells(
        new DatasetFetch(dataset, MINUTE, 0L, 2000 * MINUTE, List.of(), List.of("count")),
        cells::add);

    // then every document arrives as a single-meter cell and the second request resumed from the
    // first page's last sort value
    assertThat(cells).hasSize(PAGE_SIZE + 1);
    assertThat(cells.get(0).accumulators()).containsOnlyKeys("count");
    assertThat(cells.get(PAGE_SIZE).key().get("bpmnProcessId")).isEqualTo("ship");
    assertThat(searchClient.searchRequests).hasSize(2);
    assertThat(searchClient.searchRequests.get(0).searchAfter()).isNull();
    assertThat(searchClient.searchRequests.get(1).searchAfter()).containsExactly("key999");
  }

  @Test
  void shouldFetchTableRowsWithTypedColumnValues() {
    // given raw rows whose source values carry the loose JSON types (Integer numbers, a boolean
    // that deserialized as a String)
    final CompiledTable table = rawInstancesTable();
    searchClient.onSearch(
        response(
            hit(rowDoc("order", 1_500, 2, Boolean.TRUE)), hit(rowDoc("ship", 42_000, 0, "true"))));

    // when fetched within a single page
    final List<TableRow> rows = client.fetchRows(new TableFetch(table, List.of(), 100));

    // then each column is coerced to its declared type
    assertThat(rows)
        .extracting(
            r -> r.values().get("bpmnProcessId"),
            r -> r.values().get("durationMs"),
            r -> r.values().get("retries"),
            r -> r.values().get("active"))
        .containsExactly(
            Tuple.tuple("order", 1_500L, 2, true), Tuple.tuple("ship", 42_000L, 0, true));
    assertThat(searchClient.searchRequests).hasSize(1);
    assertThat(searchClient.searchRequests.get(0).index()).containsExactly("projection_7");
    assertThat(searchClient.scrollRequests).isEmpty();
  }

  @Test
  void shouldBoundFetchedRowsToTheRequestedLimit() {
    // given more matching rows than the limit
    final CompiledTable table = rawInstancesTable();
    searchClient.onSearch(
        response(
            hit(rowDoc("a", 1, 0, true)),
            hit(rowDoc("b", 2, 0, true)),
            hit(rowDoc("c", 3, 0, true))));

    // when fetched with a smaller limit
    final List<TableRow> rows = client.fetchRows(new TableFetch(table, List.of(), 2));

    // then only limit rows come back
    assertThat(rows).hasSize(2);
  }

  @Test
  void shouldScrollWhenLimitExceedsASinglePage() {
    // given a limit beyond the single-search cap
    final CompiledTable table = rawInstancesTable();
    searchClient.onScroll(response(hit(rowDoc("order", 1_500, 2, true))));

    // when fetched
    final List<TableRow> rows = client.fetchRows(new TableFetch(table, List.of(), MAX_HITS + 1));

    // then the client scrolls instead of searching and still maps the rows
    assertThat(rows)
        .singleElement()
        .satisfies(r -> assertThat(r.values().get("bpmnProcessId")).isEqualTo("order"));
    assertThat(searchClient.searchRequests).isEmpty();
    assertThat(searchClient.scrollRequests).hasSize(1);
  }

  private static CompiledTable rawInstancesTable() {
    return compiler()
        .compileTable(
            7L,
            DatasetDeclaration.builder("raw-instances", FactType.PROCESS_INSTANCE)
                .asTable("processInstanceKey")
                .dimension("bpmnProcessId", DimensionType.STRING)
                .dimension("durationMs", DimensionType.LONG)
                .dimension("retries", DimensionType.INT)
                .dimension("active", DimensionType.BOOLEAN)
                .build());
  }

  private static Map<String, Object> meterDoc(
      final String process,
      final int version,
      final long windowStart,
      final String meter,
      final byte[] accumulator) {
    final Map<String, Object> source = new LinkedHashMap<>();
    source.put("bpmnProcessId", process);
    source.put("version", version); // an Integer, as JSON numbers deserialize
    source.put(DocumentCubeNames.WINDOW_START, windowStart);
    source.put(DocumentCubeNames.WINDOW_SIZE, MINUTE);
    source.put(DocumentCubeNames.METER_NAME, meter);
    source.put(DocumentCubeNames.ACCUMULATOR, DocumentCubeNames.encode(accumulator));
    return source;
  }

  private static Map<String, Object> rowDoc(
      final String process, final int durationMs, final int retries, final Object active) {
    final Map<String, Object> source = new LinkedHashMap<>();
    source.put("bpmnProcessId", process);
    source.put("durationMs", durationMs);
    source.put("retries", retries);
    source.put("active", active);
    return source;
  }

  @SafeVarargs
  private static SearchQueryResponse<Map> response(final SearchQueryHit<Map>... hits) {
    return response(List.of(hits));
  }

  private static SearchQueryResponse<Map> response(final List<SearchQueryHit<Map>> hits) {
    return new SearchQueryResponse.Builder<Map>().totalHits(hits.size()).hits(hits).build();
  }

  private static SearchQueryHit<Map> hit(
      final Map<String, Object> source, final Object... sortValues) {
    return new SearchQueryHit.Builder<Map>()
        .id("doc")
        .source(source)
        .sortValues(sortValues.length == 0 ? null : sortValues)
        .build();
  }

  /** A canned {@link DocumentBasedSearchClient} that records requests and replays responses. */
  private static final class FakeSearchClient implements DocumentBasedSearchClient {

    private final Deque<SearchQueryResponse<Map>> searchResponses = new ArrayDeque<>();
    private final Deque<SearchQueryResponse<Map>> scrollResponses = new ArrayDeque<>();
    private final List<SearchQueryRequest> searchRequests = new ArrayList<>();
    private final List<SearchQueryRequest> scrollRequests = new ArrayList<>();

    void onSearch(final SearchQueryResponse<Map> response) {
      searchResponses.add(response);
    }

    void onScroll(final SearchQueryResponse<Map> response) {
      scrollResponses.add(response);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> SearchQueryResponse<T> search(
        final SearchQueryRequest searchRequest, final Class<T> documentClass) {
      searchRequests.add(searchRequest);
      if (searchResponses.isEmpty()) {
        throw new IllegalStateException("no canned search response left");
      }
      return (SearchQueryResponse<T>) searchResponses.poll();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> SearchQueryResponse<T> scroll(
        final SearchQueryRequest searchRequest, final Class<T> documentClass) {
      scrollRequests.add(searchRequest);
      if (scrollResponses.isEmpty()) {
        throw new IllegalStateException("no canned scroll response left");
      }
      return (SearchQueryResponse<T>) scrollResponses.poll();
    }

    @Override
    public <T> SearchGetResponse<T> get(
        final SearchGetRequest getRequest, final Class<T> documentClass) {
      throw new UnsupportedOperationException("not used by the query client");
    }

    @Override
    public void close() {}
  }
}
