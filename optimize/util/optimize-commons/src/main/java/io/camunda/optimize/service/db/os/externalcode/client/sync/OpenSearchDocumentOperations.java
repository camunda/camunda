/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.externalcode.client.sync;

import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.ids;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL.clearScrollRequest;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL.deleteByQueryRequestBuilder;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL.deleteRequestBuilder;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL.getRequest;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL.scrollRequest;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL.time;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL.updateByQueryRequestBuilder;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.CountResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.MgetRequest;
import org.opensearch.client.opensearch.core.MgetResponse;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.ScrollResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateByQueryRequest;
import org.opensearch.client.opensearch.core.UpdateByQueryResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.opensearch.client.opensearch.core.mget.MultiGetOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.slf4j.Logger;

public class OpenSearchDocumentOperations extends OpenSearchRetryOperation {

  public static final String SCROLL_KEEP_ALIVE_MS = "60000ms";
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(OpenSearchDocumentOperations.class);

  public OpenSearchDocumentOperations(
      final OpenSearchClient openSearchClient, final OptimizeIndexNameService indexNameService) {
    super(openSearchClient, indexNameService);
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

  public void clearScroll(final String scrollId) {
    if (scrollId != null) {
      try {
        openSearchClient.clearScroll(clearScrollRequest(scrollId));
      } catch (final Exception e) {
        log.warn("Error occurred when clearing the scroll with id [{}]", scrollId);
      }
    }
  }

  private void checkFailedShards(final SearchRequest request, final SearchResponse<?> response) {
    if (!response.shards().failures().isEmpty()) {
      throw new OptimizeRuntimeException(
          format(
              "Shards failed executing request (request=%s, failed shards=%s)",
              request, response.shards().failures()));
    }
  }

  public <R> Map<String, Aggregate> unsafeScrollWith(
      final SearchRequest.Builder searchRequestBuilder,
      final Consumer<List<Hit<R>>> hitsConsumer,
      final Consumer<HitsMetadata<R>> hitsMetadataConsumer,
      final Class<R> clazz,
      final boolean retry)
      throws IOException {
    final var request =
        applyIndexPrefix(searchRequestBuilder.scroll(time(SCROLL_KEEP_ALIVE_MS))).build();

    return retry
        ? executeWithRetries(() -> scrollWith(request, hitsConsumer, hitsMetadataConsumer, clazz))
        : scrollWith(request, hitsConsumer, hitsMetadataConsumer, clazz);
  }

  public <R> ScrollResponse<R> scroll(final ScrollRequest scrollRequest, final Class<R> clazz)
      throws IOException {
    return openSearchClient.scroll(scrollRequest, clazz);
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

  public <R> void scrollWith(
      final SearchRequest.Builder requestBuilder,
      final Class<R> entityClass,
      final Consumer<List<Hit<R>>> hitsConsumer,
      final Consumer<HitsMetadata<R>> hitsMetadataConsumer) {
    safeScrollWith(requestBuilder, entityClass, hitsConsumer, hitsMetadataConsumer);
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

  public <R> SearchResponse<R> unsafeSearch(final SearchRequest request, final Class<R> entityClass)
      throws IOException {
    final var response = openSearchClient.search(request, entityClass);
    checkFailedShards(request, response);
    return response;
  }

  public <R> SearchResponse<R> search(
      final SearchRequest.Builder requestBuilder,
      final Class<R> entityClass,
      final Function<Exception, String> errorMessage) {
    return search(requestBuilder, entityClass, errorMessage, false);
  }

  public <R> SearchResponse<R> search(
      final SearchRequest.Builder requestBuilder,
      final Class<R> entityClass,
      final Function<Exception, String> searchErrorMessage,
      final boolean retry) {
    final var request = applyIndexPrefix(requestBuilder).build();
    return retry
        ? executeWithRetries(() -> unsafeSearch(request, entityClass))
        : safe(() -> unsafeSearch(request, entityClass), searchErrorMessage);
  }

  public <R> List<R> searchValues(
      final SearchRequest.Builder requestBuilder, final Class<R> entityClass) {
    return searchValues(requestBuilder, entityClass, false);
  }

  public <R> List<R> searchValues(
      final SearchRequest.Builder requestBuilder, final Class<R> entityClass, final boolean retry) {
    return search(
            requestBuilder, entityClass, defaultSearchErrorMessage(getIndex(requestBuilder)), retry)
        .hits()
        .hits()
        .stream()
        .map(Hit::source)
        .toList();
  }

  public Map<String, Aggregate> searchAggregations(final SearchRequest.Builder requestBuilder) {
    requestBuilder.size(0);
    return search(requestBuilder, Void.class, defaultSearchErrorMessage(getIndex(requestBuilder)))
        .aggregations();
  }

  public Map<String, Aggregate> searchAggregationsUnsafe(final SearchRequest.Builder requestBuilder)
      throws IOException {
    requestBuilder.size(0);
    return unsafeSearch(requestBuilder.build(), Void.class).aggregations();
  }

  public <R> R searchUnique(
      final SearchRequest.Builder requestBuilder, final Class<R> entityClass, final String key) {
    final SearchResponse<R> response =
        search(requestBuilder, entityClass, defaultSearchErrorMessage(getIndex(requestBuilder)));

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

  public BulkResponse bulk(
      final BulkRequest.Builder bulkReqBuilder,
      final Function<Exception, String> errorMessageSupplier) {
    return safe(
        () -> openSearchClient.bulk(applyIndexPrefix(bulkReqBuilder).build()),
        errorMessageSupplier);
  }

  public long docCount(final SearchRequest.Builder requestBuilder) {
    requestBuilder.size(0);
    return search(requestBuilder, Void.class, defaultSearchErrorMessage(getIndex(requestBuilder)))
        .hits()
        .total()
        .value();
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

  public boolean documentExistsWithGivenRetries(final String name, final String id) {
    return executeWithGivenRetries(
        10,
        format("Exists document from %s with id %s", name, id),
        () -> openSearchClient.exists(e -> e.index(name).id(id)).value(),
        null);
  }

  public <R> Optional<R> getWithRetries(
      final String index, final String id, final Class<R> entityClass) {
    return executeWithRetries(
        () -> {
          try {
            final GetResponse<R> response =
                openSearchClient.get(applyIndexPrefix(getRequest(index, id)).build(), entityClass);
            return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
          } catch (final OpenSearchException e) {
            if (openSearchClient._transport() instanceof AwsSdk2Transport
                && isAwsNotFoundException(e)) {
              return Optional.empty();
            } else {
              log.error(e.getMessage());
              throw new OptimizeRuntimeException(e.getMessage());
            }
          }
        });
  }

  public DeleteByQueryResponse delete(final String index, final String field, final String value) {
    final var deleteRequestBuilder =
        new DeleteByQueryRequest.Builder().index(index).query(term(field, value));

    return safe(
        () -> openSearchClient.deleteByQuery(applyIndexPrefix(deleteRequestBuilder).build()),
        e -> defaultDeleteErrorMessage(index));
  }

  public long deleteByQuery(final Query query, final boolean refresh, final String... indexes) {
    final List<String> listIndexes = List.of(indexes);
    final DeleteByQueryRequest request =
        applyIndexPrefix(deleteByQueryRequestBuilder(listIndexes))
            .query(query)
            .refresh(refresh)
            .build();
    final DeleteByQueryResponse response;
    Long status;
    try {
      response = openSearchClient.deleteByQuery(request);
      status = response.deleted();
    } catch (final IOException e) {
      status = null;
    }

    if (status == null) {
      final String message =
          String.format("Could not delete any record from the indexes [%s]", listIndexes);
      log.error(message);
      throw new OptimizeRuntimeException(message);
    } else {
      final String message =
          String.format("Deleted [%s] records from the indexes [%s]", status, listIndexes);
      log.debug(message);
      return status;
    }
  }

  public long updateByQuery(final String index, final Query query, final Script script) {
    final UpdateByQueryRequest request =
        applyIndexPrefix(updateByQueryRequestBuilder(List.of(index)))
            .query(query)
            .refresh(true)
            .script(script)
            .build();
    final UpdateByQueryResponse response;
    Long status;
    try {
      response = openSearchClient.updateByQuery(request);
      status = response.updated();
    } catch (final IOException e) {
      status = null;
    }

    if (status == null) {
      final String message = String.format("Could not update any record from the [%s]", index);
      log.error(message);
      throw new OptimizeRuntimeException(message);
    } else {
      final String message =
          String.format("Updated [%s] records from the index [%s]", status, index);
      log.debug(message);
      return status;
    }
  }

  public boolean deleteWithRetries(final String index, final String id) {
    return executeWithRetries(
        () ->
            openSearchClient
                    .delete(applyIndexPrefix(deleteRequestBuilder(index, id)).build())
                    .result()
                == Result.Deleted);
  }

  public DeleteResponse delete(final String indexName, final String entityId) {
    return safe(
        () ->
            openSearchClient.delete(
                applyIndexPrefix(deleteRequestBuilder(indexName, entityId)).build()),
        e -> defaultDeleteErrorMessage(indexName));
  }

  public <A> IndexResponse index(final IndexRequest.Builder<A> requestBuilder) {
    return safe(
        () -> openSearchClient.index(applyIndexPrefix(requestBuilder).build()),
        e -> defaultPersistErrorMessage(getIndex(requestBuilder)));
  }

  public <T> UpdateResponse<Void> update(
      final UpdateRequest.Builder<Void, T> requestBuilder,
      final Function<Exception, String> errorMessageSupplier) {
    return safe(
        () -> openSearchClient.update(applyIndexPrefix(requestBuilder).build(), Void.class),
        errorMessageSupplier);
  }

  public <A, B> UpdateResponse<A> upsert(
      final UpdateRequest.Builder<A, B> requestBuilder,
      final Class<A> clazz,
      final Function<Exception, String> errorMessageSupplier) {
    return safe(
        () -> openSearchClient.update(applyIndexPrefix(requestBuilder).build(), clazz),
        errorMessageSupplier);
  }

  public DeleteResponse delete(
      final DeleteRequest.Builder requestBuilder,
      final Function<Exception, String> errorMessageSupplier) {
    return safe(
        () -> openSearchClient.delete(applyIndexPrefix(requestBuilder).build()),
        errorMessageSupplier);
  }

  public CountResponse count(
      final CountRequest.Builder requestBuilder,
      final Function<Exception, String> errorMessageSupplier) {
    return safe(
        () -> openSearchClient.count(applyIndexPrefix(requestBuilder).build()),
        errorMessageSupplier);
  }

  public <T> GetResponse<T> get(
      final GetRequest.Builder requestBuilder,
      final Class<T> responseClass,
      final Function<Exception, String> errorMessageSupplier) {
    try {
      return openSearchClient.get(applyIndexPrefix(requestBuilder).build(), responseClass);
    } catch (final Exception e) {
      if (e instanceof final OpenSearchException osException
          && openSearchClient._transport() instanceof AwsSdk2Transport
          && isAwsNotFoundException(osException)) {
        // Making sure the response is consistent with the one from the OpenSearch client due to
        // this
        // unresolved AWS bug https://github.com/opensearch-project/opensearch-java/issues/424
        // ID and index are required fields in the response but their value doesn't matter, since
        // what
        // matters is that found=false
        return new GetResponse.Builder<T>()
            .id("") // Value irrelevant, but required
            .index("") // Value irrelevant, but required
            .found(false)
            .build();
      } else {
        final String message = errorMessageSupplier.apply(e);
        log.error(message, e);
        throw new OptimizeRuntimeException(message, e);
      }
    }
  }

  public <T> MgetResponse<T> mget(
      final Class<T> responseClass,
      final Function<Exception, String> errorMessageSupplier,
      final Map<String, String> indexesToEntitiesId) {

    final List<MultiGetOperation> operations =
        indexesToEntitiesId.entrySet().stream()
            .filter(pair -> Objects.nonNull(pair.getKey()) && Objects.nonNull(pair.getValue()))
            .map(
                idToIndex ->
                    getMultiGetOperation(
                        getIndexAliasFor(idToIndex.getKey()), idToIndex.getValue()))
            .toList();

    final MgetRequest.Builder requestBuilder = new MgetRequest.Builder().docs(operations);
    return safe(
        () -> openSearchClient.mget(requestBuilder.build(), responseClass), errorMessageSupplier);
  }

  private MultiGetOperation getMultiGetOperation(final String index, final String id) {
    return new MultiGetOperation.Builder().id(id).index(index).build();
  }

  private Boolean isAwsNotFoundException(final OpenSearchException e) {
    return e.getMessage().contains(Integer.toString(HTTP_NOT_FOUND));
  }

  // TODO check unused methods with OPT-7352
  public record AggregatedResult<R>(List<R> values, Map<String, Aggregate> aggregates) {}
}
