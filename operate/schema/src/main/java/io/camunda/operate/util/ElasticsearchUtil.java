/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.util.CollectionUtil.throwAwayNullElements;
import static java.util.Arrays.asList;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES_VALUE;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.HitEntity;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.AbstractTemplateDescriptor;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.util.elasticsearch.ElasticsearcRequestValidator;
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

  public static CompletableFuture<Long> reindexAsyncWithConnectionRelease(
      final ThreadPoolTaskScheduler executor,
      final ReindexRequest reindexRequest,
      final String sourceIndexName,
      final RestHighLevelClient esClient) {
    final CompletableFuture<String> reindexFuture = new CompletableFuture<>();
    try {
      final String taskId =
          esClient.submitReindexTask(reindexRequest, RequestOptions.DEFAULT).getTask();
      LOGGER.debug("Reindexing started for index {}. Task id: {}", sourceIndexName, taskId);
      reindexFuture.complete(taskId);
    } catch (IOException ex) {
      reindexFuture.completeExceptionally(ex);
    }
    return reindexFuture.thenCompose(
        (tId) -> checkTaskResult(executor, tId, sourceIndexName, "reindex", esClient));
  }

  public static CompletableFuture<Long> deleteAsyncWithConnectionRelease(
      final ThreadPoolTaskScheduler executor,
      final String sourceIndexName,
      final String idFieldName,
      final List<Object> idValues,
      final ObjectMapper objectMapper,
      final RestHighLevelClient esClient) {
    final CompletableFuture<String> deleteRequestFuture = new CompletableFuture<>();
    try {
      final String query = termsQuery(idFieldName, idValues).toString();
      final Request deleteWithTaskRequest =
          new Request(HttpPost.METHOD_NAME, String.format("/%s/_delete_by_query", sourceIndexName));
      deleteWithTaskRequest.setJsonEntity(String.format("{\"query\": %s }", query));
      deleteWithTaskRequest.addParameter("wait_for_completion", "false");
      deleteWithTaskRequest.addParameter("slices", AUTO_SLICES_VALUE);
      deleteWithTaskRequest.addParameter("conflicts", "proceed");

      final Response response = esClient.getLowLevelClient().performRequest(deleteWithTaskRequest);

      if (!(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK)) {
        final HttpEntity entity = response.getEntity();
        final String errorMsg =
            String.format(
                "Exception occurred when performing deletion. Status code: %s, error: %s",
                response.getStatusLine().getStatusCode(),
                entity == null ? "" : EntityUtils.toString(entity));
        deleteRequestFuture.completeExceptionally(new ArchiverException(errorMsg));
      }

      final Map<String, Object> bodyMap =
          objectMapper.readValue(response.getEntity().getContent(), Map.class);
      final String taskId = (String) bodyMap.get("task");
      LOGGER.debug("Deletion started for index {}. Task id {}", sourceIndexName, taskId);
      deleteRequestFuture.complete(taskId);
    } catch (IOException ex) {
      deleteRequestFuture.completeExceptionally(ex);
    }
    return deleteRequestFuture.thenCompose(
        (tId) -> checkTaskResult(executor, tId, sourceIndexName, "delete", esClient));
  }

  private static CompletableFuture<Long> checkTaskResult(
      ThreadPoolTaskScheduler executor,
      String taskId,
      String sourceIndexName,
      String operation,
      RestHighLevelClient esClient) {

    final CompletableFuture<Long> checkTaskResult = new CompletableFuture<>();

    final BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(1_000, 1.2f, 5_000);
    final Runnable checkTaskResultRunnable =
        new Runnable() {
          @Override
          public void run() {
            try {
              // extract nodeId and taskId
              final String[] taskIdParts = taskId.split(":");
              final GetTaskRequest getTaskRequest =
                  new GetTaskRequest(taskIdParts[0], Long.parseLong(taskIdParts[1]));
              final Optional<GetTaskResponse> getTaskResponseOptional =
                  esClient.tasks().get(getTaskRequest, RequestOptions.DEFAULT);

              final GetTaskResponse getTaskResponse =
                  getTaskResponseOptional.orElseThrow(
                      () -> new OperateRuntimeException("Task was not found: " + taskId));

              if (getTaskResponse.isCompleted()) {
                final RawTaskStatus status =
                    (RawTaskStatus) getTaskResponse.getTaskInfo().getStatus();
                final long total = getTotalAffectedFromTask(sourceIndexName, operation, status);
                checkTaskResult.complete(total);
              } else {
                idleStrategy.idle();
                executor.schedule(
                    this, Date.from(Instant.now().plusMillis(idleStrategy.idleTime())));
              }
            } catch (Exception e) {
              checkTaskResult.completeExceptionally(e);
            }
          }
        };
    executor.submit(checkTaskResultRunnable);
    return checkTaskResult;
  }

  private static long getTotalAffectedFromTask(
      String sourceIndexName, String operation, RawTaskStatus status) {
    // parse and check task status
    final Map<String, Object> statusMap = status.toMap();
    final long total = (Integer) statusMap.get("total");
    final long created = (Integer) statusMap.get("created");
    final long updated = (Integer) statusMap.get("updated");
    final long deleted = (Integer) statusMap.get("deleted");
    if (created + updated + deleted < total) {
      // there were some failures
      final String errorMsg =
          String.format(
              "Failures occurred when performing operation %s on source index %s. Check Elasticsearch logs.",
              operation, sourceIndexName);
      throw new OperateRuntimeException(errorMsg);
    }
    LOGGER.debug("Operation {} succeeded on source index {}.", operation, sourceIndexName);
    return total;
  }

  public static SearchRequest createSearchRequest(TemplateDescriptor template) {
    return createSearchRequest(template, QueryType.ALL);
  }

  /* CREATE QUERIES */

  public static SearchRequest createSearchRequest(
      TemplateDescriptor template, QueryType queryType) {
    final SearchRequest searchRequest = new SearchRequest(whereToSearch(template, queryType));
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

  public static QueryBuilder joinWithOr(
      BoolQueryBuilder boolQueryBuilder, QueryBuilder... queries) {
    final List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
    for (QueryBuilder query : notNullQueries) {
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
  public static QueryBuilder joinWithOr(QueryBuilder... queries) {
    final List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
    switch (notNullQueries.size()) {
      case 0:
        return null;
      case 1:
        return notNullQueries.get(0);
      default:
        final BoolQueryBuilder boolQ = boolQuery();
        for (QueryBuilder query : notNullQueries) {
          boolQ.should(query);
        }
        return boolQ;
    }
  }

  public static QueryBuilder joinWithOr(Collection<QueryBuilder> queries) {
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
  public static QueryBuilder joinWithAnd(QueryBuilder... queries) {
    final List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
    switch (notNullQueries.size()) {
      case 0:
        return null;
      case 1:
        return notNullQueries.get(0);
      default:
        final BoolQueryBuilder boolQ = boolQuery();
        for (QueryBuilder query : notNullQueries) {
          boolQ.must(query);
        }
        return boolQ;
    }
  }

  public static BoolQueryBuilder createMatchNoneQuery() {
    return boolQuery().must(QueryBuilders.wrapperQuery("{\"match_none\": {}}"));
  }

  public static void processBulkRequest(
      RestHighLevelClient esClient,
      BulkRequest bulkRequest,
      long maxBulkRequestSizeInBytes,
      boolean ignoreNullIndex)
      throws PersistenceException {
    processBulkRequest(esClient, bulkRequest, false, maxBulkRequestSizeInBytes, ignoreNullIndex);
  }

  /* EXECUTE QUERY */

  public static void processBulkRequest(
      RestHighLevelClient esClient,
      BulkRequest bulkRequest,
      boolean refreshImmediately,
      long maxBulkRequestSizeInBytes,
      boolean ignoreNullIndex)
      throws PersistenceException {
    bulkRequest = ElasticsearcRequestValidator.validateIndices(bulkRequest, ignoreNullIndex);
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
      RestHighLevelClient esClient, BulkRequest bulkRequest, boolean refreshImmediately)
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
      } catch (IOException ex) {
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
      List<HitEntity> hits, ObjectMapper objectMapper, JavaType valueType) {
    return map(hits, h -> fromSearchHit(h.getSourceAsString(), objectMapper, valueType));
  }

  public static <T> List<T> mapSearchHits(
      HitEntity[] searchHits, ObjectMapper objectMapper, Class<T> clazz) {
    return map(
        searchHits,
        (searchHit) -> fromSearchHit(searchHit.getSourceAsString(), objectMapper, clazz));
  }

  public static <T> List<T> mapSearchHits(
      SearchHit[] searchHits, Function<SearchHit, T> searchHitMapper) {
    return map(searchHits, searchHitMapper);
  }

  public static <T> List<T> mapSearchHits(
      SearchHit[] searchHits, ObjectMapper objectMapper, Class<T> clazz) {
    return map(
        searchHits,
        (searchHit) -> fromSearchHit(searchHit.getSourceAsString(), objectMapper, clazz));
  }

  public static <T> T fromSearchHit(
      String searchHitString, ObjectMapper objectMapper, Class<T> clazz) {
    final T entity;
    try {
      entity = objectMapper.readValue(searchHitString, clazz);
    } catch (IOException e) {
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
      SearchHit[] searchHits, ObjectMapper objectMapper, JavaType valueType) {
    return map(
        searchHits,
        (searchHit) -> fromSearchHit(searchHit.getSourceAsString(), objectMapper, valueType));
  }

  public static <T> T fromSearchHit(
      String searchHitString, ObjectMapper objectMapper, JavaType valueType) {
    final T entity;
    try {
      entity = objectMapper.readValue(searchHitString, valueType);
    } catch (IOException e) {
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
      SearchRequest searchRequest,
      Class<T> clazz,
      ObjectMapper objectMapper,
      RestHighLevelClient esClient)
      throws IOException {
    return scroll(searchRequest, clazz, objectMapper, esClient, null, null);
  }

  public static <T> List<T> scroll(
      SearchRequest searchRequest,
      Class<T> clazz,
      ObjectMapper objectMapper,
      RestHighLevelClient esClient,
      Consumer<SearchHits> searchHitsProcessor,
      Consumer<Aggregations> aggsProcessor)
      throws IOException {
    return scroll(
        searchRequest, clazz, objectMapper, esClient, null, searchHitsProcessor, aggsProcessor);
  }

  public static <T> List<T> scroll(
      SearchRequest searchRequest,
      Class<T> clazz,
      ObjectMapper objectMapper,
      RestHighLevelClient esClient,
      Function<SearchHit, T> searchHitMapper,
      Consumer<SearchHits> searchHitsProcessor,
      Consumer<Aggregations> aggsProcessor)
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
      SearchRequest searchRequest,
      Consumer<SearchHits> searchHitsProcessor,
      RestHighLevelClient esClient)
      throws IOException {
    scroll(searchRequest, searchHitsProcessor, esClient, SCROLL_KEEP_ALIVE_MS);
  }

  public static void scroll(
      SearchRequest searchRequest,
      Consumer<SearchHits> searchHitsProcessor,
      RestHighLevelClient esClient,
      long scrollKeepAlive)
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
      SearchRequest searchRequest,
      RestHighLevelClient esClient,
      Consumer<SearchHits> searchHitsProcessor)
      throws IOException {
    scrollWith(searchRequest, esClient, searchHitsProcessor, null, null);
  }

  public static void scrollWith(
      SearchRequest searchRequest,
      RestHighLevelClient esClient,
      Consumer<SearchHits> searchHitsProcessor,
      Consumer<Aggregations> aggsProcessor,
      Consumer<SearchHits> firstResponseConsumer)
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

  public static void clearScroll(String scrollId, RestHighLevelClient esClient) {
    if (scrollId != null) {
      // clear the scroll
      final ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
      clearScrollRequest.addScrollId(scrollId);
      try {
        esClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
      } catch (Exception e) {
        LOGGER.warn("Error occurred when clearing the scroll with id [{}]", scrollId);
      }
    }
  }

  public static List<Long> scrollKeysToList(SearchRequest request, RestHighLevelClient esClient)
      throws IOException {
    final List<Long> result = new ArrayList<>();

    final Consumer<SearchHits> collectIds =
        (hits) -> {
          result.addAll(map(hits.getHits(), SEARCH_HIT_ID_TO_LONG));
        };

    scrollWith(request, esClient, collectIds, null, collectIds);
    return result;
  }

  public static <T> List<T> scrollFieldToList(
      SearchRequest request, String fieldName, RestHighLevelClient esClient) throws IOException {
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

  public static Set<String> scrollIdsToSet(SearchRequest request, RestHighLevelClient esClient)
      throws IOException {
    final Set<String> result = new HashSet<>();

    final Consumer<SearchHits> collectIds =
        (hits) -> {
          result.addAll(map(hits.getHits(), SEARCH_HIT_ID_TO_STRING));
        };
    scrollWith(request, esClient, collectIds, null, collectIds);
    return result;
  }

  public static Map<String, String> getIndexNames(
      String aliasName, Collection<String> ids, RestHighLevelClient esClient) {

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
    } catch (IOException e) {
      throw new OperateRuntimeException(e.getMessage(), e);
    }
    return indexNames;
  }

  public static Map<String, String> getIndexNames(
      AbstractTemplateDescriptor template, Collection<String> ids, RestHighLevelClient esClient) {

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
    } catch (IOException e) {
      throw new OperateRuntimeException(e.getMessage(), e);
    }
    return indexNames;
  }

  public static Map<String, List<String>> getIndexNamesAsList(
      AbstractTemplateDescriptor template, Collection<String> ids, RestHighLevelClient esClient) {

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
    } catch (IOException e) {
      throw new OperateRuntimeException(e.getMessage(), e);
    }
    return indexNames;
  }

  public static RequestOptions requestOptionsFor(int maxSizeInBytes) {
    final RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
    options.setHttpAsyncResponseConsumerFactory(
        new HttpAsyncResponseConsumerFactory.HeapBufferedResponseConsumerFactory(maxSizeInBytes));
    return options.build();
  }

  private static final class DelegatingActionListener<Response>
      implements ActionListener<Response> {

    private final CompletableFuture<Response> future;
    private final Executor executorDelegate;

    private DelegatingActionListener(
        final CompletableFuture<Response> future, final Executor executor) {
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

  public enum QueryType {
    ONLY_RUNTIME,
    ALL
  }
}
