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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.ScrollException;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.ListUtils;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
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
  public static final int AGGREGATION_TERMS_SIZE = 20000;
  public static final int SCROLL_KEEP_ALIVE_MS = 60000;
  public static final int INTERNAL_SCROLL_KEEP_ALIVE_MS =
      30000; // this scroll timeout value is used for reindex and delete queries
  public static final int QUERY_MAX_SIZE = 10000;
  public static final int UPDATE_RETRY_COUNT = 3;
  public static final Function<SearchHit, Long> SEARCH_HIT_ID_TO_LONG =
      (hit) -> Long.valueOf(hit.getId());
  public static final Function<SearchHit, String> SEARCH_HIT_ID_TO_STRING = SearchHit::getId;
  public static final Class<Map<String, Object>> MAP_CLASS =
      (Class<Map<String, Object>>) (Class<?>) Map.class;

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
    final QueryBuilder query = org.elasticsearch.index.query.QueryBuilders.idsQuery().addIds(id);

    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(descriptor, queryType)
            .source(
                new SearchSourceBuilder()
                    .query(org.elasticsearch.index.query.QueryBuilders.constantScoreQuery(query)));

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

  /**
   * Scrolls through all search results using ES8 client and collects results into a list, with
   * chunking support for large ID lists.
   *
   * @param client ES8 client
   * @param ids List of IDs to process in chunks
   * @param chunkSize Maximum number of IDs per chunk
   * @param searchRequestBuilderFactory Factory to create search request builder for each chunk
   * @param docClass Document class type
   * @param <T> Type of documents
   * @param <ID> Type of IDs
   * @return List of document sources
   */
  public static <T, ID> List<T> scrollInChunks(
      final co.elastic.clients.elasticsearch.ElasticsearchClient client,
      final List<ID> ids,
      final int chunkSize,
      final Function<List<ID>, co.elastic.clients.elasticsearch.core.SearchRequest.Builder>
          searchRequestBuilderFactory,
      final Class<T> docClass) {
    final var result = new ArrayList<T>();
    for (final var chunk : ListUtils.partition(ids, chunkSize)) {
      result.addAll(scrollAllToList(client, searchRequestBuilderFactory.apply(chunk), docClass));
    }
    return result;
  }

  public static <T, ID> List<T> scrollInChunks(
      final List<ID> list,
      final int chunkSize,
      final Function<List<ID>, SearchRequest> chunkToSearchRequest,
      final Class<T> clazz,
      final ObjectMapper objectMapper,
      final RestHighLevelClient esClient)
      throws IOException {
    final var result = new ArrayList<T>();
    for (final var chunk : ListUtils.partition(list, chunkSize)) {
      result.addAll(scroll(chunkToSearchRequest.apply(chunk), clazz, objectMapper, esClient));
    }
    return result;
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

  /**
   * Scrolls through all search results using ES8 client and collects document IDs with their index
   * names into a map.
   *
   * @param client ES8 client
   * @param searchRequestBuilder Search request builder
   * @return Map of document ID to index name
   */
  public static Map<String, String> scrollIdsWithIndexToMap(
      final co.elastic.clients.elasticsearch.ElasticsearchClient client,
      final co.elastic.clients.elasticsearch.core.SearchRequest.Builder searchRequestBuilder) {
    final Map<String, String> result = new LinkedHashMap<>();
    scrollAllStream(client, searchRequestBuilder, MAP_CLASS)
        .flatMap(response -> response.hits().hits().stream())
        .forEach(hit -> result.put(hit.id(), hit.index()));
    return result;
  }

  public static Map<String, String> scrollIdsWithIndexToMap(
      final SearchRequest request, final RestHighLevelClient esClient) throws IOException {
    final Map<String, String> result = new LinkedHashMap<>();

    final Consumer<SearchHits> collectIds =
        (hits) ->
            result.putAll(
                Stream.of(hits.getHits())
                    .collect(Collectors.toMap(SearchHit::getId, SearchHit::getIndex)));

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

  // ============ ES8 Query Helper Methods ============

  /**
   * Creates a match-none query for ES8 that returns no results.
   *
   * @return BoolQuery.Builder configured to match no documents
   */
  public static co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder
      createMatchNoneQueryEs8() {
    return co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool()
        .must(m -> m.matchNone(mn -> mn));
  }

  /**
   * Creates a terms query for ES8 with a collection of values.
   *
   * @param name Field name
   * @param values Collection of values to match
   * @return Query with terms condition
   */
  public static Query termsQuery(final String name, final Collection<?> values) {
    if (values.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(
          "Cannot use terms query with null value, trying to query ["
              + name
              + "] where terms field is "
              + values);
    }

    return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
        q ->
            q.terms(
                t ->
                    t.field(name)
                        .terms(
                            TermsQueryField.of(
                                tf -> tf.value(values.stream().map(FieldValue::of).toList())))));
  }

  /**
   * Creates a terms query for ES8 with a single value.
   *
   * @param name Field name
   * @param value Single value to match
   * @return Query with terms condition
   */
  public static <T> Query termsQuery(final String name, final T value) {
    if (value == null) {
      throw new IllegalArgumentException(
          "Cannot use terms query with null value, trying to query [" + name + "] with null value");
    }

    if (value.getClass().isArray()) {
      throw new IllegalStateException(
          "Cannot pass an array to the singleton terms query, must pass a single value");
    }

    return termsQuery(name, List.of(value));
  }

  /**
   * Joins multiple ES8 queries with AND logic. Returns null if no queries provided, single query if
   * only one provided, or a bool query with must clauses for multiple queries.
   *
   * @param queries Queries to join
   * @return Combined query or null
   */
  public static Query joinWithAnd(final Query... queries) {
    final var notNullQueries = throwAwayNullElements(queries);

    if (notNullQueries.isEmpty()) {
      return null;
    } else if (notNullQueries.size() == 1) {
      return notNullQueries.get(0);
    } else {
      return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
          q -> q.bool(b -> b.must(notNullQueries)));
    }
  }

  /**
   * Creates a match-all query for ES8.
   *
   * @return Query that matches all documents
   */
  public static Query matchAllQueryEs8() {
    return Query.of(q -> q.matchAll(m -> m));
  }

  /**
   * Creates an ES8 ids query for the given document IDs.
   *
   * @param ids Document IDs to match
   * @return Query that matches documents with the specified IDs
   */
  public static Query idsQuery(final String... ids) {
    return Query.of(q -> q.ids(i -> i.values(Arrays.asList(ids))));
  }

  /**
   * Wraps a query in a constant_score query, which assigns all matching documents a relevance score
   * equal to the boost parameter (default 1.0).
   *
   * @param query The query to wrap
   * @return Query with constant scoring applied
   */
  public static Query constantScoreQuery(final Query query) {
    return Query.of(q -> q.constantScore(cs -> cs.filter(query)));
  }

  /**
   * Creates an ES8 exists query that matches documents containing a value for the specified field.
   *
   * @param field The field name to check for existence
   * @return Query that matches documents where the field exists
   */
  public static Query existsQuery(final String field) {
    return Query.of(q -> q.exists(e -> e.field(field)));
  }

  /**
   * Creates a bool query that must NOT match the provided query.
   *
   * @param query The query to negate
   * @return Query that does not match the provided query
   */
  public static Query mustNotQuery(final Query query) {
    return Query.of(q -> q.bool(b -> b.mustNot(query)));
  }

  /**
   * Joins multiple ES8 queries with OR logic. Returns null if no queries provided, single query if
   * only one provided, or a bool query with should clauses for multiple queries.
   *
   * @param queries Queries to join
   * @return Combined query or null
   */
  public static Query joinWithOr(final Query... queries) {
    final var notNullQueries = throwAwayNullElements(queries);

    if (notNullQueries.isEmpty()) {
      return null;
    } else if (notNullQueries.size() == 1) {
      return notNullQueries.get(0);
    } else {
      return Query.of(q -> q.bool(b -> b.should(notNullQueries)));
    }
  }

  /**
   * Creates an ES8 range query builder for the specified field.
   *
   * @param field Field name to apply range on
   * @return A RangeQueryBuilder for chaining range operations
   */
  public static RangeQueryBuilder rangeQuery(final String field) {
    return new RangeQueryBuilder(field);
  }

  /**
   * Creates an ES8 script sort option.
   *
   * @param scriptSource The inline script source
   * @param scriptSortType The type of script sort (STRING or NUMBER)
   * @param sortOrder The sort order
   * @return SortOptions configured for script sorting
   */
  public static SortOptions scriptSort(
      final String scriptSource,
      final co.elastic.clients.elasticsearch._types.ScriptSortType scriptSortType,
      final co.elastic.clients.elasticsearch._types.SortOrder sortOrder) {
    return SortOptions.of(
        s ->
            s.script(
                sc ->
                    sc.script(
                            script ->
                                script
                                    .lang(
                                        co.elastic.clients.elasticsearch._types.ScriptLanguage
                                            .Painless)
                                    .source(scriptSource))
                        .type(scriptSortType)
                        .order(sortOrder)));
  }

  // ===========================================================================================
  // ES8 Scroll Helper Methods
  // ===========================================================================================

  /**
   * Scrolls through all search results using the ES8 client and returns a stream of response
   * bodies.
   *
   * @param client ES8 client
   * @param searchRequestBuilder Search request builder
   * @param docClass Document class type
   * @param <T> Type of documents
   * @return Stream of response bodies containing hits
   * @throws ScrollException if scroll operation fails
   */
  public static <T> Stream<ResponseBody<T>> scrollAllStream(
      final co.elastic.clients.elasticsearch.ElasticsearchClient client,
      final co.elastic.clients.elasticsearch.core.SearchRequest.Builder searchRequestBuilder,
      final Class<T> docClass) {
    final AtomicReference<String> lastScrollId = new AtomicReference<>(null);

    final var scrollKeepAlive = Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS + "ms"));

    final co.elastic.clients.elasticsearch.core.SearchResponse<T> searchRes;
    searchRequestBuilder.scroll(scrollKeepAlive);

    try {
      searchRes = client.search(searchRequestBuilder.build(), docClass);
      lastScrollId.set(searchRes.scrollId());

      if (searchRes.hits().hits().isEmpty()) {
        clearScrollSilently(client, lastScrollId.get());
        return Stream.of(searchRes);
      }
    } catch (final IOException e) {
      throw new ScrollException("Error during scroll where initial search request failed", e);
    }

    final var scrollStream =
        Stream.generate(
                () -> {
                  final ScrollResponse<T> response;
                  try {
                    response =
                        client.scroll(
                            r -> r.scrollId(lastScrollId.get()).scroll(scrollKeepAlive), docClass);

                    lastScrollId.set(response.scrollId());

                    if (response.hits().hits().isEmpty()) {
                      clearScrollSilently(client, lastScrollId.get());
                      return null;
                    }
                  } catch (final IOException e) {
                    clearScrollSilently(client, lastScrollId.get());
                    throw new ScrollException(
                        "Error during scroll with id: " + lastScrollId.get(), e);
                  }
                  return response;
                })
            .takeWhile(Objects::nonNull);

    return Stream.concat(Stream.of(searchRes), scrollStream);
  }

  /**
   * Clears scroll context silently, logging any errors that occur.
   *
   * @param client ES8 client
   * @param scrollId Scroll ID to clear
   */
  private static void clearScrollSilently(
      final co.elastic.clients.elasticsearch.ElasticsearchClient client, final String scrollId) {
    if (scrollId != null) {
      try {
        client.clearScroll(cs -> cs.scrollId(scrollId));
      } catch (final Exception e) {
        LOGGER.warn("Error occurred when clearing the scroll with id [{}]", scrollId, e);
      }
    }
  }

  /**
   * Scrolls through all search results using ES8 client and collects results into a list.
   *
   * @param client ES8 client
   * @param searchRequestBuilder Search request builder
   * @param docClass Document class type
   * @param <T> Type of documents
   * @return List of document sources
   */
  public static <T> List<T> scrollAllToList(
      final co.elastic.clients.elasticsearch.ElasticsearchClient client,
      final co.elastic.clients.elasticsearch.core.SearchRequest.Builder searchRequestBuilder,
      final Class<T> docClass) {
    return scrollAllStream(client, searchRequestBuilder, docClass)
        .flatMap(response -> response.hits().hits().stream())
        .map(co.elastic.clients.elasticsearch.core.search.Hit::source)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * Scrolls through all search results using ES8 client and collects a specific field's Long values
   * into a set.
   *
   * @param client ES8 client
   * @param searchRequestBuilder Search request builder
   * @param fieldName The field name to extract Long values from
   * @return Set of Long values
   */
  public static Set<Long> scrollFieldToLongSet(
      final co.elastic.clients.elasticsearch.ElasticsearchClient client,
      final co.elastic.clients.elasticsearch.core.SearchRequest.Builder searchRequestBuilder,
      final String fieldName) {
    final Set<Long> result = new HashSet<>();
    scrollAllStream(client, searchRequestBuilder, MAP_CLASS)
        .flatMap(response -> response.hits().hits().stream())
        .map(co.elastic.clients.elasticsearch.core.search.Hit::source)
        .filter(Objects::nonNull)
        .forEach(
            source -> {
              final var value = source.get(fieldName);
              if (value != null) {
                result.add(((Number) value).longValue());
              }
            });
    return result;
  }

  // ===========================================================================================
  // ES8 Sort Helper Methods
  // ===========================================================================================

  /**
   * Creates an ES8 SortOptions for a field with specified order.
   *
   * @param field The field name to sort by
   * @param sortOrder The sort order (Asc or Desc)
   * @return SortOptions configured for the field
   */
  public static SortOptions sortOrder(
      final String field, final co.elastic.clients.elasticsearch._types.SortOrder sortOrder) {
    return SortOptions.of(s -> s.field(f -> f.field(field).order(sortOrder)));
  }

  /**
   * Creates an ES8 SortOptions for a field with specified order and missing value handling.
   *
   * @param field The field name to sort by
   * @param sortOrder The sort order (Asc or Desc)
   * @param missing How to handle missing values ("_first", "_last", or a custom value)
   * @return SortOptions configured for the field with missing value handling
   */
  public static SortOptions sortOrder(
      final String field,
      final co.elastic.clients.elasticsearch._types.SortOrder sortOrder,
      final String missing) {
    return SortOptions.of(s -> s.field(f -> f.field(field).order(sortOrder).missing(missing)));
  }

  // ===========================================================================================
  // Inner Classes
  // ===========================================================================================

  /**
   * Executes a bulk request with the given operations and refresh policy.
   *
   * @param client the Elasticsearch client
   * @param operations the list of bulk operations to execute
   * @param refresh the refresh policy to use
   * @throws IOException if an I/O error occurs
   * @throws TasklistRuntimeException if the bulk request contains errors
   */
  public static void executeBulkRequest(
      final ElasticsearchClient client, final List<BulkOperation> operations, final Refresh refresh)
      throws IOException {
    if (operations.isEmpty()) {
      return;
    }

    final var bulkRequest =
        co.elastic.clients.elasticsearch.core.BulkRequest.of(
            b -> b.operations(operations).refresh(refresh));

    final var bulkResponse = client.bulk(bulkRequest);

    if (bulkResponse.errors()) {
      final var errorMessages =
          bulkResponse.items().stream()
              .filter(item -> item.error() != null)
              .map(item -> item.error().reason())
              .collect(Collectors.joining(", "));
      throw new TasklistRuntimeException("Bulk request failed. Errors: " + errorMessages);
    }
  }

  /**
   * Converts an array of search_after values to ES8 FieldValue list for pagination.
   *
   * @param searchAfter Array of sort values from previous search result
   * @return List of FieldValue objects for ES8 searchAfter parameter
   */
  public static List<co.elastic.clients.elasticsearch._types.FieldValue> searchAfterToFieldValues(
      final Object[] searchAfter) {
    return Arrays.stream(searchAfter)
        .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
        .toList();
  }

  /** Builder class for creating ES8 range queries with a fluent API. */
  public static class RangeQueryBuilder {
    private final String field;
    private Object gt;
    private Object gte;
    private Object lt;
    private Object lte;

    public RangeQueryBuilder(final String field) {
      this.field = field;
    }

    public RangeQueryBuilder gt(final Object value) {
      gt = value;
      return this;
    }

    public RangeQueryBuilder gte(final Object value) {
      gte = value;
      return this;
    }

    public RangeQueryBuilder lt(final Object value) {
      lt = value;
      return this;
    }

    public RangeQueryBuilder lte(final Object value) {
      lte = value;
      return this;
    }

    public Query build() {
      final var untypedBuilder =
          new co.elastic.clients.elasticsearch._types.query_dsl.UntypedRangeQuery.Builder();
      untypedBuilder.field(field);

      if (gt != null) {
        untypedBuilder.gt(co.elastic.clients.json.JsonData.of(gt));
      }
      if (gte != null) {
        untypedBuilder.gte(co.elastic.clients.json.JsonData.of(gte));
      }
      if (lt != null) {
        untypedBuilder.lt(co.elastic.clients.json.JsonData.of(lt));
      }
      if (lte != null) {
        untypedBuilder.lte(co.elastic.clients.json.JsonData.of(lte));
      }

      return co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.range()
          .untyped(untypedBuilder.build())
          .build()
          ._toQuery();
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
