/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Infrastructure wrapper around {@link OptimizeElasticsearchClient} for bulk-writing Zeebe records.
 *
 * <p>Provides index lifecycle management ({@link #ensureIndexExists}) and a stateful {@link
 * IndexBatch} accumulator that auto-flushes when the configured batch size is reached.
 */
class ZeebeBulkWriter {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeBulkWriter.class);
  private static final int MAX_RETRIES = 4;
  private static final long RETRY_BACKOFF_MS = 5_000L;

  private final OptimizeElasticsearchClient esClient;
  private final int batchSize;

  ZeebeBulkWriter(final OptimizeElasticsearchClient esClient, final int batchSize) {
    this.esClient = esClient;
    this.batchSize = batchSize;
  }

  /** Creates the index with dynamic mapping if it does not already exist. */
  void ensureIndexExists(final String index) {
    try {
      if (!indexExists(index)) {
        LOG.info("Index '{}' not found — creating with dynamic mapping.", index);
        createIndex(index);
      }
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Failed to check/create index: " + index, e);
    }
  }

  /** Returns a new empty {@link IndexBatch} targeting {@code index}. */
  IndexBatch newBatch(final String index) {
    return new IndexBatch(index);
  }

  /** Flushes {@code batch} when its accumulated ops reach the configured batch size. */
  void flushIfNeeded(final IndexBatch batch) {
    if (batch.pendingSize() >= batchSize) {
      flush(batch);
    }
  }

  /** Unconditionally flushes any remaining ops in {@code batch}. */
  void flush(final IndexBatch batch) {
    if (batch.isEmpty()) {
      return;
    }
    final int pendingSize = batch.pendingSize();
    final String index = batch.index();
    final long total = batch.totalCount();
    LOG.debug("Flushing {} records into '{}' (total so far: {}).", pendingSize, index, total);
    final List<BulkOperation> ops = batch.consume();
    bulkWrite(index, ops);
  }

  /** Writes {@code ops} directly to {@code index} and logs the count. */
  void write(final String index, final List<BulkOperation> ops) {
    bulkWrite(index, ops);
    LOG.info("Inserted {} records into '{}'.", ops.size(), index);
  }

  /** Returns {@code true} when {@code index} exists in Elasticsearch. */
  private boolean indexExists(final String index) throws IOException {
    return esClient.getEsClient().indices().exists(ExistsRequest.of(r -> r.index(index))).value();
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  /** Creates {@code index} in Elasticsearch with dynamic mapping. */
  private void createIndex(final String index) throws IOException {
    esClient.getEsClient().indices().create(CreateIndexRequest.of(r -> r.index(index)));
  }

  /**
   * Splits {@code ops} into slices of at most {@code batchSize} and sends each slice to
   * Elasticsearch.
   */
  private void bulkWrite(final String index, final List<BulkOperation> ops) {
    sliceOps(ops).forEach(slice -> sendBulkSlice(index, slice));
  }

  /**
   * Sends one pre-sized slice of bulk operations with exponential-backoff retries. A fresh {@link
   * BulkRequest} is built on each attempt so the request is not reused after a partial send.
   */
  private void sendBulkSlice(final String index, final List<BulkOperation> slice) {
    IOException lastException = null;
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        final BulkRequest request = buildBulkRequest(index, slice);
        final BulkResponse response = esClient.getEsClient().bulk(request);
        if (response.errors()) {
          final long failures =
              response.items().stream().filter(item -> item.error() != null).count();
          LOG.warn("{} bulk errors for index '{}'", failures, index);
        }
        return;
      } catch (final IOException e) {
        lastException = e;
        if (attempt < MAX_RETRIES) {
          final long delay = RETRY_BACKOFF_MS * attempt;
          LOG.warn(
              "Bulk insert attempt {}/{} failed for index '{}' ({}); retrying in {} ms",
              attempt,
              MAX_RETRIES,
              index,
              e.getMessage(),
              delay);
          try {
            Thread.sleep(delay);
          } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new OptimizeRuntimeException(
                "Interrupted during bulk retry for index: " + index, ie);
          }
        }
      }
    }
    throw new OptimizeRuntimeException(
        "Bulk insert failed after " + MAX_RETRIES + " attempts for index: " + index, lastException);
  }

  /** Builds a {@link BulkRequest} targeting {@code index} with the given operations. */
  private static BulkRequest buildBulkRequest(final String index, final List<BulkOperation> slice) {
    return BulkRequest.of(
        b -> {
          b.index(index);
          slice.forEach(b::operations);
          return b;
        });
  }

  /**
   * Partitions {@code ops} into consecutive sublists of at most {@code batchSize} elements. The
   * last sublist may be smaller.
   */
  private List<List<BulkOperation>> sliceOps(final List<BulkOperation> ops) {
    final int total = ops.size();
    return IntStream.range(0, (total + batchSize - 1) / batchSize)
        .mapToObj(
            i -> {
              final int start = i * batchSize;
              final int end = Math.min(start + batchSize, total);
              return ops.subList(start, end);
            })
        .toList();
  }

  /**
   * Accumulates bulk operations for a single Zeebe index and tracks the running flush total.
   *
   * <p>Callers interact exclusively through the typed mutator and accessor methods — the underlying
   * list is never exposed directly.
   */
  static final class IndexBatch {

    private final String index;
    private final List<BulkOperation> pendingOps = new ArrayList<>();
    private long flushedCount;

    IndexBatch(final String index) {
      this.index = index;
    }

    String index() {
      return index;
    }

    void addAll(final List<BulkOperation> ops) {
      pendingOps.addAll(ops);
    }

    boolean isEmpty() {
      return pendingOps.isEmpty();
    }

    int pendingSize() {
      return pendingOps.size();
    }

    /** Returns the total number of records written (flushed + pending). */
    long totalCount() {
      return flushedCount + pendingOps.size();
    }

    /** Returns only the flushed record count (i.e. after all flushes have completed). */
    long flushedCount() {
      return flushedCount;
    }

    /**
     * Drains all pending operations, records their count in {@code flushedCount}, and returns the
     * drained list for writing to Elasticsearch.
     */
    List<BulkOperation> consume() {
      final List<BulkOperation> drained = new ArrayList<>(pendingOps);
      flushedCount += pendingOps.size();
      pendingOps.clear();
      return drained;
    }
  }
}
