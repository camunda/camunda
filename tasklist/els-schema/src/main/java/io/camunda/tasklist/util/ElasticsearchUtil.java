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
import static org.elasticsearch.action.support.IndicesOptions.Option.ALLOW_NO_INDICES;
import static org.elasticsearch.action.support.IndicesOptions.Option.IGNORE_THROTTLED;
import static org.elasticsearch.action.support.IndicesOptions.Option.IGNORE_UNAVAILABLE;
import static org.elasticsearch.action.support.IndicesOptions.WildcardStates.CLOSED;
import static org.elasticsearch.action.support.IndicesOptions.WildcardStates.OPEN;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ElasticsearchUtil {

  public static final String ZEEBE_INDEX_DELIMITER = "_";
  public static final int SCROLL_KEEP_ALIVE_MS = 60000;
  public static final int INTERNAL_SCROLL_KEEP_ALIVE_MS =
      30000; // this scroll timeout value is used for reindex and delete queries
  public static final int QUERY_MAX_SIZE = 10000;
  public static final int UPDATE_RETRY_COUNT = 3;
  public static final Function<SearchHit, Long> SEARCH_HIT_ID_TO_LONG =
      (hit) -> Long.valueOf(hit.getId());
  public static final Function<SearchHit, String> SEARCH_HIT_ID_TO_STRING = SearchHit::getId;

  /** IndicesOptions */
  public static final IndicesOptions LENIENT_EXPAND_OPEN_FORBID_NO_INDICES_IGNORE_THROTTLED =
      new IndicesOptions(EnumSet.of(IGNORE_UNAVAILABLE, IGNORE_THROTTLED), EnumSet.of(OPEN));

  public static final IndicesOptions LENIENT_EXPAND_OPEN_IGNORE_THROTTLED =
      new IndicesOptions(
          EnumSet.of(ALLOW_NO_INDICES, IGNORE_UNAVAILABLE, IGNORE_THROTTLED), EnumSet.of(OPEN));
  public static final IndicesOptions STRICT_EXPAND_OPEN_CLOSED_IGNORE_THROTTLED =
      new IndicesOptions(EnumSet.of(ALLOW_NO_INDICES, IGNORE_THROTTLED), EnumSet.of(OPEN, CLOSED));
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchUtil.class);

  public static SearchRequest createSearchRequest(final IndexTemplateDescriptor template) {
    return createSearchRequest(template, QueryType.ALL);
  }

  public static SearchHit getRawResponseWithTenantCheck(
      final String id,
      final IndexDescriptor descriptor,
      final QueryType queryType,
      final TenantAwareElasticsearchClient tenantAwareClient)
      throws IOException {
    final QueryBuilder query = idsQuery().addIds(id);

    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(descriptor, queryType)
            .source(new SearchSourceBuilder().query(constantScoreQuery(query)));

    final SearchResponse response = tenantAwareClient.search(request);
    if (response.getHits().getTotalHits().value == 1) {
      return response.getHits().getHits()[0];
    } else if (response.getHits().getTotalHits().value > 1) {
      throw new NotFoundException(
          String.format("Unique %s with id %s was not found", descriptor.getIndexName(), id));
    } else {
      throw new NotFoundException(
          String.format("%s with id %s was not found", descriptor.getIndexName(), id));
    }
  }

  public static CompletableFuture<BulkByScrollResponse> reindexAsync(
      final ReindexRequest reindexRequest,
      final Executor executor,
      final RestHighLevelClient esClient) {
    final var reindexFuture = new CompletableFuture<BulkByScrollResponse>();
    esClient.reindexAsync(
        reindexRequest,
        RequestOptions.DEFAULT,
        new DelegatingActionListener<>(reindexFuture, executor));
    return reindexFuture;
  }

  public static CompletableFuture<BulkByScrollResponse> deleteByQueryAsync(
      final DeleteByQueryRequest deleteRequest,
      final Executor executor,
      final RestHighLevelClient esClient) {
    final var deleteFuture = new CompletableFuture<BulkByScrollResponse>();
    esClient.deleteByQueryAsync(
        deleteRequest,
        RequestOptions.DEFAULT,
        new DelegatingActionListener<>(deleteFuture, executor));
    return deleteFuture;
  }

  public static CompletableFuture<SearchResponse> searchAsync(
      final SearchRequest searchRequest,
      final Executor executor,
      final RestHighLevelClient esClient) {
    final var searchFuture = new CompletableFuture<SearchResponse>();
    esClient.searchAsync(
        searchRequest,
        RequestOptions.DEFAULT,
        new DelegatingActionListener<>(searchFuture, executor));
    return searchFuture;
  }

  public static CompletableFuture<SearchResponse> scrollAsync(
      final SearchScrollRequest scrollRequest,
      final Executor executor,
      final RestHighLevelClient esClient) {
    final var searchFuture = new CompletableFuture<SearchResponse>();
    esClient.scrollAsync(
        scrollRequest,
        RequestOptions.DEFAULT,
        new DelegatingActionListener<>(searchFuture, executor));
    return searchFuture;
  }

  /* CREATE QUERIES */

  public static SearchRequest createSearchRequest(
      final IndexDescriptor descriptor, final QueryType queryType) {
    return new SearchRequest(whereToSearch(descriptor, queryType));
  }

  public static String whereToSearch(final IndexDescriptor descriptor, final QueryType queryType) {
    switch (queryType) {
      case ONLY_RUNTIME:
        return descriptor.getFullQualifiedName();
      case ALL:
      default:
        return descriptor.getAlias();
    }
  }

  public static QueryBuilder joinWithOr(
      final BoolQueryBuilder boolQueryBuilder, final QueryBuilder... queries) {
    final List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
    for (final QueryBuilder query : notNullQueries) {
      boolQueryBuilder.should(query);
    }
    return boolQueryBuilder;
  }

  /**
   * Join queries with OR clause. If 0 queries are passed for wrapping, then null is returned. If 1
   * parameter is passed, it will be returned back as ia. Otherwise, the new BoolQuery will be
   * created and returned.
   */
  public static QueryBuilder joinWithOr(final QueryBuilder... queries) {
    final List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
    switch (notNullQueries.size()) {
      case 0:
        return null;
      case 1:
        return notNullQueries.get(0);
      default:
        final BoolQueryBuilder boolQ = boolQuery();
        for (final QueryBuilder query : notNullQueries) {
          boolQ.should(query);
        }
        return boolQ;
    }
  }

  public static QueryBuilder joinWithOr(final Collection<QueryBuilder> queries) {
    return joinWithOr(queries.toArray(new QueryBuilder[queries.size()]));
  }

  /**
   * Join queries with AND clause. If 0 queries are passed for wrapping, then null is returned. If 1
   * parameter is passed, it will be returned back as ia. Otherwise, the new BoolQuery will be
   * created and returned.
   */
  public static QueryBuilder joinWithAnd(final QueryBuilder... queries) {
    final List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
    switch (notNullQueries.size()) {
      case 0:
        return null;
      case 1:
        return notNullQueries.get(0);
      default:
        final BoolQueryBuilder boolQ = boolQuery();
        for (final QueryBuilder query : notNullQueries) {
          boolQ.must(query);
        }
        return boolQ;
    }
  }

  public static QueryBuilder addToBoolMust(
      final BoolQueryBuilder boolQuery, final QueryBuilder... queries) {
    if (boolQuery.mustNot().size() != 0
        || boolQuery.filter().size() != 0
        || boolQuery.should().size() != 0) {
      throw new IllegalArgumentException("BoolQuery with only must elements is expected here.");
    }
    final List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
    for (final QueryBuilder query : notNullQueries) {
      boolQuery.must(query);
    }
    return boolQuery;
  }

  public static BoolQueryBuilder createMatchNoneQuery() {
    return boolQuery().must(QueryBuilders.wrapperQuery("{\"match_none\": {}}"));
  }

  public static void processBulkRequest(
      final RestHighLevelClient esClient, final BulkRequest bulkRequest)
      throws PersistenceException {
    processBulkRequest(esClient, bulkRequest, RefreshPolicy.NONE);
  }

  /* EXECUTE QUERY */

  public static void processBulkRequest(
      final RestHighLevelClient esClient,
      BulkRequest bulkRequest,
      final RefreshPolicy refreshPolicy)
      throws PersistenceException {
    if (bulkRequest.requests().size() > 0) {
      try {
        LOGGER.debug("************* FLUSH BULK START *************");
        bulkRequest = bulkRequest.setRefreshPolicy(refreshPolicy);
        final BulkResponse bulkItemResponses = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        final BulkItemResponse[] items = bulkItemResponses.getItems();
        for (final BulkItemResponse responseItem : items) {
          if (responseItem.isFailed()) {
            LOGGER.error(
                String.format(
                    "%s failed for type [%s] and id [%s]: %s",
                    responseItem.getOpType(),
                    responseItem.getIndex(),
                    responseItem.getId(),
                    responseItem.getFailureMessage()),
                responseItem.getFailure().getCause());
            throw new PersistenceException(
                "Operation failed: " + responseItem.getFailureMessage(),
                responseItem.getFailure().getCause(),
                responseItem.getItemId());
          }
        }
        LOGGER.debug("************* FLUSH BULK FINISH *************");
      } catch (final IOException ex) {
        throw new PersistenceException(
            "Error when processing bulk request against Elasticsearch: " + ex.getMessage(), ex);
      }
    }
  }

  public static void executeUpdate(
      final RestHighLevelClient esClient, final UpdateRequest updateRequest)
      throws PersistenceException {
    try {
      esClient.update(updateRequest, RequestOptions.DEFAULT);
    } catch (final ElasticsearchException | IOException e) {
      final String errorMessage =
          String.format(
              "Update request failed for [%s] and id [%s] with the message [%s].",
              updateRequest.index(), updateRequest.id(), e.getMessage());
      throw new PersistenceException(errorMessage, e);
    }
  }

  public static <T> List<T> mapSearchHits(
      final List<SearchHit> searchHits, final ObjectMapper objectMapper, final JavaType valueType) {
    return mapSearchHits(
        searchHits.toArray(new SearchHit[searchHits.size()]), objectMapper, valueType);
  }

  /* MAP QUERY RESULTS */

  public static <T> List<T> mapSearchHits(
      final SearchHit[] searchHits, final Function<SearchHit, T> searchHitMapper) {
    return map(searchHits, searchHitMapper);
  }

  public static <T> List<T> mapSearchHits(
      final SearchHit[] searchHits, final ObjectMapper objectMapper, final Class<T> clazz) {
    return map(
        searchHits,
        (searchHit) -> fromSearchHit(searchHit.getSourceAsString(), objectMapper, clazz));
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

  public static <T> List<T> mapSearchHits(
      final SearchHit[] searchHits, final ObjectMapper objectMapper, final JavaType valueType) {
    return map(
        searchHits,
        (searchHit) -> fromSearchHit(searchHit.getSourceAsString(), objectMapper, valueType));
  }

  public static <T> T fromSearchHit(
      final String searchHitString, final ObjectMapper objectMapper, final JavaType valueType) {
    final T entity;
    try {
      entity = objectMapper.readValue(searchHitString, valueType);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error while reading entity of type %s from Elasticsearch!", valueType.toString()),
          e);
    }
    return entity;
  }

  public static <T> List<T> scroll(
      final SearchRequest searchRequest,
      final Class<T> clazz,
      final ObjectMapper objectMapper,
      final RestHighLevelClient esClient)
      throws IOException {
    return scroll(searchRequest, clazz, objectMapper, esClient, null, null);
  }

  public static <T> List<T> scroll(
      final SearchRequest searchRequest,
      final Class<T> clazz,
      final ObjectMapper objectMapper,
      final RestHighLevelClient esClient,
      final Consumer<SearchHits> searchHitsProcessor,
      final Consumer<Aggregations> aggsProcessor)
      throws IOException {

    searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
    SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    // call aggregations processor
    if (aggsProcessor != null) {
      aggsProcessor.accept(response.getAggregations());
    }

    final List<T> result = new ArrayList<>();
    String scrollId = response.getScrollId();
    SearchHits hits = response.getHits();

    while (hits.getHits().length != 0) {
      result.addAll(mapSearchHits(hits.getHits(), objectMapper, clazz));

      // call response processor
      if (searchHitsProcessor != null) {
        searchHitsProcessor.accept(response.getHits());
      }

      final SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));

      response = esClient.scroll(scrollRequest, RequestOptions.DEFAULT);

      scrollId = response.getScrollId();
      hits = response.getHits();
    }

    clearScroll(scrollId, esClient);

    return result;
  }

  public static void scrollWith(
      final SearchRequest searchRequest,
      final RestHighLevelClient esClient,
      final Consumer<SearchHits> searchHitsProcessor,
      final Consumer<Aggregations> aggsProcessor,
      final Consumer<SearchHits> firstResponseConsumer)
      throws IOException {

    searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
    SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    if (firstResponseConsumer != null) {
      firstResponseConsumer.accept(response.getHits());
    }

    // call aggregations processor
    if (aggsProcessor != null) {
      aggsProcessor.accept(response.getAggregations());
    }

    String scrollId = response.getScrollId();
    SearchHits hits = response.getHits();
    while (hits.getHits().length != 0) {
      // call response processor
      if (searchHitsProcessor != null) {
        searchHitsProcessor.accept(response.getHits());
      }

      final SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));

      response = esClient.scroll(scrollRequest, RequestOptions.DEFAULT);

      scrollId = response.getScrollId();
      hits = response.getHits();
    }

    clearScroll(scrollId, esClient);
  }

  public static void clearScroll(final String scrollId, final RestHighLevelClient esClient) {
    if (scrollId != null) {
      // clear the scroll
      final ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
      clearScrollRequest.addScrollId(scrollId);
      try {
        esClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
      } catch (final Exception e) {
        LOGGER.warn("Error occurred when clearing the scroll with id [{}]", scrollId);
      }
    }
  }

  public static List<String> scrollIdsToList(
      final SearchRequest request, final RestHighLevelClient esClient) throws IOException {
    final List<String> result = new ArrayList<>();

    final Consumer<SearchHits> collectIds =
        (hits) -> result.addAll(map(hits.getHits(), SEARCH_HIT_ID_TO_STRING));

    scrollWith(request, esClient, collectIds, null, null);
    return result;
  }

  public static List<String> scrollUserTaskKeysToList(
      final SearchRequest request, final RestHighLevelClient esClient) throws IOException {
    final List<String> result = new ArrayList<>();

    final Consumer<SearchHits> collectKeys =
        (hits) ->
            result.addAll(
                map(
                    hits.getHits(),
                    hit ->
                        String.valueOf(
                            (long) hit.getDocumentFields().get(TaskTemplate.KEY).getValue())));

    scrollWith(request, esClient, collectKeys, null, null);
    return result;
  }

  public static Map<String, String> scrollIdsWithIndexToMap(
      final SearchRequest request, final RestHighLevelClient esClient) throws IOException {
    final Map<String, String> result = new LinkedHashMap();

    final Consumer<SearchHits> collectIds =
        (hits) ->
            result.putAll(
                Stream.of(hits.getHits())
                    .collect(Collectors.toMap(SearchHit::getId, SearchHit::getIndex)));

    scrollWith(request, esClient, collectIds, null, null);
    return result;
  }

  public static List<Long> scrollKeysToList(
      final SearchRequest request, final RestHighLevelClient esClient) throws IOException {
    final List<Long> result = new ArrayList<>();

    final Consumer<SearchHits> collectIds =
        (hits) -> result.addAll(map(hits.getHits(), SEARCH_HIT_ID_TO_LONG));

    scrollWith(request, esClient, collectIds, null, null);
    return result;
  }

  public static <T> List<T> scrollFieldToList(
      final SearchRequest request, final String fieldName, final RestHighLevelClient esClient)
      throws IOException {
    final List<T> result = new ArrayList<>();
    final Function<SearchHit, T> searchHitFieldToString =
        (searchHit) -> (T) searchHit.getSourceAsMap().get(fieldName);

    final Consumer<SearchHits> collectFields =
        (hits) -> result.addAll(map(hits.getHits(), searchHitFieldToString));

    scrollWith(request, esClient, collectFields, null, null);
    return result;
  }

  public static Set<String> scrollIdsToSet(
      final SearchRequest request, final RestHighLevelClient esClient) throws IOException {
    final Set<String> result = new HashSet<>();

    final Consumer<SearchHits> collectIds =
        (hits) -> result.addAll(map(hits.getHits(), SEARCH_HIT_ID_TO_STRING));
    scrollWith(request, esClient, collectIds, null, collectIds);
    return result;
  }

  public static Set<Long> scrollKeysToSet(
      final SearchRequest request, final RestHighLevelClient esClient) throws IOException {
    final Set<Long> result = new HashSet<>();
    final Consumer<SearchHits> collectIds =
        (hits) -> result.addAll(map(hits.getHits(), SEARCH_HIT_ID_TO_LONG));
    scrollWith(request, esClient, collectIds, null, null);
    return result;
  }

  public static void refreshIndicesFor(
      final RestHighLevelClient esClient, final String indexPattern) {
    final var refreshRequest = new RefreshRequest(indexPattern);
    try {
      final RefreshResponse refresh =
          esClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
      if (refresh.getFailedShards() > 0) {
        LOGGER.warn("Unable to refresh indices: {}", indexPattern);
      }
    } catch (final Exception ex) {
      LOGGER.warn(String.format("Unable to refresh indices: %s", indexPattern), ex);
    }
  }

  private static final class DelegatingActionListener<Response>
      implements ActionListener<Response> {

    private final CompletableFuture<Response> future;
    private final Executor executorDelegate;

    private DelegatingActionListener(
        final CompletableFuture<Response> future, final Executor executor) {
      this.future = future;
      executorDelegate = executor;
    }

    @Override
    public void onResponse(final Response response) {
      executorDelegate.execute(() -> future.complete(response));
    }

    @Override
    public void onFailure(final Exception e) {
      executorDelegate.execute(() -> future.completeExceptionally(e));
    }
  }

  public enum QueryType {
    ONLY_RUNTIME,
    ALL
  }
}
