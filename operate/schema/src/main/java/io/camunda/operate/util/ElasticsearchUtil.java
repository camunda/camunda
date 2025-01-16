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
import static java.util.Arrays.asList;
import static org.elasticsearch.index.query.QueryBuilders.*;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.HitEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.*;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
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

  private static String whereToSearch(
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

  public static BoolQueryBuilder createMatchNoneQuery() {
    return boolQuery().must(QueryBuilders.wrapperQuery("{\"match_none\": {}}"));
  }

  public static void processBulkRequest(
      final RestHighLevelClient esClient,
      final BulkRequest bulkRequest,
      final long maxBulkRequestSizeInBytes)
      throws PersistenceException {
    processBulkRequest(esClient, bulkRequest, false, maxBulkRequestSizeInBytes);
  }

  /* EXECUTE QUERY */

  public static void processBulkRequest(
      final RestHighLevelClient esClient,
      final BulkRequest bulkRequest,
      final boolean refreshImmediately,
      final long maxBulkRequestSizeInBytes)
      throws PersistenceException {
    if (bulkRequest.estimatedSizeInBytes() > maxBulkRequestSizeInBytes) {
      divideLargeBulkRequestAndProcess(
          esClient, bulkRequest, refreshImmediately, maxBulkRequestSizeInBytes);
    } else {
      processLimitedBulkRequest(esClient, bulkRequest, refreshImmediately);
    }
  }

  private static void divideLargeBulkRequestAndProcess(
      final RestHighLevelClient esClient,
      final BulkRequest bulkRequest,
      final boolean refreshImmediately,
      final long maxBulkRequestSizeInBytes)
      throws PersistenceException {
    LOGGER.debug(
        "Bulk request has {} bytes > {} max bytes ({} requests). Will divide it into smaller bulk requests.",
        bulkRequest.estimatedSizeInBytes(),
        maxBulkRequestSizeInBytes,
        bulkRequest.requests().size());

    int requestCount = 0;
    final List<DocWriteRequest<?>> requests = bulkRequest.requests();

    BulkRequest limitedBulkRequest = new BulkRequest();
    while (requestCount < requests.size()) {
      final DocWriteRequest<?> nextRequest = requests.get(requestCount);
      if (nextRequest.ramBytesUsed() > maxBulkRequestSizeInBytes) {
        throw new PersistenceException(
            String.format(
                "One of the request with size of %d bytes is greater than max allowed %d bytes",
                nextRequest.ramBytesUsed(), maxBulkRequestSizeInBytes));
      }

      final long wholeSize = limitedBulkRequest.estimatedSizeInBytes() + nextRequest.ramBytesUsed();
      if (wholeSize < maxBulkRequestSizeInBytes) {
        limitedBulkRequest.add(nextRequest);
      } else {
        LOGGER.debug(
            "Submit bulk of {} requests, size {} bytes.",
            limitedBulkRequest.requests().size(),
            limitedBulkRequest.estimatedSizeInBytes());
        processLimitedBulkRequest(esClient, limitedBulkRequest, refreshImmediately);
        limitedBulkRequest = new BulkRequest();
        limitedBulkRequest.add(nextRequest);
      }
      requestCount++;
    }
    if (!limitedBulkRequest.requests().isEmpty()) {
      LOGGER.debug(
          "Submit bulk of {} requests, size {} bytes.",
          limitedBulkRequest.requests().size(),
          limitedBulkRequest.estimatedSizeInBytes());
      processLimitedBulkRequest(esClient, limitedBulkRequest, refreshImmediately);
    }
  }

  @SuppressWarnings("checkstyle:NestedIfDepth")
  private static void processLimitedBulkRequest(
      final RestHighLevelClient esClient, BulkRequest bulkRequest, final boolean refreshImmediately)
      throws PersistenceException {
    if (bulkRequest.requests().size() > 0) {
      try {
        LOGGER.debug("************* FLUSH BULK START *************");
        if (refreshImmediately) {
          bulkRequest = bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        }
        final BulkResponse bulkItemResponses = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        final BulkItemResponse[] items = bulkItemResponses.getItems();
        for (int i = 0; i < items.length; i++) {
          final BulkItemResponse responseItem = items[i];
          if (responseItem.isFailed() && !isEventConflictError(responseItem)) {

            if (isMissingIncident(responseItem)) {
              // the case when incident was already archived to dated index, but must be updated
              final DocWriteRequest<?> request = bulkRequest.requests().get(i);
              final String incidentId = extractIncidentId(responseItem.getFailure().getMessage());
              final String indexName =
                  getIndexNames(request.index() + "alias", asList(incidentId), esClient)
                      .get(incidentId);
              request.index(indexName);
              if (indexName == null) {
                LOGGER.warn("Index is not known for incident: " + incidentId);
              } else {
                esClient.update((UpdateRequest) request, RequestOptions.DEFAULT);
              }
            } else {
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
        }
        LOGGER.debug("************* FLUSH BULK FINISH *************");
      } catch (final IOException ex) {
        throw new PersistenceException(
            "Error when processing bulk request against Elasticsearch: " + ex.getMessage(), ex);
      }
    }
  }

  private static String extractIncidentId(final String errorMessage) {
    final Pattern fniPattern = Pattern.compile(".*\\[_doc\\]\\[(\\d*)\\].*");
    final Matcher matcher = fniPattern.matcher(errorMessage);
    matcher.matches();
    return matcher.group(1);
  }

  private static boolean isMissingIncident(final BulkItemResponse responseItem) {
    return responseItem.getIndex().contains(IncidentTemplate.INDEX_NAME)
        && responseItem.getFailure().getStatus().equals(RestStatus.NOT_FOUND);
  }

  private static boolean isEventConflictError(final BulkItemResponse responseItem) {
    return responseItem.getIndex().contains(EventTemplate.INDEX_NAME)
        && responseItem.getFailure().getStatus().equals(RestStatus.CONFLICT);
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

  public static void scroll(
      final SearchRequest searchRequest,
      final Consumer<SearchHits> searchHitsProcessor,
      final RestHighLevelClient esClient)
      throws IOException {
    scroll(searchRequest, searchHitsProcessor, esClient, SCROLL_KEEP_ALIVE_MS);
  }

  public static void scroll(
      final SearchRequest searchRequest,
      final Consumer<SearchHits> searchHitsProcessor,
      final RestHighLevelClient esClient,
      final long scrollKeepAlive)
      throws IOException {
    final var scrollKeepAliveTimeValue = TimeValue.timeValueMillis(scrollKeepAlive);

    searchRequest.scroll(scrollKeepAliveTimeValue);
    SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    String scrollId = response.getScrollId();
    SearchHits hits = response.getHits();

    while (hits.getHits().length != 0) {

      // call response processor
      if (searchHitsProcessor != null) {
        searchHitsProcessor.accept(response.getHits());
      }

      final SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(scrollKeepAliveTimeValue);

      response = esClient.scroll(scrollRequest, RequestOptions.DEFAULT);

      scrollId = response.getScrollId();
      hits = response.getHits();
    }

    clearScroll(scrollId, esClient);
  }

  public static void scrollWith(
      final SearchRequest searchRequest,
      final RestHighLevelClient esClient,
      final Consumer<SearchHits> searchHitsProcessor)
      throws IOException {
    scrollWith(searchRequest, esClient, searchHitsProcessor, null, null);
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

  public static List<Long> scrollKeysToList(
      final SearchRequest request, final RestHighLevelClient esClient) throws IOException {
    final List<Long> result = new ArrayList<>();

    final Consumer<SearchHits> collectIds =
        (hits) -> {
          result.addAll(map(hits.getHits(), SEARCH_HIT_ID_TO_LONG));
        };

    scrollWith(request, esClient, collectIds, null, collectIds);
    return result;
  }

  public static <T> List<T> scrollFieldToList(
      final SearchRequest request, final String fieldName, final RestHighLevelClient esClient)
      throws IOException {
    final List<T> result = new ArrayList<>();
    final Function<SearchHit, T> searchHitFieldToString =
        (searchHit) -> (T) searchHit.getSourceAsMap().get(fieldName);

    final Consumer<SearchHits> collectFields =
        (hits) -> {
          result.addAll(map(hits.getHits(), searchHitFieldToString));
        };

    scrollWith(request, esClient, collectFields, null, collectFields);
    return result;
  }

  public static Set<String> scrollIdsToSet(
      final SearchRequest request, final RestHighLevelClient esClient) throws IOException {
    final Set<String> result = new HashSet<>();

    final Consumer<SearchHits> collectIds =
        (hits) -> {
          result.addAll(map(hits.getHits(), SEARCH_HIT_ID_TO_STRING));
        };
    scrollWith(request, esClient, collectIds, null, collectIds);
    return result;
  }

  public static Map<String, String> getIndexNames(
      final String aliasName, final Collection<String> ids, final RestHighLevelClient esClient) {

    final Map<String, String> indexNames = new HashMap<>();

    final SearchRequest piRequest =
        new SearchRequest(aliasName)
            .source(
                new SearchSourceBuilder()
                    .query(idsQuery().addIds(ids.toArray(String[]::new)))
                    .fetchSource(false));
    try {
      scrollWith(
          piRequest,
          esClient,
          sh -> {
            indexNames.putAll(
                Arrays.stream(sh.getHits())
                    .collect(
                        Collectors.toMap(
                            hit -> {
                              return hit.getId();
                            },
                            hit -> {
                              return hit.getIndex();
                            })));
          });
    } catch (final IOException e) {
      throw new OperateRuntimeException(e.getMessage(), e);
    }
    return indexNames;
  }

  public static Map<String, String> getIndexNames(
      final AbstractTemplateDescriptor template,
      final Collection<String> ids,
      final RestHighLevelClient esClient) {

    final Map<String, String> indexNames = new HashMap<>();

    final SearchRequest piRequest =
        ElasticsearchUtil.createSearchRequest(template)
            .source(
                new SearchSourceBuilder()
                    .query(idsQuery().addIds(ids.toArray(String[]::new)))
                    .fetchSource(false));
    try {
      scrollWith(
          piRequest,
          esClient,
          sh -> {
            indexNames.putAll(
                Arrays.stream(sh.getHits())
                    .collect(
                        Collectors.toMap(
                            hit -> {
                              return hit.getId();
                            },
                            hit -> {
                              return hit.getIndex();
                            })));
          });
    } catch (final IOException e) {
      throw new OperateRuntimeException(e.getMessage(), e);
    }
    return indexNames;
  }

  public static Map<String, List<String>> getIndexNamesAsList(
      final AbstractTemplateDescriptor template,
      final Collection<String> ids,
      final RestHighLevelClient esClient) {

    final Map<String, List<String>> indexNames = new ConcurrentHashMap<>();

    final SearchRequest piRequest =
        ElasticsearchUtil.createSearchRequest(template)
            .source(
                new SearchSourceBuilder()
                    .query(idsQuery().addIds(ids.toArray(String[]::new)))
                    .fetchSource(false));
    try {
      scrollWith(
          piRequest,
          esClient,
          sh -> {
            Arrays.stream(sh.getHits())
                .collect(
                    Collectors.groupingBy(
                        SearchHit::getId,
                        Collectors.mapping(SearchHit::getIndex, Collectors.toList())))
                .forEach(
                    (key, value) ->
                        indexNames.merge(
                            key,
                            value,
                            (v1, v2) -> {
                              v1.addAll(v2);
                              return v1;
                            }));
          });
    } catch (final IOException e) {
      throw new OperateRuntimeException(e.getMessage(), e);
    }
    return indexNames;
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
