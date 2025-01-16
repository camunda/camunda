/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.CollectionUtil.map;
import static io.camunda.tasklist.util.CollectionUtil.throwAwayNullElements;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import jakarta.json.JsonArray;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryVariant;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.SearchResult;
import org.opensearch.client.opensearch.indices.RefreshRequest;
import org.opensearch.client.opensearch.indices.RefreshResponse;
import org.opensearch.client.util.ObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class OpenSearchUtil {

  public static final String ZEEBE_INDEX_DELIMITER = "_";
  public static final String SCROLL_KEEP_ALIVE_MS = "60000ms";
  public static final String INTERNAL_SCROLL_KEEP_ALIVE_MS =
      "30000ms"; // this scroll timeout value is used for reindex and delete q
  public static final int QUERY_MAX_SIZE = 10000;
  public static final int UPDATE_RETRY_COUNT = 3;
  public static final Function<Hit, Long> SEARCH_HIT_ID_TO_LONG = (hit) -> Long.valueOf(hit.id());
  public static final Function<Hit, String> SEARCH_HIT_ID_TO_STRING = Hit::id;
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchUtil.class);

  public static void clearScroll(final String scrollId, final OpenSearchClient osClient) {
    if (scrollId != null) {
      // clear the scroll
      final ClearScrollRequest clearScrollRequest =
          new ClearScrollRequest.Builder().scrollId(scrollId).build();

      try {
        osClient.clearScroll(clearScrollRequest);
      } catch (final Exception e) {
        LOGGER.warn("Error occurred when clearing the scroll with id [{}]", scrollId, e);
      }
    }
  }

  public static Query joinWithAnd(final ObjectBuilder... queries) {
    final List<ObjectBuilder> notNullQueries = throwAwayNullElements(queries);
    if (notNullQueries.size() == 0) {
      return new Query.Builder().build();
    }
    final BoolQuery.Builder boolQ = boolQuery();
    for (final ObjectBuilder queryBuilder : notNullQueries) {
      final var query = queryBuilder.build();

      if (query instanceof final QueryVariant qv) {
        boolQ.must(qv._toQuery());
      } else if (query instanceof final Query q) {
        boolQ.must(q);
      } else {
        throw new TasklistRuntimeException("Queries should be of type [Query] or [QueryVariant]");
      }
    }
    return new Query.Builder().bool(boolQ.build()).build();
  }

  public static Query createMatchNoneQuery() {
    final BoolQuery boolQuery =
        new BoolQuery.Builder()
            .must(must -> must.matchNone(none -> none.queryName("matchNone")))
            .build();
    return boolQuery._toQuery();
  }

  public static Query joinWithAnd(final Query... queries) {
    final List<Query> notNullQueries = throwAwayNullElements(queries);
    if (notNullQueries.size() == 0) {
      return new Query.Builder().build();
    }
    final BoolQuery.Builder boolQ = boolQuery();
    for (final Query queryBuilder : notNullQueries) {
      final var query = queryBuilder;

      if (query instanceof final QueryVariant qv) {
        boolQ.must(qv._toQuery());
      } else {
        boolQ.must(query);
      }
    }
    return new Query.Builder().bool(boolQ.build()).build();
  }

  public static Query.Builder joinQueryBuilderWithAnd(final ObjectBuilder... queries) {
    final List<ObjectBuilder> notNullQueries = throwAwayNullElements(queries);
    final Query.Builder queryBuilder = new Query.Builder();
    switch (notNullQueries.size()) {
      case 0:
        return null;
      default:
        final BoolQuery.Builder boolQ = boolQuery();
        for (final ObjectBuilder query : notNullQueries) {
          boolQ.must((Query) query.build());
        }
        queryBuilder.bool(boolQ.build());
        return queryBuilder;
    }
  }

  public static Query.Builder joinQueryBuilderWithOr(final ObjectBuilder... queries) {
    final List<ObjectBuilder> notNullQueries = throwAwayNullElements(queries);
    final Query.Builder queryBuilder = new Query.Builder();
    switch (notNullQueries.size()) {
      case 0:
        return null;
      default:
        final BoolQuery.Builder boolQ = boolQuery();
        for (final ObjectBuilder query : notNullQueries) {
          boolQ.should((Query) query.build());
        }
        queryBuilder.bool(boolQ.build());
        return queryBuilder;
    }
  }

  public static <T> T fromSearchHit(
      final String searchHitString, final ObjectMapper objectMapper, final Class<T> clazz) {
    final T entity;
    try {
      entity = objectMapper.readValue(searchHitString, clazz);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error while reading entity of type %s from Elasticsearch!", clazz.getName()),
          e);
    }
    return entity;
  }

  public static CompletableFuture<ScrollResponse<Object>> scrollAsync(
      final ScrollRequest scrollRequest,
      final Executor executor,
      final OpenSearchAsyncClient osClient) {
    final var searchFuture = new CompletableFuture<SearchResponse>();
    try {
      final CompletableFuture<ScrollResponse<Object>> response =
          osClient.scroll(scrollRequest, Object.class);
      return response;
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  public static BoolQuery.Builder boolQuery() {
    return new BoolQuery.Builder();
  }

  public static CompletableFuture<DeleteByQueryResponse> deleteByQueryAsync(
      final DeleteByQueryRequest deleteRequest,
      final Executor executor,
      final OpenSearchAsyncClient osClient) {
    try {
      return osClient.deleteByQuery(deleteRequest);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  public static CompletableFuture<ReindexResponse> reindexAsync(
      final ReindexRequest reindexRequest,
      final Executor executor,
      final OpenSearchAsyncClient osClient) {
    try {
      return osClient.reindex(reindexRequest);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  public static void processBulkRequest(
      final OpenSearchClient osClient, final BulkRequest bulkRequest) throws PersistenceException {

    if (bulkRequest.operations().size() > 0) {
      try {
        LOGGER.debug("************* FLUSH BULK START *************");
        final BulkResponse bulkItemResponses = osClient.bulk(bulkRequest);
        final List<BulkResponseItem> items = bulkItemResponses.items();
        for (final BulkResponseItem responseItem : items) {
          if (responseItem.error() != null) {
            LOGGER.error(
                String.format(
                    "%s failed for type [%s] and id [%s]: %s",
                    responseItem.operationType(),
                    responseItem.index(),
                    responseItem.id(),
                    responseItem.error().reason()),
                "error on OpenSearch BulkRequest");
            throw new PersistenceException(
                "Operation failed: " + responseItem.error().reason(),
                new TasklistRuntimeException(responseItem.error().reason()),
                Integer.valueOf(responseItem.id()));
          }
        }
        LOGGER.debug("************* FLUSH BULK FINISH *************");
      } catch (final IOException ex) {
        throw new PersistenceException(
            "Error when processing bulk request against OpenSearch: " + ex.getMessage(), ex);
      }
    }
  }

  public static void refreshIndicesFor(final OpenSearchClient osClient, final String indexPattern) {
    final var refreshRequest = new RefreshRequest.Builder().index(List.of(indexPattern)).build();
    try {
      final RefreshResponse refresh = osClient.indices().refresh(refreshRequest);
      if (refresh.shards().failures().size() > 0) {
        LOGGER.warn("Unable to refresh indices: {}", indexPattern);
      }
    } catch (final Exception ex) {
      LOGGER.warn(String.format("Unable to refresh indices: %s", indexPattern), ex);
    }
  }

  public static <T> List<T> mapSearchHits(
      final List<Hit> searchHits, final ObjectMapper objectMapper, final JavaType valueType) {
    return map(searchHits, (searchHit) -> objectMapper.convertValue(searchHit.source(), valueType));
  }

  public static CompletableFuture<SearchResponse<Object>> searchAsync(
      final SearchRequest searchRequest,
      final Executor executor,
      final OpenSearchAsyncClient osClient) {
    final var searchFuture = new CompletableFuture<SearchResponse>();

    try {
      return osClient.search(searchRequest, Object.class);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  public static void scrollWith(
      final SearchRequest.Builder searchRequest,
      final OpenSearchClient osClient,
      final Consumer<List<Hit>> searchHitsProcessor,
      final Consumer<Map> aggsProcessor,
      final Consumer<HitsMetadata> firstResponseConsumer)
      throws IOException {

    searchRequest.scroll(Time.of(t -> t.time(OpenSearchUtil.INTERNAL_SCROLL_KEEP_ALIVE_MS)));
    SearchResult response = osClient.search(searchRequest.build(), Object.class);

    if (firstResponseConsumer != null) {
      firstResponseConsumer.accept(response.hits());
    }

    if (aggsProcessor != null) {
      aggsProcessor.accept(response.aggregations());
    }

    String scrollId = response.scrollId();
    HitsMetadata hits = response.hits();
    try {
      while (hits.hits().size() != 0) {
        if (searchHitsProcessor != null) {
          searchHitsProcessor.accept(response.hits().hits());
        }

        final ScrollRequest.Builder scrollRequest = new ScrollRequest.Builder();
        scrollRequest.scrollId(scrollId);
        scrollRequest.scroll(Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS)));

        response = osClient.scroll(scrollRequest.build(), Object.class);
        scrollId = response.scrollId();
        hits = response.hits();
      }
    } catch (final Exception e) {
      throw new TasklistRuntimeException(e.getMessage());
    } finally {
      clearScroll(scrollId, osClient);
    }
  }

  public static <T> void scrollWith(
      final SearchRequest.Builder searchRequest,
      final OpenSearchClient osClient,
      final Consumer<List<Hit<T>>> searchHitsProcessor,
      final Consumer<Map> aggsProcessor,
      final Class<T> clazz,
      final Consumer<HitsMetadata<T>> firstResponseConsumer)
      throws IOException {

    searchRequest.scroll(Time.of(t -> t.time(OpenSearchUtil.INTERNAL_SCROLL_KEEP_ALIVE_MS)));
    SearchResult<T> response = osClient.search(searchRequest.build(), clazz);

    if (firstResponseConsumer != null) {
      firstResponseConsumer.accept(response.hits());
    }

    if (aggsProcessor != null) {
      aggsProcessor.accept(response.aggregations());
    }

    String scrollId = response.scrollId();
    HitsMetadata hits = response.hits();
    try {
      while (hits.hits().size() != 0) {
        if (searchHitsProcessor != null) {
          searchHitsProcessor.accept(response.hits().hits());
        }

        final ScrollRequest.Builder scrollRequest = new ScrollRequest.Builder();
        scrollRequest.scrollId(scrollId);
        scrollRequest.scroll(Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS)));

        response = osClient.scroll(scrollRequest.build(), clazz);
        scrollId = response.scrollId();
        hits = response.hits();
      }
    } catch (final Exception e) {
      throw new TasklistRuntimeException(e.getMessage());
    } finally {
      clearScroll(scrollId, osClient);
    }
  }

  public static String whereToSearch(
      final IndexDescriptor descriptor, final OpenSearchUtil.QueryType queryType) {
    switch (queryType) {
      case ONLY_RUNTIME:
        return descriptor.getFullQualifiedName();
      case ALL:
      default:
        return descriptor.getAlias();
    }
  }

  public static <T> List<T> mapSearchHits(
      final List<? extends Hit<?>> searchHits,
      final ObjectMapper objectMapper,
      final Class<T> clazz) {
    return map(
        searchHits,
        (searchHit) -> fromSearchHit(searchHit.source().toString(), objectMapper, clazz));
  }

  public static <T> List<T> scrollFieldToList(
      final SearchRequest.Builder request, final String fieldName, final OpenSearchClient esClient)
      throws IOException {
    final List<T> result = new ArrayList<>();

    final Function<Hit, T> searchHitFieldToString =
        (searchHit) ->
            (T)
                ((LinkedHashMap) searchHit.source())
                    .get(fieldName); // searchHit.getSourceAsMap().get(fieldName);

    final Consumer<List<Hit>> collectFields =
        (hits) -> result.addAll(map(hits, searchHitFieldToString));

    scrollWith(request, esClient, collectFields, null, null);
    return result;
  }

  public static SearchRequest.Builder createSearchRequest(final IndexTemplateDescriptor template) {
    return createSearchRequest(template, QueryType.ALL);
  }

  public static <T> T getRawResponseWithTenantCheck(
      final String id,
      final IndexDescriptor descriptor,
      final OpenSearchUtil.QueryType queryType,
      final TenantAwareOpenSearchClient tenantAwareClient,
      final Class<T> objectClass)
      throws IOException {
    final SearchRequest.Builder request =
        OpenSearchUtil.createSearchRequest(descriptor, queryType)
            .query(q -> q.ids(ids -> ids.values(id)));

    final SearchResponse<T> response = tenantAwareClient.search(request, objectClass);

    if (response.hits().total().value() == 1L) {
      return response.hits().hits().get(0).source();
    } else if (response.hits().total().value() > 1L) {
      throw new NotFoundException(
          String.format("Unique %s with id %s was not found", descriptor.getIndexName(), id));
    } else {
      throw new NotFoundException(
          String.format("%s with id %s was not found", descriptor.getIndexName(), id));
    }
  }

  public static SearchRequest.Builder createSearchRequest(
      final IndexDescriptor descriptor, final OpenSearchUtil.QueryType queryType) {
    final SearchRequest.Builder builder = new SearchRequest.Builder();
    builder.index(whereToSearch(descriptor, queryType));
    return builder;
  }

  public static <T> List<T> scroll(
      final SearchRequest.Builder searchRequest,
      final Class<T> clazz,
      final OpenSearchClient osClient)
      throws IOException {
    return scroll(searchRequest, clazz, osClient, null);
  }

  public static <T> List<T> scroll(
      final SearchRequest.Builder searchRequest,
      final Class<T> clazz,
      final OpenSearchClient osClient,
      final Consumer<HitsMetadata> searchHitsProcessor)
      throws IOException {

    searchRequest.scroll(Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS)));
    SearchResponse<T> response = osClient.search(searchRequest.build(), clazz);
    final List<T> result = new ArrayList<>();
    String scrollId = response.scrollId();
    HitsMetadata hits = response.hits();

    while (hits.hits().size() != 0) {
      result.addAll(hits.hits().stream().map(m -> ((Hit) m).source()).toList());
      // call response processor
      if (searchHitsProcessor != null) {
        searchHitsProcessor.accept(response.hits());
      }

      final ScrollRequest.Builder scrollRequest = new ScrollRequest.Builder();
      scrollRequest.scrollId(scrollId);
      scrollRequest.scroll(Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS)));

      response = osClient.scroll(scrollRequest.build(), clazz);
      scrollId = response.scrollId();
      hits = response.hits();
    }
    clearScroll(scrollId, osClient);

    return result;
  }

  public static List<String> scrollIdsToList(
      final SearchRequest.Builder request, final OpenSearchClient osClient) throws IOException {
    final List<String> result = new ArrayList<>();

    final Consumer<List<Hit>> collectIds =
        (hits) -> result.addAll(map(hits, SEARCH_HIT_ID_TO_STRING));

    scrollWith(request, osClient, collectIds, null, null);
    return result;
  }

  public static List<String> scrollUserTaskKeysToList(
      final SearchRequest.Builder request, final OpenSearchClient osClient) throws IOException {
    final List<String> result = new ArrayList<>();

    final Consumer<List<Hit>> collectKeys =
        (hits) ->
            result.addAll(
                map(
                    hits,
                    hit ->
                        ((JsonData) hit.fields().get(TaskTemplate.KEY))
                            .to(JsonArray.class)
                            .getFirst()
                            .toString()));

    scrollWith(request, osClient, collectKeys, null, null);
    return result;
  }

  public static Map<String, String> scrollIdsWithIndexToMap(
      final SearchRequest.Builder request, final OpenSearchClient osClient) throws IOException {
    final Map<String, String> result = new LinkedHashMap<>();

    final Consumer<List<Hit>> collectIds =
        (hits) -> result.putAll(hits.stream().collect(Collectors.toMap(Hit::id, Hit::index)));

    scrollWith(request, osClient, collectIds, null, null);
    return result;
  }

  public static void executeUpdate(
      final OpenSearchClient osClient, final UpdateRequest updateRequest)
      throws PersistenceException {
    try {

      osClient.update(updateRequest, Object.class);

    } catch (final OpenSearchException | IOException e) {
      final String errorMessage =
          String.format(
              "Update request failed for [%s] and id [%s] with the message [%s].",
              updateRequest.index(), updateRequest.id(), e.getMessage());
      throw new PersistenceException(errorMessage, e);
    }
  }

  public enum QueryType {
    ONLY_RUNTIME,
    ALL
  }
}
