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
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.serving.spi.ServingWriteMetrics;
import io.camunda.analytics.serving.spi.WriteVersion;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.core.SearchBulkIndexRequest;
import io.camunda.search.clients.core.SearchBulkResponse;
import io.camunda.search.clients.core.SearchBulkResponse.Item;
import io.camunda.search.clients.core.SearchBulkResponse.ItemResult;
import io.camunda.search.clients.core.SearchDeleteRequest;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.clients.core.SearchWriteResponse;
import java.util.ArrayList;
import java.util.List;
import org.agrona.collections.MutableLong;
import org.junit.jupiter.api.Test;

/**
 * The document writer's write-path health signals, against a fake write client (no store): an
 * APPLIED bulk item counts as a row written for its dataset, a NOOP item as a fence rejection, one
 * batch-size sample per bulk request and one duration sample per flush.
 */
final class DocumentServingWriteMetricsTest {

  private static final long MINUTE = 60_000L;

  private final CompiledDataset dataset =
      new DatasetCompiler(
              MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()))
          .compile(
              1L,
              DatasetDeclaration.builder("pi-count", FactType.PROCESS_INSTANCE)
                  .dimension("bpmnProcessId", DimensionType.STRING)
                  .meter(Meter.of("count", MeterCatalog.COUNT))
                  .window(MINUTE)
                  .build());

  @Test
  void shouldCountAppliedItemsAsRowsWrittenAndNoopItemsAsFenced() {
    // given a client that fences the second document of each bulk
    final FakeWriteClient client = new FakeWriteClient(1);
    final RecordingMetrics metrics = new RecordingMetrics();
    final DocumentDatasetWriter writer = new DocumentDatasetWriter(client, metrics);

    // when two distinct cells flush as one bulk
    writer.upsertCell(dataset, key("orders"), 0L, MINUTE, count(10L), new WriteVersion(5, 100));
    writer.upsertCell(dataset, key("ship"), 0L, MINUTE, count(3L), new WriteVersion(5, 100));
    writer.flush();

    // then the applied item counts as a written row for this dataset, the fenced one as rejected,
    // and the flush recorded one batch-size sample and one duration sample
    assertThat(metrics.rowsWritten).containsExactly("pi-count");
    assertThat(metrics.fencedRejected).isEqualTo(1);
    assertThat(metrics.batchSizes).containsExactly(2);
    assertThat(metrics.writeDurations).hasSize(1);
  }

  @Test
  void shouldRecordNothingOnAnEmptyFlush() {
    // given a writer with nothing staged
    final RecordingMetrics metrics = new RecordingMetrics();
    final DocumentDatasetWriter writer =
        new DocumentDatasetWriter(new FakeWriteClient(Integer.MAX_VALUE), metrics);

    // when
    writer.flush();

    // then no meter moved — an empty flush is not a write
    assertThat(metrics.rowsWritten).isEmpty();
    assertThat(metrics.fencedRejected).isZero();
    assertThat(metrics.batchSizes).isEmpty();
    assertThat(metrics.writeDurations).isEmpty();
  }

  private DimensionKey key(final String process) {
    return DimensionKey.of(dataset.grain(), process);
  }

  private byte[] count(final long value) {
    return new CompositeAccumulatorValue(dataset.meterBounds())
        .toBytes(new Object[] {new MutableLong(value)});
  }

  /** Applies every bulk item except the one at {@code noopAt}, which reports NOOP (fenced). */
  private static final class FakeWriteClient implements DocumentBasedWriteClient {

    private final int noopAt;

    private FakeWriteClient(final int noopAt) {
      this.noopAt = noopAt;
    }

    @Override
    public <T> SearchWriteResponse index(final SearchIndexRequest<T> indexRequest) {
      throw new UnsupportedOperationException("bulk-only fake");
    }

    @Override
    public <T> SearchBulkResponse bulk(final SearchBulkIndexRequest<T> bulkRequest) {
      final List<Item> items = new ArrayList<>();
      final List<SearchIndexRequest<T>> requests = bulkRequest.items();
      for (int i = 0; i < requests.size(); i++) {
        final SearchIndexRequest<T> request = requests.get(i);
        items.add(
            new Item(
                request.id(),
                request.index(),
                i == noopAt ? ItemResult.NOOP : ItemResult.APPLIED,
                null));
      }
      return new SearchBulkResponse(items);
    }

    @Override
    public SearchWriteResponse delete(final SearchDeleteRequest deleteRequest) {
      return SearchWriteResponse.of(
          r ->
              r.id(deleteRequest.id())
                  .index(deleteRequest.index())
                  .result(SearchWriteResponse.Result.DELETED));
    }
  }

  /** A {@link ServingWriteMetrics} fake recording every signal. */
  private static final class RecordingMetrics implements ServingWriteMetrics {

    private final List<String> rowsWritten = new ArrayList<>();
    private final List<Long> writeDurations = new ArrayList<>();
    private final List<Integer> batchSizes = new ArrayList<>();
    private int fencedRejected;

    @Override
    public void rowWritten(final String datasetName) {
      rowsWritten.add(datasetName);
    }

    @Override
    public void fencedRejected() {
      fencedRejected++;
    }

    @Override
    public void writeDuration(final long durationNanos) {
      writeDurations.add(durationNanos);
    }

    @Override
    public void batchSize(final int size) {
      batchSizes.add(size);
    }
  }
}
