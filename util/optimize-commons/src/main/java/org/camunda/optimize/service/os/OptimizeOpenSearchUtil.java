/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os;

import jakarta.ws.rs.NotSupportedException;
import org.opensearch.client.opensearch.indices.IndexSettings;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;

public abstract class OptimizeOpenSearchUtil {

  public static IndexSettings.Builder addStaticSetting(final String key,
                                                       final int value,
                                                       final IndexSettings.Builder contentBuilder) {
    if (NUMBER_OF_SHARDS_SETTING.equalsIgnoreCase(key)) {
      return contentBuilder.numberOfShards(Integer.toString(value));
    } else {
      throw new NotSupportedException("Cannot set property " + value + " for OpenSearch settings. Operation not " +
                                        "supported");
    }
  }

// TODO uncomment with OPT-7229
//  public static final String ZEEBE_INDEX_DELIMITER = "_";
//  public static final String SCROLL_KEEP_ALIVE_MS = "60000ms";
//  public static final String INTERNAL_SCROLL_KEEP_ALIVE_MS =
//    "30000ms"; // this scroll timeout value is used for reindex and delete q
//  public static final int QUERY_MAX_SIZE = 10000;
//  public static final int TERMS_AGG_SIZE = 10000;
//  public static final int TOPHITS_AGG_SIZE = 100;
//  public static final int UPDATE_RETRY_COUNT = 3;
//  public static final Function<Hit, Long> SEARCH_HIT_ID_TO_LONG = (hit) -> Long.valueOf(hit.id());
//  public static final Function<Hit<?>, String> SEARCH_HIT_ID_TO_STRING = Hit::id;
//  private static final Logger LOGGER = LoggerFactory.getLogger(OptimizeOpenSearchUtil.class);
//
//  public static <T> List<T> scroll(
//    SearchRequest searchRequest, Class<T> clazz, OpenSearchClient osClient) throws IOException {
//
//    String scrollId = null;
//    try {
//      SearchResponse<T> response = osClient.search(searchRequest, clazz);
//
//      final List<T> result = new ArrayList<>();
//      scrollId = response.scrollId();
//      List<Hit<T>> hits = response.hits().hits();
//
//      while (hits.size() != 0) {
//        result.addAll(hits.stream().map(Hit::source).toList());
//
//        final ScrollRequest scrollRequest =
//          new ScrollRequest.Builder()
//            .scrollId(scrollId)
//            .scroll(Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS)))
//            .build();
//
//        response = osClient.scroll(scrollRequest, clazz);
//
//        scrollId = response.scrollId();
//        hits = response.hits().hits();
//      }
//      return result;
//
//    } finally {
//      if (scrollId != null) {
//        clearScroll(scrollId, osClient);
//      }
//    }
//  }
//
//  public static void clearScroll(String scrollId, OpenSearchClient osClient) {
//    if (scrollId != null) {
//      // clear the scroll
//      final ClearScrollRequest clearScrollRequest =
//        new ClearScrollRequest.Builder().scrollId(scrollId).build();
//
//      try {
//        osClient.clearScroll(clearScrollRequest);
//      } catch (Exception e) {
//        LOGGER.warn("Error occurred when clearing the scroll with id [{}]", scrollId);
//      }
//    }
//  }
///*
//  public static Query joinWithAnd(ObjectBuilder... queries) {
//    final List<ObjectBuilder> notNullQueries = throwAwayNullElements(queries);
//    switch (notNullQueries.size()) {
//      case 0:
//        return null;
//      default:
//        final BoolQuery.Builder boolQ = boolQuery();
//        for (ObjectBuilder query : notNullQueries) {
//          boolQ.must((Query) query.build());
//        }
//        return new Query.Builder().bool(boolQ.build()).build();
//    }
//  }
//
//  public static Query.Builder joinQueryBuilderWithAnd(ObjectBuilder... queries) {
//    final List<ObjectBuilder> notNullQueries = throwAwayNullElements(queries);
//    final Query.Builder queryBuilder = new Query.Builder();
//    switch (notNullQueries.size()) {
//      case 0:
//        return null;
//      default:
//        final BoolQuery.Builder boolQ = boolQuery();
//        for (ObjectBuilder query : notNullQueries) {
//          boolQ.must((Query) query.build());
//        }
//        queryBuilder.bool(boolQ.build());
//        return queryBuilder;
//    }
//  }
//
// */
//
//  public static <T> T fromSearchHit(
//    String searchHitString, ObjectMapper objectMapper, Class<T> clazz) {
//    final T entity;
//    try {
//      entity = objectMapper.readValue(searchHitString, clazz);
//    } catch (IOException e) {
//      throw new OptimizeRuntimeException(
//        String.format(
//          "Error while reading entity of type %s from OpenSearch!", clazz.getName()),
//        e);
//    }
//    return entity;
//  }
//
//  public static CompletableFuture<ScrollResponse<Object>> scrollAsync(
//    final ScrollRequest scrollRequest,
//    final Executor executor,
//    final OpenSearchAsyncClient osClient) {
//    final var searchFuture = new CompletableFuture<SearchResponse>();
//    try {
//      final CompletableFuture<ScrollResponse<Object>> response =
//        osClient.scroll(scrollRequest, Object.class);
//      return response;
//    } catch (IOException e) {
//      throw new OptimizeRuntimeException(e.getMessage());
//    }
//  }
//
//  public static BoolQuery.Builder boolQuery() {
//    return new BoolQuery.Builder();
//  }
//
//  public static CompletableFuture<DeleteByQueryResponse> deleteByQueryAsync(
//    final DeleteByQueryRequest deleteRequest,
//    final Executor executor,
//    final OpenSearchAsyncClient osClient) {
//    try {
//      return osClient.deleteByQuery(deleteRequest);
//    } catch (IOException e) {
//      throw new OptimizeRuntimeException(e.getMessage());
//    }
//  }

//  public static CompletableFuture<ReindexResponse> reindexAsync(
//    final ReindexRequest reindexRequest,
//    final Executor executor,
//    final OpenSearchAsyncClient osClient) {
//    try {
//      return osClient.reindex(reindexRequest);
//    } catch (IOException e) {
//      throw new OptimizeRuntimeException(e.getMessage());
//    }
//  }
//
//  public static void processBulkRequest(OpenSearchClient osClient, BulkRequest bulkRequest)
//    throws PersistenceException {
//
//    if (bulkRequest.operations().size() > 0) {
//      try {
//        LOGGER.debug("************* FLUSH BULK START *************");
//        final BulkResponse bulkItemResponses = osClient.bulk(bulkRequest);
//        final List<BulkResponseItem> items = bulkItemResponses.items();
//        for (BulkResponseItem responseItem : items) {
//          if (responseItem.error() != null) {
//            // TODO check how to log the error for OpenSearch;
//            LOGGER.error(
//              String.format(
//                "%s failed for type [%s] and id [%s]: %s",
//                responseItem.operationType(),
//                responseItem.index(),
//                responseItem.id(),
//                responseItem.error().reason()),
//              "error on OpenSearch BulkRequest");
//            throw new PersistenceException(
//              "Operation failed: " + responseItem.error().reason(),
//              new OptimizeRuntimeException(responseItem.error().reason()),
//              Integer.valueOf(responseItem.id()));
//          }
//        }
//        LOGGER.debug("************* FLUSH BULK FINISH *************");
//      } catch (IOException ex) {
//        throw new PersistenceException(
//          "Error when processing bulk request against OpenSearch: " + ex.getMessage(), ex);
//      }
//    }
//  }
//
//  public static void refreshIndicesFor(final OpenSearchClient osClient, final String indexPattern) {
//    final var refreshRequest = new RefreshRequest.Builder().index(List.of(indexPattern)).build();
//    try {
//      final RefreshResponse refresh = osClient.indices().refresh(refreshRequest);
//      if (refresh.shards().failures().size() > 0) {
//        LOGGER.warn("Unable to refresh indices: {}", indexPattern);
//      }
//    } catch (Exception ex) {
//      LOGGER.warn(String.format("Unable to refresh indices: %s", indexPattern), ex);
//    }
//  }
//
//  public static <T> List<T> mapSearchHits(
//    List<Hit> searchHits, ObjectMapper objectMapper, JavaType valueType) {
//    return map(searchHits, (searchHit) -> objectMapper.convertValue(searchHit.source(), valueType));
//  }
//
//  public static CompletableFuture<SearchResponse<Object>> searchAsync(
//    final SearchRequest searchRequest,
//    final Executor executor,
//    final OpenSearchAsyncClient osClient) {
//    final var searchFuture = new CompletableFuture<SearchResponse>();
//
//    try {
//      return osClient.search(searchRequest, Object.class);
//    } catch (IOException e) {
//      throw new OptimizeRuntimeException(e.getMessage());
//    }
//  }
//
//  public static <R> void scrollWith(
//    SearchRequest.Builder searchRequest,
//    OpenSearchClient osClient,
//    Consumer<List<Hit<R>>> searchHitsProcessor,
//    Consumer<Map<String, Aggregate>> aggsProcessor,
//    Consumer<HitsMetadata<R>> firstResponseConsumer,
//    Class<R> resultClass
//  ) throws IOException {
//    searchRequest.scroll(Time.of(t -> t.time(OptimizeOpenSearchUtil.INTERNAL_SCROLL_KEEP_ALIVE_MS)));
//    SearchResponse<R> response = osClient.search(searchRequest.build(), resultClass);
//
//    if (firstResponseConsumer != null) {
//      firstResponseConsumer.accept(response.hits());
//    }
//
//    //call aggregations processor
//    if (aggsProcessor != null) {
//      aggsProcessor.accept(response.aggregations());
//    }
//
//    String scrollId = response.scrollId();
//    HitsMetadata<R> hits = response.hits();
//    while (hits.hits().size() != 0) {
//      if (firstResponseConsumer != null) {
//        firstResponseConsumer.accept(response.hits());
//      }
//
//      final ScrollRequest.Builder scrollRequest = new ScrollRequest.Builder();
//      scrollRequest.scrollId(scrollId);
//      scrollRequest.scroll(Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS)));
//      response = osClient.scroll(scrollRequest.build(), resultClass);
//
//      scrollId = response.scrollId();
//      hits = response.hits();
//    }
//
//    clearScroll(scrollId, osClient);
//  }
///*
//  public static String whereToSearch(
//    TemplateDescriptor template, OptimizeOpenSearchUtil.QueryType queryType) {
//    switch (queryType) {
//      case ONLY_RUNTIME:
//        return template.getFullQualifiedName();
//      case ALL:
//      default:
//        return template.getAlias();
//    }
//  }*/
//
//  public enum QueryType {
//    ONLY_RUNTIME,
//    ALL
//  }
//
//  public static <T> List<T> mapSearchHits(
//    List<Hit> searchHits, ObjectMapper objectMapper, Class<T> clazz) {
//    return map(
//      searchHits,
//      (searchHit) -> fromSearchHit(searchHit.source().toString(), objectMapper, clazz));
//  }
//
//  public static <T> List<T> scrollFieldToList(
//    SearchRequest.Builder request, String fieldName, OpenSearchClient esClient)
//    throws IOException {
//    final List<T> result = new ArrayList<>();
//
//    final Function<Hit<Object>, T> searchHitFieldToString =
//      (searchHit) ->
//        (T)
//          ((LinkedHashMap) searchHit.source())
//            .get(fieldName); // searchHit.getSourceAsMap().get(fieldName);
//
//    final Consumer<List<Hit<Object>>> collectFields =
//      (hits) -> result.addAll(map(hits, searchHitFieldToString));
//
//    scrollWith(request, esClient, collectFields, null, null, Object.class);
//    return result;
//  }
//
//  public static SearchRequest.Builder createSearchRequest(TemplateDescriptor template) {
//    return createSearchRequest(template, OptimizeOpenSearchUtil.QueryType.ALL);
//  }
//
//  public static SearchRequest.Builder createSearchRequest(
//    TemplateDescriptor template, OptimizeOpenSearchUtil.QueryType queryType) {
//    final SearchRequest.Builder builder = new SearchRequest.Builder();
//    builder.index(whereToSearch(template, queryType));
//    return builder;
//  }
//
//  public static <T extends OptimizeDto> List<T> scroll(
//    SearchRequest.Builder searchRequest,
//    Class<T> clazz,
//    OpenSearchClient osClient)
//    throws IOException {
//    return scroll(searchRequest, clazz, osClient, null, null);
//  }
//
//  public static <T> List<T> scroll(
//    SearchRequest.Builder searchRequest,
//    Class<T> clazz,
//    OpenSearchClient osClient,
//    Consumer<HitsMetadata<T>> searchHitsProcessor,
//    Consumer<Map<String, Aggregate>> aggsProcessor)
//    throws IOException {
//
//    searchRequest.scroll(Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS)));
//    SearchResponse<T> response = osClient.search(searchRequest.build(), clazz);
//
//    if (aggsProcessor != null) {
//      aggsProcessor.accept(response.aggregations());
//    }
//
//    final List<T> result = new ArrayList<>();
//    String scrollId = response.scrollId();
//    HitsMetadata hits = response.hits();
//
//    while (hits.hits().size() != 0) {
//      result.addAll(hits.hits().stream().map(m -> ((Hit) m).source()).toList());
//      // call response processor
//      if (searchHitsProcessor != null) {
//        searchHitsProcessor.accept(response.hits());
//      }
//
//      final ScrollRequest.Builder scrollRequest = new ScrollRequest.Builder();
//      scrollRequest.scrollId(scrollId);
//      scrollRequest.scroll(Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS)));
//
//      response = osClient.scroll(scrollRequest.build(), clazz);
//      scrollId = response.scrollId();
//      hits = response.hits();
//    }
//    clearScroll(scrollId, osClient);
//
//    return result;
//  }
//
//  public static List<String> scrollIdsToList(
//    SearchRequest.Builder request, OpenSearchClient osClient) throws IOException {
//    final List<String> result = new ArrayList<>();
//
//    final Consumer<List<Hit<Object>>> collectIds =
//      (hits) -> result.addAll(map(hits, SEARCH_HIT_ID_TO_STRING));
//
//    scrollWith(request, osClient, collectIds, null, null, Object.class);
//    return result;
//  }
//
//  public static void executeUpdate(OpenSearchClient osClient, UpdateRequest updateRequest)
//    throws PersistenceException {
//    try {
//
//      osClient.update(updateRequest, Object.class);
//
//    } catch (OpenSearchException | IOException e) {
//      final String errorMessage =
//        String.format(
//          "Update request failed for [%s] and id [%s] with the message [%s].",
//          updateRequest.index(), updateRequest.id(), e.getMessage());
//      throw new PersistenceException(errorMessage, e);
//    }
//  }
}
