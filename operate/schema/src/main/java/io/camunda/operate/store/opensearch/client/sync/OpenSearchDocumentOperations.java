/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.client.sync;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.ids;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.clearScrollRequest;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.deleteByQueryRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.deleteRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.getRequest;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.scrollRequest;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.time;
import static java.lang.String.format;

import io.camunda.operate.opensearch.ExtendedOpenSearchClient;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.opensearch.client.OpenSearchFailedShardsException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.ShardFailure;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.slf4j.Logger;

public class OpenSearchDocumentOperations extends OpenSearchRetryOperation {
  public static final String SCROLL_KEEP_ALIVE_MS = "60000ms";
  // this scroll timeout value is used for reindex and delete q
  public static final String INTERNAL_SCROLL_KEEP_ALIVE_MS = "30000ms";
  public static final int TERMS_AGG_SIZE = 10000;
  public static final int TOPHITS_AGG_SIZE = 100;

  public OpenSearchDocumentOperations(
      final Logger logger, final OpenSearchClient openSearchClient) {
    super(logger, openSearchClient);
  }

  private static Function<Exception, String> defaultSearchErrorMessage(final String index) {
    return e -> format("Failed to search index: %s! Reason: %s", index, e.getMessage());
  }

  private static String defaultDeleteErrorMessage(final String index) {
    return format("Failed to delete index: %s", index);
  }

  private static String defaultPersistErrorMessage(final String index) {
    return format("Failed to persist index: %s", index);
  }

  private void clearScroll(final String scrollId) {
    if (scrollId != null) {
      try {
        openSearchClient.clearScroll(clearScrollRequest(scrollId));
      } catch (final Exception e) {
        logger.warn("Error occurred when clearing the scroll with id [{}]", scrollId);
      }
    }
  }

  private void checkFailedShards(final SearchRequest request, final SearchResponse<?> response) {
    if (!response.shards().failures().isEmpty()) {
      throw new OpenSearchFailedShardsException(
          format(
              "Shards failed executing request (indices=%s, failed shards=%s)",
              request.index(),
              response.shards().failures().stream().map(this::formatShardFailure).toList()));
    }
  }

  private String formatShardFailure(final ShardFailure failure) {
    return String.format(
        "ShardFailure[index=%s, shard=%s, status=%s, node=%s, reason=%s]",
        failure.index(),
        failure.shard(),
        failure.status(),
        failure.node(),
        formatErrorCause(failure.reason()));
  }

  private String formatErrorCause(final ErrorCause errorCause) {
    if (errorCause == null) {
      return "";
    }
    final StringBuilder details = new StringBuilder();
    details.append("type=").append(errorCause.type());
    if (errorCause.reason() != null) {
      details.append(", reason=").append(errorCause.reason());
    }
    if (errorCause.causedBy() != null) {
      details.append(", causedBy=[").append(formatErrorCause(errorCause.causedBy())).append("]");
    }
    return details.toString();
  }

  public <R> Map<String, Aggregate> unsafeScrollWith(
      final SearchRequest.Builder searchRequestBuilder,
      final Consumer<List<Hit<R>>> hitsConsumer,
      final Consumer<HitsMetadata<R>> hitsMetadataConsumer,
      final Class<R> clazz,
      final boolean retry)
      throws IOException {
    final var request = searchRequestBuilder.scroll(time(SCROLL_KEEP_ALIVE_MS)).build();

    return retry
        ? executeWithRetries(() -> scrollWith(request, hitsConsumer, hitsMetadataConsumer, clazz))
        : scrollWith(request, hitsConsumer, hitsMetadataConsumer, clazz);
  }

  private <R> Map<String, Aggregate> scrollWith(
      final SearchRequest request,
      final Consumer<List<Hit<R>>> hitsConsumer,
      final Consumer<HitsMetadata<R>> hitsMetadataConsumer,
      final Class<R> clazz)
      throws IOException {
    String scrollId = null;

    try {
      SearchResponse<R> response = openSearchClient.search(request, clazz);
      final var aggregates = response.aggregations();

      if (hitsMetadataConsumer != null) {
        hitsMetadataConsumer.accept(response.hits());
      }

      scrollId = response.scrollId();
      List<Hit<R>> hits = response.hits().hits();

      while (!hits.isEmpty() && scrollId != null) {
        checkFailedShards(request, response);

        if (hitsConsumer != null) {
          hitsConsumer.accept(hits);
        }

        response = openSearchClient.scroll(scrollRequest(scrollId), clazz);
        scrollId = response.scrollId();
        hits = response.hits().hits();
      }

      return aggregates;
    } finally {
      if (scrollId != null) {
        clearScroll(scrollId);
      }
    }
  }

  private <R> Map<String, Aggregate> safeScrollWith(
      final SearchRequest.Builder requestBuilder,
      final Class<R> entityClass,
      final Consumer<List<Hit<R>>> hitsConsumer) {
    return safeScrollWith(requestBuilder, entityClass, hitsConsumer, null);
  }

  private <R> Map<String, Aggregate> safeScrollWith(
      final SearchRequest.Builder requestBuilder,
      final Class<R> entityClass,
      final Consumer<List<Hit<R>>> hitsConsumer,
      final Consumer<HitsMetadata<R>> hitsMetadataConsumer) {
    return safe(
        () ->
            unsafeScrollWith(
                requestBuilder, hitsConsumer, hitsMetadataConsumer, entityClass, false),
        defaultSearchErrorMessage(getIndex(requestBuilder)));
  }

  private <R> AggregatedResult<R> scroll(
      final SearchRequest.Builder searchRequestBuilder, final Class<R> clazz, final boolean retry)
      throws IOException {
    final var result = scrollHits(searchRequestBuilder, clazz, retry);
    return new AggregatedResult<>(
        result.values().stream().map(Hit::source).toList(), result.aggregates());
  }

  public <R> AggregatedResult<Hit<R>> scrollHits(
      final SearchRequest.Builder searchRequestBuilder, final Class<R> clazz) throws IOException {
    final List<Hit<R>> result = new ArrayList<>();
    final var aggregates =
        unsafeScrollWith(searchRequestBuilder, result::addAll, null, clazz, false);
    return new AggregatedResult<>(result, aggregates);
  }

  public <R> AggregatedResult<Hit<R>> scrollHits(
      final SearchRequest.Builder searchRequestBuilder, final Class<R> clazz, final boolean retry)
      throws IOException {
    final List<Hit<R>> result = new ArrayList<>();
    final var aggregates =
        unsafeScrollWith(searchRequestBuilder, result::addAll, null, clazz, retry);
    return new AggregatedResult<>(result, aggregates);
  }

  public <R> void scrollWith(
      final SearchRequest.Builder requestBuilder,
      final Class<R> entityClass,
      final Consumer<List<Hit<R>>> hitsConsumer) {
    safeScrollWith(requestBuilder, entityClass, hitsConsumer);
  }

  public <R> AggregatedResult<R> scrollValuesAndAggregations(
      final SearchRequest.Builder requestBuilder, final Class<R> entityClass) {
    return safe(
        () -> scroll(requestBuilder, entityClass, false),
        defaultSearchErrorMessage(getIndex(requestBuilder)));
  }

  public <R> AggregatedResult<R> scrollValuesAndAggregations(
      final SearchRequest.Builder requestBuilder, final Class<R> entityClass, final boolean retry) {
    return safe(
        () -> scroll(requestBuilder, entityClass, retry),
        defaultSearchErrorMessage(getIndex(requestBuilder)));
  }

  public <R> List<R> scrollValues(
      final SearchRequest.Builder requestBuilder, final Class<R> entityClass) {
    return scrollValuesAndAggregations(requestBuilder, entityClass).values();
  }

  public <R> List<R> scrollValues(
      final SearchRequest.Builder requestBuilder, final Class<R> entityClass, final boolean retry) {
    return scrollValuesAndAggregations(requestBuilder, entityClass, retry).values();
  }

  private <R> SearchResponse<R> unsafeSearch(
      final SearchRequest request, final Class<R> entityClass) throws IOException {
    final var response = openSearchClient.search(request, entityClass);
    checkFailedShards(request, response);
    return response;
  }

  public <R> SearchResponse<R> search(
      final SearchRequest.Builder requestBuilder, final Class<R> entityClass) {
    return search(requestBuilder, entityClass, false);
  }

  public <R> SearchResponse<R> search(
      final SearchRequest.Builder requestBuilder, final Class<R> entityClass, final boolean retry) {
    final var request = requestBuilder.build();
    return retry
        ? executeWithRetries(() -> unsafeSearch(request, entityClass))
        : safe(
            () -> unsafeSearch(request, entityClass),
            defaultSearchErrorMessage(getIndex(requestBuilder)));
  }

  public <R> List<R> searchValues(
      final SearchRequest.Builder requestBuilder, final Class<R> entityClass) {
    return searchValues(requestBuilder, entityClass, false);
  }

  public <R> List<R> searchValues(
      final SearchRequest.Builder requestBuilder, final Class<R> entityClass, final boolean retry) {
    return search(requestBuilder, entityClass, retry).hits().hits().stream()
        .map(Hit::source)
        .toList();
  }

  public Map<String, Aggregate> searchAggregations(final SearchRequest.Builder requestBuilder) {
    requestBuilder.size(0);
    return search(requestBuilder, Void.class).aggregations();
  }

  public <R> R searchUnique(
      final SearchRequest.Builder requestBuilder, final Class<R> entityClass, final String key) {
    final SearchResponse<R> response = search(requestBuilder, entityClass);

    if (response.hits().total().value() == 1) {
      return response.hits().hits().get(0).source();
    } else if (response.hits().total().value() > 1) {
      throw new NotFoundException(
          format("Could not find unique %s with key '%s'.", getIndex(requestBuilder), key));
    } else {
      throw new NotFoundException(
          format("Could not find %s with key '%s'.", getIndex(requestBuilder), key));
    }
  }

  public long docCount(final SearchRequest.Builder requestBuilder) {
    requestBuilder.size(0);
    return search(requestBuilder, Void.class).hits().total().value();
  }

  public Map<String, String> getIndexNames(final String index, final Collection<String> ids) {
    final Map<String, String> result = new HashMap<>();
    final var searchRequestBuilder =
        new SearchRequest.Builder().index(index).query(ids(ids)).source(s -> s.fetch(false));

    final Consumer<List<Hit<Void>>> hitsConsumer =
        hits -> hits.forEach(hit -> result.put(hit.id(), hit.index()));

    safeScrollWith(searchRequestBuilder, Void.class, hitsConsumer);

    return result;
  }

  public <R> Optional<R> getWithRetries(
      final String index, final String id, final Class<R> entitiyClass) {
    return executeWithRetries(
        () -> {
          final GetResponse<R> response = openSearchClient.get(getRequest(index, id), entitiyClass);
          return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
        });
  }

  public DeleteResponse delete(final String index, final String id) {
    final var deleteRequestBuilder = new DeleteRequest.Builder().index(index).id(id);

    return safe(
        () -> openSearchClient.delete(deleteRequestBuilder.build()),
        e -> defaultDeleteErrorMessage(index));
  }

  public DeleteByQueryResponse delete(final String index, final String field, final String value) {
    final var deleteRequestBuilder =
        new DeleteByQueryRequest.Builder().index(index).query(term(field, value));

    return safe(
        () -> openSearchClient.deleteByQuery(deleteRequestBuilder.build()),
        e -> defaultDeleteErrorMessage(index));
  }

  public long deleteByQuery(final String index, final Query query) {
    return executeWithRetries(
        () -> {
          final DeleteByQueryRequest request =
              deleteByQueryRequestBuilder(index).query(query).build();
          final DeleteByQueryResponse response = openSearchClient.deleteByQuery(request);
          return response.deleted();
        });
  }

  public <A> IndexResponse index(final IndexRequest.Builder<A> requestBuilder) {
    return safe(
        () -> openSearchClient.index(requestBuilder.build()),
        e -> defaultPersistErrorMessage(getIndex(requestBuilder)));
  }

  public <A> boolean indexWithRetries(final IndexRequest.Builder<A> requestBuilder) {
    final IndexRequest<A> indexRequest = requestBuilder.build();
    return executeWithRetries(
        () -> {
          final IndexResponse response = openSearchClient.index(indexRequest);
          return List.of(Result.Created, Result.Updated).contains(response.result());
        });
  }

  public <A> UpdateResponse<Void> update(
      final UpdateRequest.Builder<Void, A> requestBuilder,
      final Function<Exception, String> errorMessageSupplier) {
    return safe(
        () -> openSearchClient.update(requestBuilder.build(), Void.class), errorMessageSupplier);
  }

  public <R> SearchResponse<R> fixedSearch(final SearchRequest request, final Class<R> classR) {
    if (openSearchClient instanceof final ExtendedOpenSearchClient extendedOpenSearchClient) {
      return safe(
          () -> extendedOpenSearchClient.fixedSearch(request, classR),
          e -> defaultDeleteErrorMessage(request.index().toString()));
    } else {
      throw new UnsupportedOperationException(
          "ExtendedOpenSearchClient is required to execute fixedSearch! Provided: "
              + openSearchClient.getClass().getName());
    }
  }

  public Map<String, Object> searchAsMap(final SearchRequest.Builder requestBuilder) {
    final var request = requestBuilder.size(0).build();
    if (openSearchClient instanceof final ExtendedOpenSearchClient extendedOpenSearchClient) {
      return safe(
          () -> extendedOpenSearchClient.searchAsMap(request),
          e -> defaultDeleteErrorMessage(request.index().toString()));
    } else {
      throw new UnsupportedOperationException(
          "ExtendedOpenSearchClient is required to execute fixedSearch! Provided: "
              + openSearchClient.getClass().getName());
    }
  }

  public record AggregatedResult<R>(List<R> values, Map<String, Aggregate> aggregates) {}
}
