/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.util.CollectionUtil.throwAwayNullElements;
import static org.elasticsearch.index.query.QueryBuilders.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.MissingRequiredPropertyException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.HitEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.ScrollException;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.*;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ElasticsearchUtil {

  public static final int SCROLL_KEEP_ALIVE_MS = 60000;
  public static final int TERMS_AGG_SIZE = 10000;
  public static final int QUERY_MAX_SIZE = 10000;
  public static final int TOPHITS_AGG_SIZE = 100;
  public static final int UPDATE_RETRY_COUNT = 3;
  public static final Function<SearchHit, Long> SEARCH_HIT_ID_TO_LONG =
      (hit) -> Long.valueOf(hit.getId());
  public static final Function<SearchHit, String> SEARCH_HIT_ID_TO_STRING = SearchHit::getId;
  public static RequestOptions requestOptions = RequestOptions.DEFAULT;
  public static final Class<Map<String, Object>> MAP_CLASS =
      (Class<Map<String, Object>>) (Class<?>) Map.class;
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchUtil.class);

  public static void setRequestOptions(final RequestOptions newRequestOptions) {
    requestOptions = newRequestOptions;
  }

  public static SearchRequest createSearchRequest(final IndexTemplateDescriptor template) {
    return createSearchRequest(template, QueryType.ALL);
  }

  /* CREATE QUERIES */

  public static SearchRequest createSearchRequest(
      final IndexTemplateDescriptor template, final QueryType queryType) {
    final SearchRequest searchRequest = new SearchRequest(whereToSearch(template, queryType));
    return searchRequest;
  }

  public static String whereToSearch(
      final IndexTemplateDescriptor template, final QueryType queryType) {
    switch (queryType) {
      case ONLY_RUNTIME:
        return template.getFullQualifiedName();
      case ALL:
      default:
        return template.getAlias();
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
   *
   * @param queries
   * @return
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

  public static Query joinWithOr(final Query... queries) {
    final List<Query> notNullQueries = throwAwayNullElements(queries);

    return switch (notNullQueries.size()) {
      case 0 -> null;
      case 1 -> notNullQueries.get(0);
      default -> Query.of(q -> q.bool(b -> b.should(notNullQueries)));
    };
  }

  public static QueryBuilder joinWithOr(final Collection<QueryBuilder> queries) {
    return joinWithOr(queries.toArray(new QueryBuilder[queries.size()]));
  }

  /**
   * Join queries with AND clause. If 0 queries are passed for wrapping, then null is returned. If 1
   * parameter is passed, it will be returned back as ia. Otherwise, the new BoolQuery will be
   * created and returned.
   *
   * @param queries
   * @return
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

  /**
   * Join queries with AND clause. If 0 queries are passed for wrapping, then null is returned. If 1
   * parameter is passed, it will be returned back as is. Otherwise, a new BoolQuery will be created
   * and returned.
   *
   * @param queries Variable number of Query objects to join
   * @return A single Query combining all inputs with AND logic, or null if no queries provided
   */
  public static Query joinWithAnd(final Query... queries) {
    final List<Query> notNullQueries = throwAwayNullElements(queries);

    return switch (notNullQueries.size()) {
      case 0 -> null;
      case 1 -> notNullQueries.get(0);
      default -> Query.of(q -> q.bool(b -> b.must(notNullQueries)));
    };
  }

  public static BoolQueryBuilder createMatchNoneQuery() {
    return boolQuery().must(QueryBuilders.wrapperQuery("{\"match_none\": {}}"));
  }

  public static BoolQuery.Builder createMatchNoneQueryEs8() {
    return co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool()
        .must(m -> m.matchNone(mn -> mn));
  }

  public static void processBulkRequest(
      final ElasticsearchClient esClient,
      final BulkRequest.Builder bulkRequestBuilder,
      final long maxBulkRequestSizeInBytes) {
    processBulkRequest(esClient, bulkRequestBuilder, false, maxBulkRequestSizeInBytes);
  }

  public static String getFieldFromResponseObject(
      final co.elastic.clients.elasticsearch.core.SearchResponse<Map<String, Object>> response,
      final String fieldName) {
    if (response.hits().hits().size() != 1) {
      throw new IllegalArgumentException(
          "Expected exactly one document in response object " + response);
    }

    return String.valueOf(response.hits().hits().getFirst().source().get(fieldName));
  }

  public static Query idsQuery(final String... ids) {
    return Query.of(q -> q.ids(i -> i.values(Arrays.asList(ids))));
  }

  /**
   * A query that wraps another query and simply returns a constant score equal to the query boost
   * for every document in the query.
   *
   * @param query The query to wrap in a constant score query
   */
  public static Query constantScoreQuery(final Query query) {
    return Query.of(q -> q.constantScore(cs -> cs.filter(query)));
  }

  /* EXECUTE QUERY */

  public static void processBulkRequest(
      final ElasticsearchClient esClient,
      final BulkRequest.Builder bulkRequestBuilder,
      final boolean refreshImmediately,
      final long maxBulkRequestSizeInBytes) {
    try {
      final var bulkRequest = bulkRequestBuilder.build();
      LOGGER.debug("Execute batchRequest with {} requests", bulkRequest.operations().size());

      try (final var bulkIngester =
          createBulkIngester(esClient, maxBulkRequestSizeInBytes, refreshImmediately)) {
        bulkRequest.operations().forEach(bulkIngester::add);
      }
    } catch (final MissingRequiredPropertyException ignored) {
      // if bulk request has no operations calling .build() will throw an exception, we suppress
      // this as it is a no op.
    }
  }

  private static BulkIngester<Void> createBulkIngester(
      final ElasticsearchClient esClient,
      final long maxBulkRequestSizeInBytes,
      final boolean refreshImmediately) {
    final Refresh refreshVal;
    if (refreshImmediately) {
      refreshVal = Refresh.True;
    } else {
      refreshVal = Refresh.False;
    }
    return BulkIngester.of(
        b ->
            b.client(esClient)
                .maxOperations(100)
                .maxSize(maxBulkRequestSizeInBytes)
                .globalSettings(s -> s.refresh(refreshVal)));
  }

  /* MAP QUERY RESULTS */
  public static <T> List<T> mapSearchHits(
      final List<HitEntity> hits, final ObjectMapper objectMapper, final JavaType valueType) {
    return map(hits, h -> fromSearchHit(h.getSourceAsString(), objectMapper, valueType));
  }

  public static <T> List<T> mapSearchHits(
      final HitEntity[] searchHits, final ObjectMapper objectMapper, final Class<T> clazz) {
    return map(
        searchHits,
        (searchHit) -> fromSearchHit(searchHit.getSourceAsString(), objectMapper, clazz));
  }

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
      LOGGER.error(
          String.format(
              "Error while reading entity of type %s from Elasticsearch!", clazz.getName()),
          e);
      throw new OperateRuntimeException(
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
      LOGGER.error(
          String.format(
              "Error while reading entity of type %s from Elasticsearch!", valueType.toString()),
          e);
      throw new OperateRuntimeException(
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
    return scroll(
        searchRequest, clazz, objectMapper, esClient, null, searchHitsProcessor, aggsProcessor);
  }

  public static <T> List<T> scroll(
      final SearchRequest searchRequest,
      final Class<T> clazz,
      final ObjectMapper objectMapper,
      final RestHighLevelClient esClient,
      final Function<SearchHit, T> searchHitMapper,
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
      if (searchHitMapper != null) {
        result.addAll(mapSearchHits(hits.getHits(), searchHitMapper));
      } else {
        result.addAll(mapSearchHits(hits.getHits(), objectMapper, clazz));
      }

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

  public static <T> Stream<ResponseBody<T>> scrollAllStream(
      final ElasticsearchClient client,
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

  private static void clearScrollSilently(final ElasticsearchClient client, final String scrollId) {
    if (scrollId != null) {
      try {
        client.clearScroll(cs -> cs.scrollId(scrollId));
      } catch (final Exception e) {
        LOGGER.warn("Error occurred when clearing the scroll with id [{}]", scrollId, e);
      }
    }
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

  public static RequestOptions requestOptionsFor(final int maxSizeInBytes) {
    final RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
    options.setHttpAsyncResponseConsumerFactory(
        new HttpAsyncResponseConsumerFactory.HeapBufferedResponseConsumerFactory(maxSizeInBytes));
    return options.build();
  }

  public static SortOrder reverseOrder(final SortOrder sortOrder) {
    if (sortOrder.equals(SortOrder.ASC)) {
      return SortOrder.DESC;
    } else {
      return SortOrder.ASC;
    }
  }

  public static Query termsQuery(final String name, final Collection<?> values) {
    if (values.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(
          "Cannot use terms query with null value, trying to query ["
              + name
              + "] where terms field is "
              + values);
    }
    return Query.of(
        q ->
            q.terms(
                t ->
                    t.field(name)
                        .terms(
                            TermsQueryField.of(
                                tf -> tf.value(values.stream().map(FieldValue::of).toList())))));
  }

  public static <T> Query termsQuery(final String name, final T value) {
    if (value == null) {
      throw new IllegalArgumentException(
          "Cannot use terms query with null value, trying to query [" + name + "] with null value");
    }

    return termsQuery(name, Collections.singletonList(value));
  }

  public static SortOptions sortOrder(
      final String field, final co.elastic.clients.elasticsearch._types.SortOrder sortOrder) {
    return SortOptions.of(s -> s.field(f -> f.field(field).order(sortOrder)));
  }

  public enum QueryType {
    ONLY_RUNTIME,
    ALL
  }
}
