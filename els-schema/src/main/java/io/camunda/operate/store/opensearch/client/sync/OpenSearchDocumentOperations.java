/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.client.sync;

import io.camunda.operate.store.NotFoundException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.Time;
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
import java.util.function.Consumer;
import java.util.function.Function;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.ids;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.clearScrollRequest;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.getRequest;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.scrollRequest;


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

  private static  String defaultSearchErrorMessage(String index) {
    return String.format("Failed to search index: %s", index);
  }

  private static  String defaultDeleteErrorMessage(String index) {
    return String.format("Failed to delete index: %s", index);
  }

  private static  String defaultPersistErrorMessage(String index) {
    return String.format("Failed to persist index: %s", index);
  }

  private final Runnable ignoreFailedShardHandler = () -> {};

  private void clearScroll(String scrollId) {
    if (scrollId != null) {
      try {
        openSearchClient.clearScroll(clearScrollRequest(scrollId));
      } catch (Exception e) {
        logger.warn("Error occurred when clearing the scroll with id [{}]", scrollId);
      }
    }
  }

  private <R> Map<String, Aggregate> scrollWith(
    SearchRequest.Builder searchRequestBuilder,
    Consumer<List<Hit<R>>> hitsConsumer,
    Consumer<HitsMetadata<R>> hitsMetadataConsumer,
    Runnable failedShardHandler,
    Class<R> clazz
  ) throws IOException {
    var searchRequest = searchRequestBuilder.scroll(Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS))).build();
    String scrollId = null;

    try {
      SearchResponse<R> response = openSearchClient.search(searchRequest, clazz);
      var aggregates = response.aggregations();

      if (hitsMetadataConsumer != null) {
        hitsMetadataConsumer.accept(response.hits());
      }

      scrollId = response.scrollId();
      List<Hit<R>> hits = response.hits().hits();

      while (!hits.isEmpty() && scrollId != null) {
        if (!response.shards().failures().isEmpty()) {
          failedShardHandler.run();
        }

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
      () -> scrollWith(requestBuilder, hitsConsumer, hitsMetadataConsumer, ignoreFailedShardHandler, entityClass),
      (e) -> defaultSearchErrorMessage(getIndex(requestBuilder))
    );
  }

  private <R> AggregatedResult<R> scroll(
    SearchRequest.Builder searchRequestBuilder,
    Class<R> clazz)
    throws IOException {
    return scroll(searchRequestBuilder, clazz, ignoreFailedShardHandler);
  }

  private <R> AggregatedResult<R> scroll(
    SearchRequest.Builder searchRequestBuilder,
    Class<R> clazz,
    Runnable failedShardHandler)
    throws IOException {
    var result = scrollHits(searchRequestBuilder, clazz, failedShardHandler);
    return new AggregatedResult<>(result.values().stream().map(Hit::source).toList(), result.aggregates());
  }

  private <R> AggregatedResult<Hit<R>> scrollHits(SearchRequest.Builder searchRequestBuilder, Class<R> clazz, Runnable failedShardHandler) throws IOException {
    final List<Hit<R>> result = new ArrayList<>();
    var aggregates = scrollWith(searchRequestBuilder, result::addAll, null, failedShardHandler, clazz);
    return new AggregatedResult<>(result, aggregates);
  }

  public <R> void scrollWith(SearchRequest.Builder requestBuilder, Class<R> entityClass, Consumer<List<Hit<R>>> hitsConsumer) {
    safeScrollWith(requestBuilder, entityClass, hitsConsumer);
  }

  public <R> void scrollWith(SearchRequest.Builder requestBuilder, Class<R> entityClass, Consumer<List<Hit<R>>> hitsConsumer, Consumer<HitsMetadata<R>> hitsMetadataConsumer) {
    safeScrollWith(requestBuilder, entityClass, hitsConsumer, hitsMetadataConsumer);
  }

  public <R> AggregatedResult<Hit<R>> searchHits(SearchRequest.Builder requestBuilder, Class<R> entityClass, Runnable failedShardHandler) {
    return safe(() -> scrollHits(requestBuilder, entityClass, failedShardHandler), (e) -> defaultSearchErrorMessage(getIndex(requestBuilder)));
  }

  public <R> AggregatedResult<Hit<R>> searchHits(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
    return searchHits(requestBuilder, entityClass, ignoreFailedShardHandler);
  }

  public <R> AggregatedResult<R> searchValuesAndAggregations(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
    return safe(() -> scroll(requestBuilder, entityClass), (e) -> defaultSearchErrorMessage(getIndex(requestBuilder)));
  }

  public <R> List<R> searchValues(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
    return searchValuesAndAggregations(requestBuilder, entityClass).values();
  }

  public <R> SearchResponse<R> search(SearchRequest.Builder requestBuilder, Class<R> entityClass) {
    return safe(() -> openSearchClient.search(requestBuilder.build(), entityClass), (e) -> defaultSearchErrorMessage(getIndex(requestBuilder)));
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
      throw new NotFoundException(String.format("Could not find unique %s with key '%s'.", getIndex(requestBuilder), key));
    } else {
      throw new NotFoundException(String.format("Could not find %s with key '%s'.", getIndex(requestBuilder), key));
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
      String.format("Exists document from %s with id %s", name, id),
      () -> openSearchClient.exists(e -> e.index(name).id(id)).value(),
      null);
  }

  public Map<String, Object> getDocumentWithGivenRetries(String index, String id) {
    return executeWithGivenRetries(
      10,
      String.format("Get document from %s with id %s", index, id),
      () -> {
        final GetResponse response = openSearchClient.get(getRequest(index, id), Void.class);
        if (response.found()) {
          return response.fields();
        } else {
          return null;
        }
      },
      null);
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
        final DeleteByQueryRequest request =
          new DeleteByQueryRequest.Builder().index(List.of(index)).query(query).build();
        final DeleteByQueryResponse response = openSearchClient.deleteByQuery(request);
        return response.failures().isEmpty() && response.deleted() > 0;
      });
  }

  public boolean deleteWithRetries(String index, String id) {
    return executeWithRetries(
      () -> {
        final DeleteResponse response =
          openSearchClient.delete(new DeleteRequest.Builder().index(index).id(id).build());
        return response.result() == Result.Deleted;
      });
  }

  public <A> IndexResponse index(IndexRequest.Builder<A> requestBuilder) {
    return safe(() -> openSearchClient.index(requestBuilder.build()), e -> defaultPersistErrorMessage(getIndex(requestBuilder)));
  }

  public boolean createOrUpdateWithRetries(String name, String id, Map<?, ?> source) {
    return executeWithRetries(
      () -> {
        final IndexResponse response = openSearchClient.index(i -> i.index(name).id(id).document(source));
        return List.of(Result.Created, Result.Updated).contains(response.result());
      });
  }

  public boolean createOrUpdateWithRetries(String name, String id, String source) {
    return executeWithRetries(
      () -> {
        final IndexResponse response =
          openSearchClient.index(i -> i.index(name).id(id).document(source));
        return List.of(Result.Created, Result.Updated).contains(response.result());
      });
  }

  public <A> UpdateResponse<Void> update(UpdateRequest.Builder<Void, A> requestBuilder, Function<Exception, String> errorMessageSupplier) {
    return safe(() -> openSearchClient.update(requestBuilder.build(), Void.class), errorMessageSupplier);
  }
}
