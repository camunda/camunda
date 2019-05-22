/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.PersistenceException;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

public abstract class ElasticsearchUtil {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchUtil.class);

  public static final String ES_INDEX_TYPE = "_doc";
  public static final int SCROLL_KEEP_ALIVE_MS = 60000;
  public static final int TERMS_AGG_SIZE = 10000;
  public static final int TOPHITS_AGG_SIZE = 100;

  public static QueryBuilder joinWithOr(BoolQueryBuilder boolQueryBuilder, QueryBuilder... queries) {
    List<QueryBuilder> notNullQueries = CollectionUtil.throwAwayNullElements(queries);
    for (QueryBuilder query: notNullQueries) {
      boolQueryBuilder.should(query);
    }
    return boolQueryBuilder;
  }

  /**
   * Join queries with OR clause. If 0 queries are passed for wrapping, then null is returned. If 1 parameter is passed, it will be returned back as ia. Otherwise, the new
   * BoolQuery will be created and returned.
   * @param queries
   * @return
   */
  public static QueryBuilder joinWithOr(QueryBuilder... queries) {
    List<QueryBuilder> notNullQueries = CollectionUtil.throwAwayNullElements(queries);
    switch (notNullQueries.size()) {
    case 0:
      return null;
    case 1:
      return notNullQueries.get(0);
    default:
      final BoolQueryBuilder boolQ = boolQuery();
      for (QueryBuilder query: notNullQueries) {
        boolQ.should(query);
      }
      return boolQ;
    }
  }

  /**
   * Join queries with AND clause. If 0 queries are passed for wrapping, then null is returned. If 1 parameter is passed, it will be returned back as ia. Otherwise, the new
   * BoolQuery will be created and returned.
   * @param queries
   * @return
   */
  public static QueryBuilder joinWithAnd(QueryBuilder... queries) {
    List<QueryBuilder> notNullQueries = CollectionUtil.throwAwayNullElements(queries);
    switch (notNullQueries.size()) {
    case 0:
      return null;
    case 1:
      return notNullQueries.get(0);
    default:
      final BoolQueryBuilder boolQ = boolQuery();
      for (QueryBuilder query: notNullQueries) {
        boolQ.must(query);
      }
      return boolQ;
    }
  }

  public static QueryBuilder addToBoolMust(BoolQueryBuilder boolQuery, QueryBuilder... queries) {
    if (boolQuery.mustNot().size() != 0 || boolQuery.filter().size() != 0 || boolQuery.should().size() != 0) {
      throw new IllegalArgumentException("BoolQuery with only must elements is expected here.");
    }
    List<QueryBuilder> notNullQueries = CollectionUtil.throwAwayNullElements(queries);
    for (QueryBuilder query : notNullQueries) {
      boolQuery.must(query);
    }
    return boolQuery;
  }

  public static BoolQueryBuilder createMatchNoneQuery() {
    return boolQuery().must(QueryBuilders.wrapperQuery("{\"match_none\": {}}"));
  }

  public static <T> List<T> mapSearchHits(SearchHit[] searchHits, ObjectMapper objectMapper, Class<T> clazz) {
    List<T> result = new ArrayList<>();
    for (SearchHit searchHit : searchHits) {
      String searchHitAsString = searchHit.getSourceAsString();
      result.add(fromSearchHit(searchHitAsString, objectMapper, clazz));
    }
    return result;
  }

  public static <T> T fromSearchHit(String searchHitString, ObjectMapper objectMapper, Class<T> clazz) {
    T workflowInstance;
    try {
      workflowInstance = objectMapper.readValue(searchHitString, clazz);
    } catch (IOException e) {
      logger.error(String.format("Error while reading entity of type %s from Elasticsearch!", clazz.getName()), e);
      throw new OperateRuntimeException(String.format("Error while reading entity of type %s from Elasticsearch!", clazz.getName()), e);
    }
    return workflowInstance;
  }

  public static <T> List<T> mapSearchHits(SearchHit[] searchHits, ObjectMapper objectMapper, JavaType valueType) {
    List<T> result = new ArrayList<>();
    for (SearchHit searchHit : searchHits) {
      String searchHitAsString = searchHit.getSourceAsString();
      result.add(fromSearchHit(searchHitAsString, objectMapper, valueType));
    }
    return result;
  }

  public static <T> T fromSearchHit(String searchHitString, ObjectMapper objectMapper, JavaType valueType) {
    T workflowInstance;
    try {
      workflowInstance = objectMapper.readValue(searchHitString, valueType);
    } catch (IOException e) {
      logger.error(String.format("Error while reading entity of type %s from Elasticsearch!", valueType.toString()), e);
      throw new OperateRuntimeException(String.format("Error while reading entity of type %s from Elasticsearch!", valueType.toString()), e);
    }
    return workflowInstance;
  }

  public static void processBulkRequest(RestHighLevelClient esClient, BulkRequest bulkRequest) throws PersistenceException {
    processBulkRequest(esClient, bulkRequest, false);
  }

  public static void processBulkRequest(RestHighLevelClient esClient, BulkRequest bulkRequest, boolean refreshImmediately) throws PersistenceException {
    if (bulkRequest.requests().size() > 0) {
      try {
        logger.debug("************* FLUSH BULK *************");
        if (refreshImmediately) {
          bulkRequest = bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        }
        final BulkResponse bulkItemResponses = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        final BulkItemResponse[] items = bulkItemResponses.getItems();
        for (BulkItemResponse responseItem : items) {
          if (responseItem.isFailed()) {
            logger.error(String.format("%s failed for type [%s] and id [%s]: %s", responseItem.getOpType(), responseItem.getIndex(), responseItem.getId(),
              responseItem.getFailureMessage()), responseItem.getFailure().getCause());
            throw new PersistenceException("Operation failed: " + responseItem.getFailureMessage(), responseItem.getFailure().getCause(), responseItem.getItemId());
          }
        }
      } catch (IOException ex) {
        throw new PersistenceException("Error when processing bulk request against Elasticsearch: " + ex.getMessage(), ex);
      }
    }
  }

  public static void executeUpdate(RestHighLevelClient esClient, UpdateRequest updateRequest) throws PersistenceException {
      try {
        esClient.update(updateRequest, RequestOptions.DEFAULT);
      } catch (ElasticsearchException | IOException e)  {
        final String errorMessage = String.format("Update request failed for type [%s] and id [%s] with the message [%s].",
          updateRequest.type(), updateRequest.id(), e.getMessage());
        logger.error(errorMessage, e);
        throw new PersistenceException(errorMessage, e);
      }
  }

  public static void executeIndex(RestHighLevelClient esClient, List<IndexRequest> indexRequests) throws PersistenceException {
    if (indexRequests.size() == 1) {
      executeIndex(esClient, indexRequests.get(0));
    } else if (indexRequests.size() > 1) {
      BulkRequest bulkRequest = new BulkRequest();
      for (IndexRequest indexRequest: indexRequests) {
        bulkRequest.add(indexRequest);
      }
      processBulkRequest(esClient, bulkRequest);
    }
  }

  public static void executeIndex(RestHighLevelClient esClient, IndexRequest indexRequest) throws PersistenceException {
      try {
        esClient.index(indexRequest, RequestOptions.DEFAULT);
      } catch (ElasticsearchException | IOException e)  {
        final String errorMessage = String.format("Index request failed for type [%s] and id [%s] with the message [%s].",
          indexRequest.type(), indexRequest.id(), e.getMessage());
        logger.error(errorMessage, e);
        throw new PersistenceException(errorMessage, e);
      }
  }

  public static <T extends OperateEntity> List<T> scroll(SearchRequest searchRequest, Class<T> clazz, ObjectMapper objectMapper, RestHighLevelClient esClient)
    throws IOException {
    return scroll(searchRequest, clazz, objectMapper, esClient, null, null);
  }


  public static <T extends OperateEntity> List<T> scroll(SearchRequest searchRequest, Class<T> clazz, ObjectMapper objectMapper, RestHighLevelClient esClient,
    Consumer<SearchHits> searchHitsProcessor, Consumer<Aggregations> aggsProcessor) throws IOException {

    searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
    SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    //call aggregations processor
    if (aggsProcessor != null) {
      aggsProcessor.accept(response.getAggregations());
    }

    List<T> result = new ArrayList<>();
    String scrollId = null;
    while (response.getHits().getHits().length != 0) {
      SearchHits hits = response.getHits();
      scrollId = response.getScrollId();

      result.addAll(mapSearchHits(hits.getHits(), objectMapper, clazz));

      //call response processor
      if (searchHitsProcessor != null) {
        searchHitsProcessor.accept(response.getHits());
      }

      SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));

      response = esClient
        .scroll(scrollRequest, RequestOptions.DEFAULT);

    };

    clearScroll(scrollId, esClient);

    return result;
  }

  public static void scrollWithoutResults(SearchRequest searchRequest, RestHighLevelClient esClient,
    Consumer<SearchHits> searchHitsProcessor, Consumer<Aggregations> aggsProcessor,
      Consumer<SearchHits> firstResponseConsumer) throws IOException {

    searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
    SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    firstResponseConsumer.accept(response.getHits());

    //call aggregations processor
    if (aggsProcessor != null) {
      aggsProcessor.accept(response.getAggregations());
    }

    String scrollId = null;
    while (response.getHits().getHits().length != 0) {
      scrollId = response.getScrollId();

      //call response processor
      if (searchHitsProcessor != null) {
        searchHitsProcessor.accept(response.getHits());
      }

      SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));

      response = esClient
        .scroll(scrollRequest, RequestOptions.DEFAULT);

    };

    clearScroll(scrollId, esClient);
  }

  private static void clearScroll(String scrollId, RestHighLevelClient esClient) {
    if (scrollId != null) {
      //clear the scroll
      ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
      clearScrollRequest.addScrollId(scrollId);
      try {
        esClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
      } catch (IOException e) {
        logger.warn("Error occurred when clearing the scroll with id [{}]", scrollId);
      }
    }
  }

  public static List<String> scrollIdsToList(SearchRequest request, RestHighLevelClient esClient) throws IOException {
    List<String> result = new ArrayList<>();

    request.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
    SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);

    String scrollId = null;

    while (response.getHits().getHits().length != 0) {
      SearchHits hits = response.getHits();
      scrollId = response.getScrollId();

      result.addAll(Arrays.stream(hits.getHits()).collect(HashSet::new, (set, hit) -> set.add(hit.getId()), (set1, set2) -> set1.addAll(set2)));      result.addAll(Arrays.stream(hits.getHits()).collect(ArrayList::new, (list, hit) -> list.add(hit.getId()), (list1, list2) -> list1.addAll(list2)));

      SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));

      response = esClient
        .scroll(scrollRequest, RequestOptions.DEFAULT);
    }

    clearScroll(scrollId, esClient);

    return result;
  }

  public static List<String> scrollFieldToList(SearchRequest request, String fieldName, RestHighLevelClient esClient) throws IOException {
    List<String> result = new ArrayList<>();

    request.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
    SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);

    String scrollId = null;

    while (response.getHits().getHits().length != 0) {
      SearchHits hits = response.getHits();
      scrollId = response.getScrollId();

      result.addAll(Arrays.stream(hits.getHits()).collect(ArrayList::new, (list, hit) -> list.add(hit.getSourceAsMap().get(fieldName).toString()), (list1, list2) -> list1.addAll(list2)));

      SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));

      response = esClient
        .scroll(scrollRequest, RequestOptions.DEFAULT);
    }

    clearScroll(scrollId, esClient);

    return result;
  }

  public static Set<String> scrollIdsToSet(SearchRequest request, RestHighLevelClient esClient) throws IOException {
    Set<String> result = new HashSet<>();

    request.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
    SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);

    String scrollId = null;

    while (response.getHits().getHits().length != 0) {
      SearchHits hits = response.getHits();
      scrollId = response.getScrollId();

      result.addAll(Arrays.stream(hits.getHits()).collect(HashSet::new, (set, hit) -> set.add(hit.getId()), (set1, set2) -> set1.addAll(set2)));

      SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));

      response = esClient
          .scroll(scrollRequest, RequestOptions.DEFAULT);
    }

    clearScroll(scrollId, esClient);

    return result;
  }
}
