/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import co.elastic.clients.elasticsearch._helpers.bulk.BulkListener;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the outcome of async bulk flushes from a {@link
 * co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester}.
 *
 * <p>Called from the ingester's scheduler thread on completion. The exporter thread polls via {@link
 * #checkAndAdvancePosition()} to safely advance the exported position without cross-thread calls to
 * the controller.
 *
 * <p>With maxConcurrentRequests=1, bulks complete in order so positions advance contiguously (no
 * holes).
 */
public class AsyncFlushTracker implements BulkListener<Void> {

  private static final Logger LOG = LoggerFactory.getLogger(AsyncFlushTracker.class);

  // Written by ingester scheduler thread, read by exporter thread
  private final AtomicLong lastFlushedBulkId = new AtomicLong(-1);
  private final AtomicReference<Throwable> lastError = new AtomicReference<>();

  // Counters for metrics
  private final AtomicLong successCount = new AtomicLong();
  private final AtomicLong failureCount = new AtomicLong();

  @Override
  public void beforeBulk(
      final long executionId, final BulkRequest request, final List<Void> contexts) {
    LOG.debug(
        "Sending bulk request {} with {} operations", executionId, request.operations().size());
  }

  @Override
  public void afterBulk(
      final long executionId,
      final BulkRequest request,
      final List<Void> contexts,
      final BulkResponse response) {
    if (response.errors()) {
      final var errorItems =
          response.items().stream()
              .filter(item -> item.error() != null)
              .collect(Collectors.toList());
      final var errorMsg = formatErrors(errorItems);
      LOG.error("Bulk request {} completed with errors: {}", executionId, errorMsg);
      lastError.set(new RuntimeException("Bulk request " + executionId + " had errors: " + errorMsg));
      failureCount.incrementAndGet();
    } else {
      LOG.debug("Bulk request {} completed successfully", executionId);
      lastFlushedBulkId.set(executionId);
      successCount.incrementAndGet();
    }
  }

  @Override
  public void afterBulk(
      final long executionId,
      final BulkRequest request,
      final List<Void> contexts,
      final Throwable failure) {
    LOG.error("Bulk request {} failed", executionId, failure);
    lastError.set(failure);
    failureCount.incrementAndGet();
  }

  /**
   * Called from the exporter thread. If the last async bulk failed, clears and returns the error. If
   * it succeeded, returns null.
   *
   * @return the error from the last failed bulk, or null if no error
   */
  public Throwable checkError() {
    return lastError.getAndSet(null);
  }

  /**
   * Returns true if at least one bulk has completed successfully since the last check.
   */
  public boolean hasCompletedFlush() {
    return lastFlushedBulkId.get() >= 0;
  }

  public long getSuccessCount() {
    return successCount.get();
  }

  public long getFailureCount() {
    return failureCount.get();
  }

  private String formatErrors(final List<BulkResponseItem> errorItems) {
    return errorItems.stream()
        .limit(10)
        .map(
            item ->
                String.format(
                    "[index=%s, id=%s, type=%s, reason=%s]",
                    item.index(),
                    item.id(),
                    item.error() != null ? item.error().type() : "unknown",
                    item.error() != null ? item.error().reason() : "unknown"))
        .collect(Collectors.joining(", "));
  }
}
