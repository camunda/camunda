/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.util.CollectionUtil.throwAwayNullElements;
import static java.util.Arrays.asList;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES_VALUE;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.AbstractTemplateDescriptor;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.tasks.GetTaskRequest;
import org.elasticsearch.client.tasks.GetTaskResponse;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.tasks.RawTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public abstract class ElasticsearchUtil {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchUtil.class);

  public static final String ZEEBE_INDEX_DELIMITER = "_";
  public static final int SCROLL_KEEP_ALIVE_MS = 60000;
  public static final int INTERNAL_SCROLL_KEEP_ALIVE_MS = 30000;    //this scroll timeout value is used for reindex and delete queries
  public static final int TERMS_AGG_SIZE = 10000;
  public static final int QUERY_MAX_SIZE = 10000;
  public static final int TOPHITS_AGG_SIZE = 100;
  public static final int UPDATE_RETRY_COUNT = 3;

  public static final Function<SearchHit,Long> searchHitIdToLong = (hit) -> Long.valueOf(hit.getId());
  public static final Function<SearchHit,String> searchHitIdToString = SearchHit::getId;

  public static CompletableFuture<SearchResponse> searchAsync(final SearchRequest searchRequest,
      final Executor executor, final RestHighLevelClient esClient) {
    final var searchFuture = new CompletableFuture<SearchResponse>();
    esClient.searchAsync(searchRequest, RequestOptions.DEFAULT, new DelegatingActionListener<>(searchFuture, executor));
    return searchFuture;
  }

  public static CompletableFuture<Long> reindexAsyncWithConnectionRelease(final ThreadPoolTaskScheduler executor,
      final ReindexRequest reindexRequest, final String sourceIndexName, final RestHighLevelClient esClient) {
    CompletableFuture<String> reindexFuture = new CompletableFuture<>();
    try {
      final String taskId = esClient.submitReindexTask(reindexRequest, RequestOptions.DEFAULT).getTask();
      logger.debug("Reindexing started for index {}. Task id: {}", sourceIndexName, taskId);
      reindexFuture.complete(taskId);
    } catch (IOException ex) {
      reindexFuture.completeExceptionally(ex);
    }
    return reindexFuture.thenCompose((tId) -> checkTaskResult(executor, tId, sourceIndexName, "reindex", esClient));
  }

  public static CompletableFuture<Long> deleteAsyncWithConnectionRelease(final ThreadPoolTaskScheduler executor,
      final String sourceIndexName, final String idFieldName, final List<Object> idValues,
      final ObjectMapper objectMapper, final RestHighLevelClient esClient) {
    CompletableFuture<String> deleteRequestFuture = new CompletableFuture<>();
    try {
      final String query = termsQuery(idFieldName, idValues).toString();
      final Request deleteWithTaskRequest = new Request(HttpPost.METHOD_NAME,
          String.format("/%s/_delete_by_query", sourceIndexName));
      deleteWithTaskRequest.setJsonEntity(String.format("{\"query\": %s }", query));
      deleteWithTaskRequest.addParameter("wait_for_completion", "false");
      deleteWithTaskRequest.addParameter("slices", AUTO_SLICES_VALUE);
      deleteWithTaskRequest.addParameter("conflicts", "proceed");

      final Response response = esClient.getLowLevelClient().performRequest(deleteWithTaskRequest);

      if (!(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK)) {
        final HttpEntity entity = response.getEntity();
        String errorMsg = String.format("Exception occurred when performing deletion. Status code: %s, error: %s",
            response.getStatusLine().getStatusCode(), entity == null ? "" : EntityUtils.toString(entity));
        deleteRequestFuture.completeExceptionally(new ArchiverException(errorMsg));
      }

      Map<String, Object> bodyMap = objectMapper.readValue(response.getEntity().getContent(), Map.class);
      String taskId = (String) bodyMap.get("task");
      logger.debug("Deletion started for index {}. Task id {}", sourceIndexName, taskId);
      deleteRequestFuture.complete(taskId);
    } catch (IOException ex) {
      deleteRequestFuture.completeExceptionally(ex);
    }
    return deleteRequestFuture.thenCompose(
        (tId) -> checkTaskResult(executor, tId, sourceIndexName, "delete", esClient));
  }

  public static CompletableFuture<Long> checkTaskResult(ThreadPoolTaskScheduler executor, String taskId,
      String sourceIndexName, String operation, RestHighLevelClient esClient) {

    CompletableFuture<Long> checkTaskResult = new CompletableFuture<>();

    final BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(1_000, 1.2f, 5_000);
    final Runnable checkTaskResultRunnable = new Runnable() {
      @Override
      public void run() {
        try {
          //extract nodeId and taskId
          final String[] taskIdParts = taskId.split(":");
          final GetTaskRequest getTaskRequest = new GetTaskRequest(taskIdParts[0], Long.parseLong(taskIdParts[1]));
          final Optional<GetTaskResponse> getTaskResponseOptional = esClient.tasks().get(getTaskRequest, RequestOptions.DEFAULT);

          final GetTaskResponse getTaskResponse = getTaskResponseOptional.orElseThrow(
              ()-> new OperateRuntimeException("Task was not found: " + taskId));

          if (getTaskResponse.isCompleted()) {
            RawTaskStatus status = (RawTaskStatus) getTaskResponse.getTaskInfo().getStatus();
            long total = getTotalAffectedFromTask(sourceIndexName, operation, status);
            checkTaskResult.complete(total);
          } else {
            idleStrategy.idle();
            executor.schedule(this, Date.from(Instant.now().plusMillis(idleStrategy.idleTime())));
          }
        } catch (Exception e) {
          checkTaskResult.completeExceptionally(e);
        }
      }
    };
    executor.submit(checkTaskResultRunnable);
    return checkTaskResult;
  }

  private static long getTotalAffectedFromTask(String sourceIndexName, String operation, RawTaskStatus status) {
    //parse and check task status
    final Map<String, Object> statusMap = status.toMap();
    final long total = (Integer) statusMap.get("total");
    final long created = (Integer) statusMap.get("created");
    final long updated = (Integer) statusMap.get("updated");
    final long deleted = (Integer) statusMap.get("deleted");
    if (created + updated + deleted < total) {
      //there were some failures
      final String errorMsg = String.format(
          "Failures occurred when performing operation %s on source index %s. Check Elasticsearch logs.", operation,
          sourceIndexName);
      throw new OperateRuntimeException(errorMsg);
    }
    logger.debug("Operation {} succeeded on source index {}.", operation, sourceIndexName);
    return total;
  }

  public enum QueryType {
    ONLY_RUNTIME,
    ALL
  }

  /* CREATE QUERIES */

  public static SearchRequest createSearchRequest(TemplateDescriptor template) {
    return createSearchRequest(template, QueryType.ALL);
  }

  public static SearchRequest createSearchRequest(TemplateDescriptor template, QueryType queryType) {
    SearchRequest searchRequest = new SearchRequest(whereToSearch(template, queryType));
    return searchRequest;
  }

  private static String whereToSearch(TemplateDescriptor template, QueryType queryType) {
    switch (queryType) {
    case ONLY_RUNTIME:
      return template.getFullQualifiedName();
    case ALL:
    default:
      return template.getAlias();
    }
  }


  public static QueryBuilder joinWithOr(BoolQueryBuilder boolQueryBuilder, QueryBuilder... queries) {
    List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
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
    List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
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

  public static QueryBuilder joinWithOr(Collection<QueryBuilder> queries) {
    return joinWithOr(queries.toArray(new QueryBuilder[queries.size()]));
  }

  /**
   * Join queries with AND clause. If 0 queries are passed for wrapping, then null is returned. If 1 parameter is passed, it will be returned back as ia. Otherwise, the new
   * BoolQuery will be created and returned.
   * @param queries
   * @return
   */
  public static QueryBuilder joinWithAnd(QueryBuilder... queries) {
    List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
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
    List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
    for (QueryBuilder query : notNullQueries) {
      boolQuery.must(query);
    }
    return boolQuery;
  }

  public static BoolQueryBuilder createMatchNoneQuery() {
    return boolQuery().must(QueryBuilders.wrapperQuery("{\"match_none\": {}}"));
  }

  /* EXECUTE QUERY */

  public static void processBulkRequest(RestHighLevelClient esClient, BulkRequest bulkRequest) throws PersistenceException {
    processBulkRequest(esClient, bulkRequest, false);
  }

  public static void processBulkRequest(RestHighLevelClient esClient, BulkRequest bulkRequest,
      Runnable afterBulkAction) throws PersistenceException {
    processBulkRequest(esClient, bulkRequest, false);
    afterBulkAction.run();
  }

  public static void processBulkRequest(RestHighLevelClient esClient, BulkRequest bulkRequest, boolean refreshImmediately) throws PersistenceException {
    if (bulkRequest.requests().size() > 0) {
      try {
        logger.debug("************* FLUSH BULK START *************");
        if (refreshImmediately) {
          bulkRequest = bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        }
        final BulkResponse bulkItemResponses = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        final BulkItemResponse[] items = bulkItemResponses.getItems();
        for (int i = 0; i< items.length; i++) {
          BulkItemResponse responseItem = items[i];
          if (responseItem.isFailed() && !isEventConflictError(responseItem)) {

            if (isMissingIncident(responseItem)) {
              //the case when incident was already archived to dated index, but must be updated
              final DocWriteRequest<?> request = bulkRequest.requests().get(i);
              String incidentId = extractIncidentId(responseItem.getFailure().getMessage());
              final String indexName = getIndexNames(request.index() + "alias", asList(incidentId),
                  esClient).get(incidentId);
              request.index(indexName);
              if (indexName == null) {
                logger.warn("Index is not known for incident: " + incidentId);
              } else {
                esClient.update((UpdateRequest)request, RequestOptions.DEFAULT);
              }
            } else {
              logger.error(String
                  .format("%s failed for type [%s] and id [%s]: %s", responseItem.getOpType(),
                      responseItem.getIndex(), responseItem.getId(),
                      responseItem.getFailureMessage()), responseItem.getFailure().getCause());
              throw new PersistenceException(
                  "Operation failed: " + responseItem.getFailureMessage(),
                  responseItem.getFailure().getCause(), responseItem.getItemId());
            }
          }
        }
        logger.debug("************* FLUSH BULK FINISH *************");
      } catch (IOException ex) {
        throw new PersistenceException("Error when processing bulk request against Elasticsearch: " + ex.getMessage(), ex);
      }
    }
  }

  public static String extractIncidentId(final String errorMessage) {
    final Pattern fniPattern = Pattern
        .compile(".*\\[_doc\\]\\[(\\d*)\\].*");
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

  public static void executeUpdate(RestHighLevelClient esClient, UpdateRequest updateRequest) throws PersistenceException {
    try {
      esClient.update(updateRequest, RequestOptions.DEFAULT);
    } catch (ElasticsearchException | IOException e)  {
      final String errorMessage = String.format("Update request failed for [%s] and id [%s] with the message [%s].",
          updateRequest.index(), updateRequest.id(), e.getMessage());
      logger.error(errorMessage, e);
      throw new PersistenceException(errorMessage, e);
    }
  }

  /* MAP QUERY RESULTS */

  public static <T> List<T> mapSearchHits(List<SearchHit> searchHits, ObjectMapper objectMapper, JavaType valueType) {
    return mapSearchHits(searchHits.toArray(new SearchHit[searchHits.size()]), objectMapper, valueType);
  }

  public static <T> List<T> mapSearchHits(SearchHit[] searchHits, Function<SearchHit, T> searchHitMapper) {
    return map(searchHits, searchHitMapper);
  }

  public static <T> List<T> mapSearchHits(SearchHit[] searchHits, ObjectMapper objectMapper, Class<T> clazz) {
    return map(searchHits, (searchHit) -> fromSearchHit(searchHit.getSourceAsString(), objectMapper, clazz));
  }

  public static <T> T fromSearchHit(String searchHitString, ObjectMapper objectMapper, Class<T> clazz) {
    T entity;
    try {
    	entity = objectMapper.readValue(searchHitString, clazz);
    } catch (IOException e) {
      logger.error(String.format("Error while reading entity of type %s from Elasticsearch!", clazz.getName()), e);
      throw new OperateRuntimeException(String.format("Error while reading entity of type %s from Elasticsearch!", clazz.getName()), e);
    }
    return entity;
  }

  public static <T> List<T> mapSearchHits(SearchHit[] searchHits, ObjectMapper objectMapper, JavaType valueType) {
    return map(searchHits, (searchHit) -> fromSearchHit(searchHit.getSourceAsString(), objectMapper, valueType));
  }

  public static <T> T fromSearchHit(String searchHitString, ObjectMapper objectMapper, JavaType valueType) {
    T entity;
    try {
    	entity = objectMapper.readValue(searchHitString, valueType);
    } catch (IOException e) {
      logger.error(String.format("Error while reading entity of type %s from Elasticsearch!", valueType.toString()), e);
      throw new OperateRuntimeException(String.format("Error while reading entity of type %s from Elasticsearch!", valueType.toString()), e);
    }
    return entity;
  }

  public static <T> List<T> scroll(SearchRequest searchRequest, Class<T> clazz, ObjectMapper objectMapper, RestHighLevelClient esClient)
    throws IOException {
    return scroll(searchRequest, clazz, objectMapper, esClient, null, null);
  }

  public static <T> List<T> scroll(SearchRequest searchRequest, Class<T> clazz, ObjectMapper objectMapper, RestHighLevelClient esClient,
      Consumer<SearchHits> searchHitsProcessor, Consumer<Aggregations> aggsProcessor) throws IOException {
    return scroll(searchRequest, clazz, objectMapper, esClient, null, searchHitsProcessor, aggsProcessor);
  }

  public static <T> List<T> scroll(SearchRequest searchRequest, Class<T> clazz, ObjectMapper objectMapper, RestHighLevelClient esClient,
      Function<SearchHit, T> searchHitMapper, Consumer<SearchHits> searchHitsProcessor, Consumer<Aggregations> aggsProcessor) throws IOException {


      searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
      SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

      //call aggregations processor
      if (aggsProcessor != null) {
        aggsProcessor.accept(response.getAggregations());
      }

      List<T> result = new ArrayList<>();
      String scrollId = response.getScrollId();
      SearchHits hits = response.getHits();

      while (hits.getHits().length != 0) {
        if (searchHitMapper != null) {
          result.addAll(mapSearchHits(hits.getHits(), searchHitMapper));
        } else {
          result.addAll(mapSearchHits(hits.getHits(), objectMapper, clazz));
        }

        //call response processor
        if (searchHitsProcessor != null) {
          searchHitsProcessor.accept(response.getHits());
        }

        SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
        scrollRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));

        response = esClient
            .scroll(scrollRequest, RequestOptions.DEFAULT);

        scrollId = response.getScrollId();
        hits = response.getHits();
      }

      clearScroll(scrollId, esClient);

      return result;
  }

  public static void scrollWith(SearchRequest searchRequest, RestHighLevelClient esClient,
      Consumer<SearchHits> searchHitsProcessor) throws IOException {
    scrollWith(searchRequest, esClient, searchHitsProcessor,
        null, null);
  }

  public static void scrollWith(SearchRequest searchRequest, RestHighLevelClient esClient,
    Consumer<SearchHits> searchHitsProcessor, Consumer<Aggregations> aggsProcessor,
      Consumer<SearchHits> firstResponseConsumer) throws IOException {

    searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
    SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    if (firstResponseConsumer != null) {
      firstResponseConsumer.accept(response.getHits());
    }

    //call aggregations processor
    if (aggsProcessor != null) {
      aggsProcessor.accept(response.getAggregations());
    }

    String scrollId = response.getScrollId();
    SearchHits hits = response.getHits();
    while (hits.getHits().length != 0) {
      //call response processor
      if (searchHitsProcessor != null) {
        searchHitsProcessor.accept(response.getHits());
      }

      SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));

      response = esClient
        .scroll(scrollRequest, RequestOptions.DEFAULT);

      scrollId = response.getScrollId();
      hits = response.getHits();
    }

    clearScroll(scrollId, esClient);
  }

  private static void clearScroll(String scrollId, RestHighLevelClient esClient) {
    if (scrollId != null) {
      //clear the scroll
      ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
      clearScrollRequest.addScrollId(scrollId);
      try {
        esClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
      } catch (Exception e) {
        logger.warn("Error occurred when clearing the scroll with id [{}]", scrollId);
      }
    }
  }

  public static List<String> scrollIdsToList(SearchRequest request, RestHighLevelClient esClient) throws IOException {
    List<String> result = new ArrayList<>();

    Consumer<SearchHits> collectIds = (hits) -> {
      result.addAll(map(hits.getHits(),searchHitIdToString));
    };

    scrollWith(request, esClient, collectIds, null, collectIds);
    return result;
  }

  public static List<Long> scrollKeysToList(SearchRequest request, RestHighLevelClient esClient) throws IOException {
    List<Long> result = new ArrayList<>();

    Consumer<SearchHits> collectIds = (hits) -> {
      result.addAll(map(hits.getHits(),searchHitIdToLong));
    };

    scrollWith(request, esClient, collectIds, null, collectIds);
    return result;
  }

  public static <T> List<T> scrollFieldToList(SearchRequest request, String fieldName, RestHighLevelClient esClient) throws IOException {
    List<T> result = new ArrayList<>();
    Function<SearchHit, T> searchHitFieldToString = (searchHit) -> (T)searchHit.getSourceAsMap().get(fieldName);

    Consumer<SearchHits> collectFields = (hits) -> {
        result.addAll(map(hits.getHits(), searchHitFieldToString));
    };

    scrollWith(request, esClient, collectFields,null, collectFields);
    return result;
  }

  public static Set<String> scrollIdsToSet(SearchRequest request, RestHighLevelClient esClient) throws IOException {
    Set<String> result = new HashSet<>();

    Consumer<SearchHits> collectIds= (hits) -> {
        result.addAll(map(hits.getHits(),searchHitIdToString));
    };
    scrollWith(request, esClient, collectIds, null, collectIds);
    return result;
  }

  public static Set<Long> scrollKeysToSet(SearchRequest request, RestHighLevelClient esClient) throws IOException {
    Set<Long> result = new HashSet<>();
    Consumer<SearchHits> collectIds= (hits) -> {
      result.addAll(map(hits.getHits(), searchHitIdToLong));
    };
    scrollWith(request, esClient, collectIds, null, collectIds);
    return result;
  }

  public static Map<String, String> getIndexNames(String aliasName,
      Collection<String> ids, RestHighLevelClient esClient) {

    Map<String, String> indexNames = new HashMap<>();

    final SearchRequest piRequest = new SearchRequest(aliasName)
        .source(new SearchSourceBuilder()
            .query(idsQuery().addIds(ids.toArray(String[]::new)))
            .fetchSource(false)
        );
    try {
      scrollWith(piRequest, esClient, sh -> {
        indexNames.putAll(
            Arrays.stream(sh.getHits()).collect(Collectors.toMap(hit -> {
              return hit.getId();
            }, hit -> {
              return hit.getIndex();
            })));
      });
    } catch (IOException e) {
      throw new OperateRuntimeException(e.getMessage(), e);
    }
    return indexNames;
  }

  public static Map<String, String> getIndexNames(AbstractTemplateDescriptor template,
      Collection<String> ids, RestHighLevelClient esClient) {

    Map<String, String> indexNames = new HashMap<>();

    final SearchRequest piRequest = ElasticsearchUtil.createSearchRequest(template)
        .source(new SearchSourceBuilder()
            .query(idsQuery().addIds(ids.toArray(String[]::new)))
            .fetchSource(false)
        );
    try {
      scrollWith(piRequest, esClient, sh -> {
        indexNames.putAll(
            Arrays.stream(sh.getHits()).collect(Collectors.toMap(hit -> {
              return hit.getId();
            }, hit -> {
              return hit.getIndex();
            })));
      });
    } catch (IOException e) {
      throw new OperateRuntimeException(e.getMessage(), e);
    }
    return indexNames;
  }

  public static Map<String, List<String>> getIndexNamesAsList(AbstractTemplateDescriptor template,
      Collection<String> ids, RestHighLevelClient esClient) {

    Map<String, List<String>> indexNames = new ConcurrentHashMap<>();

    final SearchRequest piRequest = ElasticsearchUtil.createSearchRequest(template)
        .source(new SearchSourceBuilder()
            .query(idsQuery().addIds(ids.toArray(String[]::new)))
            .fetchSource(false)
        );
    try {
      scrollWith(piRequest, esClient, sh -> {
        Arrays.stream(sh.getHits())
            .collect(Collectors.groupingBy(SearchHit::getId,
                Collectors.mapping(SearchHit::getIndex, Collectors.toList())))
            .forEach((key, value) -> indexNames.merge(key, value, (v1, v2) -> {
              v1.addAll(v2);
              return v1;
            }));
      });
    } catch (IOException e) {
      throw new OperateRuntimeException(e.getMessage(), e);
    }
    return indexNames;
  }

  public static void refreshIndicesFor(final RestHighLevelClient esClient, final String indexPattern) {
    RefreshRequest refreshRequest = new RefreshRequest(indexPattern);
    try {
      RefreshResponse refresh = esClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
      if (refresh.getFailedShards() > 0) {
        logger.warn("Unable to refresh indices: {}", indexPattern);
      }
    } catch (Exception ex) {
      logger.warn(String.format("Unable to refresh indices: %s", indexPattern), ex);
    }
  }

  public static boolean indexExists(final RestHighLevelClient esCLient, final String indexName) throws IOException {
    return esCLient.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
  }

  public static boolean fieldInIndexExists(final RestHighLevelClient esCLient, final String indexName, final String fieldName)
      throws IOException {
    final SearchResponse searchResponse = esCLient.search(new SearchRequest(indexName)
            .source(new SearchSourceBuilder()
                .query(
                    QueryBuilders.existsQuery(fieldName)))
        , RequestOptions.DEFAULT);
    return searchResponse.getHits().getTotalHits().value > 0;
  }

  private static class DelegatingActionListener<Response> implements ActionListener<Response> {

    private final CompletableFuture<Response> future;
    private final Executor executorDelegate;

    private DelegatingActionListener(final CompletableFuture<Response> future, final Executor executor) {
      this.future = future;
      this.executorDelegate = executor;
    }

    @Override
    public void onResponse(Response response) {
      executorDelegate.execute(() -> future.complete(response));
    }

    @Override
    public void onFailure(Exception e) {
      executorDelegate.execute(() -> future.completeExceptionally(e));
    }
  }
}
