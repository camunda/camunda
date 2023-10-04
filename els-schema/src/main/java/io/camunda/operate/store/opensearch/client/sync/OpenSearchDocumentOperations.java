/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.client.sync;

import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.opensearch.client.OpenSearchFailedShardsException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Result;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.ids;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.clearScrollRequest;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.deleteByQueryRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.deleteRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.getRequest;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.scrollRequest;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.time;
import static java.lang.String.format;


public class OpenSearchDocumentOperations extends OpenSearchRetryOperation {
  public record AggregatedResult<R>(List<R> values, Map<String, Aggregate> aggregates){}

  public static final String SCROLL_KEEP_ALIVE_MS = "60000ms";
  // this scroll timeout value is used for reindex and delete q
  public static final String INTERNAL_SCROLL_KEEP_ALIVE_MS = "30000ms";
  public static final int TERMS_AGG_SIZE = 10000;
  public static final int TOPHITS_AGG_SIZE = 100;

  public OpenSearchDocumentOperations(Logger logger, OpenSearchClient openSearchClient) {
    super(logger, openSearchClient);
  }

  private static Function<Exception, String> defaultSearchErrorMessage(String index) {
    return e -> format("Failed to search index: %s! Reason: %s", index, e.getMessage());
  }

  private static  String defaultDeleteErrorMessage(String index) {
    return format("Failed to delete index: %s", index);
  }

  private static  String defaultPersistErrorMessage(String index) {
    return format("Failed to persist index: %s", index);
  }

  private void clearScroll(String scrollId) {
    if (scrollId != null) {
      try {
        openSearchClient.clearScroll(clearScrollRequest(scrollId));
      } catch (Exception e) {
        logger.warn("Error occurred when clearing the scroll with id [{}]", scrollId);
      }
    }
  }

  private void checkFailedShards(SearchRequest request, SearchResponse<?> response) {
    if (!response.shards().failures().isEmpty()) {
      throw new OpenSearchFailedShardsException(
        format("Shards failed executing request (request=%s, failed shards=%s)", request, response.shards().failures())
      );
    }
  }

  public <R> Map<String, Aggregate> unsafeScrollWith(
    SearchRequest.Builder searchRequestBuilder,
    Consumer<List<Hit<R>>> hitsConsumer,
    Consumer<HitsMetadata<R>> hitsMetadataConsumer,
    Class<R> clazz,
    boolean retry
  ) throws IOException {
    var request = searchRequestBuilder.scroll(time(SCROLL_KEEP_ALIVE_MS)).build();

    return retry ?
      executeWithRetries(() -> scrollWith(request, hitsConsumer, hitsMetadataConsumer, clazz)) :
      scrollWith(request, hitsConsumer, hitsMetadataConsumer, clazz);
  }

  private <R> Map<String, Aggregate> scrollWith(
    SearchRequest request,
    Consumer<List<Hit<R>>> hitsConsumer,
    Consumer<HitsMetadata<R>> hitsMetadataConsumer,
    Class<R> clazz
  ) throws IOException {
    String scrollId = null;

    try {
      SearchResponse<R> response = openSearchClient.search(request, clazz);
      var aggregates = response.aggregations();

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

  private <R> Map<String, Aggregate> safeScrollWith(SearchRequest.Builder requestBuilder, Class<R> entityClass, Consumer<List<Hit<R>>> hitsConsumer) {
    return safeScrollWith(requestBuilder, entityClass, hitsConsumer, null);
  }

  private <R> Map<String, Aggregate> safeScrollWith(SearchRequest.Builder requestBuilder, Class<R> entityClass, Consumer<List<Hit<R>>> hitsConsumer, Consumer<HitsMetadata<R>> hitsMetadataConsumer) {
    return safe(
      () -> unsafeScrollWith(requestBuilder, hitsConsumer, hitsMetadataConsumer, entityClass, false),
      defaultSearchErrorMessage(getIndex(requestBuilder))
    );
  }

  private <R> AggregatedResult<R> scroll(SearchRequest.Builder searchRequestBuilder, Class<R> clazz, boolean retry) throws IOException {
    var result = scrollHits(searchRequestBuilder, clazz, retry);
    return new AggregatedResult<>(result.values().stream().map(Hit::source).toList(), result.aggregates());
  }

  public <R> AggregatedResult<Hit<R>> scrollHits(SearchRequest.Builder searchRequestBuilder, Class<R> clazz) throws IOException {
    final List<Hit<R>> result = new ArrayList<>();
    var aggregates = unsafeScrollWith(searchRequestBuilder, result::addAll, null, clazz, false);
    return new AggregatedResult<>(result, aggregates);
  }

  public <R> AggregatedResult<Hit<R>> scrollHits(SearchRequest.Builder searchRequestBuilder, Class<R> clazz, boolean retry) throws IOException {
    final List<Hit<R>> result = new ArrayList<>();
    var aggregates = unsafeScrollWith(searchRequestBuilder, result::addAll, null, clazz, retry);
    return new AggregatedResult<>(result, aggregates);
  }

  public <R> void scrollWith(SearchRequest.Builder requestBuilder, Class<R> entityClass, Consumer<List<Hit<R>>> hitsConsumer) {
    safeScrollWith(requestBuilder, entityClass, hitsConsumer);
  }

  public <R> void scrollWith(SearchRequest.Builder requestBuilder, Class<R> entityClass, Consumer<List<Hit<R>>> hitsConsumer, Consumer<HitsMetadata<R>> hitsMetadataConsumer) {
    safeScrollWith(requestBuilder, entityClass, hitsConsumer, hitsMetadataConsumer);
  }

  public <R> AggregatedResult<R> scrollValuesAndAggregations(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
    return safe(() -> scroll(requestBuilder, entityClass, false), defaultSearchErrorMessage(getIndex(requestBuilder)));
  }

  public <R> AggregatedResult<R> scrollValuesAndAggregations(SearchRequest.Builder requestBuilder, Class<R> entityClass, boolean retry) {
    return safe(() -> scroll(requestBuilder, entityClass, retry), defaultSearchErrorMessage(getIndex(requestBuilder)));
  }

  public <R> List<R> scrollValues(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
    return scrollValuesAndAggregations(requestBuilder, entityClass).values();
  }

  public <R> List<R> scrollValues(SearchRequest.Builder requestBuilder, Class<R> entityClass, boolean retry) {
    return scrollValuesAndAggregations(requestBuilder, entityClass, retry).values();
  }

  private <R> SearchResponse<R> unsafeSearch(SearchRequest request, Class<R> entityClass) throws IOException {
    var response = openSearchClient.search(request, entityClass);
    checkFailedShards(request, response);
    return response;
  }

  public <R> SearchResponse<R> search(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
    return search(requestBuilder, entityClass, false);
  }

  public <R> SearchResponse<R> search(SearchRequest.Builder requestBuilder, Class<R> entityClass, boolean retry) {
    var request = requestBuilder.build();
    return retry ?
      executeWithRetries(() -> unsafeSearch(request, entityClass)) :
      safe(
        () -> unsafeSearch(request, entityClass),
        defaultSearchErrorMessage(getIndex(requestBuilder))
      );
  }

  public <R> List<R> searchValues(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
    return searchValues(requestBuilder, entityClass, false);
  }

  public <R> List<R> searchValues(SearchRequest.Builder requestBuilder, Class<R> entityClass, boolean retry) {
    return search(requestBuilder, entityClass, retry).hits().hits().stream().map(Hit::source).toList();
  }

  public Map<String, Aggregate> searchAggregations(SearchRequest.Builder requestBuilder) {
    requestBuilder.size(0);
    return search(requestBuilder, Void.class).aggregations();
  }

  public <R> R searchUnique(SearchRequest.Builder requestBuilder, Class<R> entityClass, String key) {
    final SearchResponse<R> response = search(requestBuilder, entityClass);

    if (response.hits().total().value() == 1) {
      return response.hits().hits().get(0).source();
    } else if (response.hits().total().value() > 1) {
      throw new NotFoundException(format("Could not find unique %s with key '%s'.", getIndex(requestBuilder), key));
    } else {
      throw new NotFoundException(format("Could not find %s with key '%s'.", getIndex(requestBuilder), key));
    }
  }

  public long docCount(SearchRequest.Builder requestBuilder) {
    requestBuilder.size(0);
    return search(requestBuilder, Void.class).hits().total().value();
  }

  public Map<String, String> getIndexNames(String index, Collection<String> ids) {
    final Map<String, String> result = new HashMap<>();
    var searchRequestBuilder = new SearchRequest.Builder()
      .index(index)
      .query(ids(ids))
      .source(s -> s.fetch(false));

    Consumer<List<Hit<Void>>> hitsConsumer = hits -> hits.forEach(
      hit -> result.put(hit.id(), hit.index())
    );

    safeScrollWith(searchRequestBuilder, Void.class, hitsConsumer);

    return result;
  }

  // TODO check unused
  public boolean documentExistsWithGivenRetries(String name, String id) {
    return executeWithGivenRetries(
      10,
      format("Exists document from %s with id %s", name, id),
      () -> openSearchClient.exists(e -> e.index(name).id(id)).value(),
      null);
  }

  public <R> Optional<R> getWithRetries(String index, String id, Class<R> entitiyClass) {
    return executeWithRetries(() -> {
      final GetResponse<R> response = openSearchClient.get(getRequest(index, id), entitiyClass);
      return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
    });
  }

  public DeleteResponse delete(String index, String id) {
    var deleteRequestBuilder = new DeleteRequest.Builder()
      .index(index)
      .id(id);

    return safe(() -> openSearchClient.delete(deleteRequestBuilder.build()), e -> defaultDeleteErrorMessage(index));
  }

  public DeleteByQueryResponse delete(String index, String field, String value) {
    var deleteRequestBuilder = new DeleteByQueryRequest.Builder()
      .index(index)
      .query(term(field, value));

    return safe(() -> openSearchClient.deleteByQuery(deleteRequestBuilder.build()), e -> defaultDeleteErrorMessage(index));
  }

  public boolean deleteWithRetries(String index, Query query) {
    return executeWithRetries(
      () -> {
        final DeleteByQueryRequest request = deleteByQueryRequestBuilder(index).query(query).build();
        final DeleteByQueryResponse response = openSearchClient.deleteByQuery(request);
        return response.failures().isEmpty() && response.deleted() > 0;
      });
  }

  public boolean deleteWithRetries(String index, String id) {
    return executeWithRetries(() -> openSearchClient.delete(deleteRequestBuilder(index, id).build()).result() == Result.Deleted);
  }

  public <A> IndexResponse index(IndexRequest.Builder<A> requestBuilder) {
    return safe(() -> openSearchClient.index(requestBuilder.build()), e -> defaultPersistErrorMessage(getIndex(requestBuilder)));
  }

  public <A> boolean indexWithRetries(IndexRequest.Builder<A> requestBuilder) {
    return executeWithRetries(
      () -> {
        final IndexResponse response = openSearchClient.index(requestBuilder.build());
        return List.of(Result.Created, Result.Updated).contains(response.result());
      });
  }

  public <A> UpdateResponse<Void> update(UpdateRequest.Builder<Void, A> requestBuilder, Function<Exception, String> errorMessageSupplier) {
    return safe(() -> openSearchClient.update(requestBuilder.build(), Void.class), errorMessageSupplier);
  }
}
