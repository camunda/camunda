/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import io.camunda.zeebe.exporter.api.ExporterException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.WillCloseWhenClosed;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch.core.ClearScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest.Builder;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;

public class OpensearchRepository implements AutoCloseable {

  private static final Time SCROLL_KEEP_ALIVE = Time.of(t -> t.time("1m"));
  private static final int SCROLL_PAGE_SIZE = 100;
  protected final OpenSearchAsyncClient client;
  protected final Executor executor;
  protected final Logger logger;

  public OpensearchRepository(
      @WillCloseWhenClosed final OpenSearchAsyncClient client,
      final Executor executor,
      final Logger logger) {
    this.client = client;
    this.executor = executor;
    this.logger = logger;
  }

  /**
   * Variant of {@link #fetchUnboundedDocumentCollection(Builder, Class, Function)} to use when you
   * don't care about the source document, meaning you won't be using any deserialization
   * functionality.
   */
  public <T> CompletionStage<Collection<T>> fetchUnboundedDocumentCollection(
      final Builder requestBuilder, final Function<Hit<Object>, T> transformer) {
    return fetchUnboundedDocumentCollection(requestBuilder, Object.class, transformer);
  }

  public <TDocument, TResult> CompletionStage<Collection<TResult>> fetchUnboundedDocumentCollection(
      final Builder requestBuilder,
      final Class<TDocument> type,
      final Function<Hit<TDocument>, TResult> transformer) {
    final var request =
        requestBuilder
            .allowNoIndices(true)
            .scroll(SCROLL_KEEP_ALIVE)
            .size(SCROLL_PAGE_SIZE)
            .build();

    try {
      return client
          .search(request, type)
          .thenComposeAsync(
              r -> {
                try {
                  return clearScrollOnComplete(
                      r.scrollId(),
                      scrollDocuments(
                          r.hits().hits(), r.scrollId(), new ArrayList<>(), transformer, type));
                } catch (final Exception e) {
                  // scrollDocuments may fail, in which case we still want to clear the scroll
                  // anyway
                  // we don't need to do this later on however, since at this point our async
                  // pipeline
                  // is set up already to clear it
                  return clearScroll(r.scrollId(), null, e);
                }
              },
              executor);
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private <T> CompletionStage<T> clearScrollOnComplete(
      final String scrollId, final CompletionStage<T> scrollOperation) {
    return scrollOperation
        // we combine `handleAsync` and `thenComposeAsync` to emulate the behavior of a try/finally
        // so we always clear the scroll even if the future is already failed
        .handleAsync((result, error) -> clearScroll(scrollId, result, error), executor)
        .thenComposeAsync(Function.identity(), executor);
  }

  private <T> CompletableFuture<T> clearScroll(
      final String scrollId, final T result, final Throwable error) {
    final var request = new ClearScrollRequest.Builder().scrollId(scrollId).build();
    final CompletionStage<T> endResult =
        error != null
            ? CompletableFuture.failedFuture(error)
            : CompletableFuture.completedFuture(result);
    try {
      return client
          .clearScroll(request)
          .exceptionallyAsync(
              clearError -> {
                logger.warn(
                    """
                        Failed to clear scroll context; this could eventually lead to \
                        increased resource usage in Elastic""",
                    clearError);

                return null;
              },
              executor)
          .thenComposeAsync(ignored -> endResult);
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private <TResult, TDocument> CompletionStage<Collection<TDocument>> scrollDocuments(
      final List<Hit<TResult>> hits,
      final String scrollId,
      final List<TDocument> accumulator,
      final Function<Hit<TResult>, TDocument> transformer,
      final Class<TResult> type) {
    if (hits.isEmpty()) {
      return CompletableFuture.completedFuture(accumulator);
    }

    for (final var hit : hits) {
      accumulator.add(transformer.apply(hit));
    }

    try {
      return client
          .scroll(r -> r.scrollId(scrollId).scroll(SCROLL_KEEP_ALIVE), type)
          .thenComposeAsync(
              r -> scrollDocuments(r.hits().hits(), r.scrollId(), accumulator, transformer, type));
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  public Throwable collectBulkErrors(final List<BulkResponseItem> items) {
    final var collectedErrors = new ArrayList<String>();
    items.stream()
        .flatMap(item -> Optional.ofNullable(item.error()).stream())
        .collect(Collectors.groupingBy(ErrorCause::type))
        .forEach(
            (type, errors) ->
                collectedErrors.add(
                    String.format(
                        "Failed to update %d item(s) of bulk update [type: %s, reason: %s]",
                        errors.size(), type, errors.getFirst().reason())));

    return new ExporterException("Failed to flush bulk request: " + collectedErrors);
  }

  @Override
  public void close() throws Exception {
    client._transport().close();
  }
}
