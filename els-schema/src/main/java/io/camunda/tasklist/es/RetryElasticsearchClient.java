/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.es;

import static io.camunda.tasklist.util.CollectionUtil.getOrDefaultForNullValue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.CollectionUtil;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedSupplier;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.ingest.DeletePipelineRequest;
import org.elasticsearch.action.ingest.PutPipelineRequest;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.indexlifecycle.PutLifecyclePolicyRequest;
import org.elasticsearch.client.indices.ComposableIndexTemplateExistRequest;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.DeleteComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.client.indices.PutComponentTemplateRequest;
import org.elasticsearch.client.indices.PutComposableIndexTemplateRequest;
import org.elasticsearch.client.tasks.GetTaskRequest;
import org.elasticsearch.client.tasks.GetTaskResponse;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RetryElasticsearchClient {

  public static final String REFRESH_INTERVAL = "index.refresh_interval";
  public static final String NO_REFRESH = "-1";
  public static final String NUMBERS_OF_REPLICA = "index.number_of_replicas";
  public static final String NO_REPLICA = "0";
  public static final int SCROLL_KEEP_ALIVE_MS = 60_000;
  public static final int DEFAULT_NUMBER_OF_RETRIES =
      30 * 10; // 30*10 with 2 seconds = 10 minutes retry loop
  public static final int DEFAULT_DELAY_INTERVAL_IN_SECONDS = 2;

  private static final Logger LOGGER = LoggerFactory.getLogger(RetryElasticsearchClient.class);
  @Autowired private RestHighLevelClient esClient;

  @Autowired private ElasticsearchTask elasticsearchTask;
  private RequestOptions requestOptions = RequestOptions.DEFAULT;
  private int numberOfRetries = DEFAULT_NUMBER_OF_RETRIES;
  private int delayIntervalInSeconds = DEFAULT_DELAY_INTERVAL_IN_SECONDS;

  public boolean isHealthy() {
    try {
      final ClusterHealthResponse response =
          esClient
              .cluster()
              .health(
                  new ClusterHealthRequest().timeout(TimeValue.timeValueMillis(500)),
                  RequestOptions.DEFAULT);
      final ClusterHealthStatus status = response.getStatus();
      return !response.isTimedOut() && !status.equals(ClusterHealthStatus.RED);
    } catch (IOException e) {
      LOGGER.error(
          String.format(
              "Couldn't connect to Elasticsearch due to %s. Return unhealthy state.",
              e.getMessage()),
          e);
      return false;
    }
  }

  public int getNumberOfRetries() {
    return numberOfRetries;
  }

  public RetryElasticsearchClient setNumberOfRetries(int numberOfRetries) {
    this.numberOfRetries = numberOfRetries;
    return this;
  }

  public int getDelayIntervalInSeconds() {
    return delayIntervalInSeconds;
  }

  public RetryElasticsearchClient setDelayIntervalInSeconds(int delayIntervalInSeconds) {
    this.delayIntervalInSeconds = delayIntervalInSeconds;
    return this;
  }

  public RetryElasticsearchClient setRequestOptions(RequestOptions requestOptions) {
    this.requestOptions = requestOptions;
    return this;
  }

  public void refresh(final String indexPattern) {
    executeWithRetries(
        "Refresh " + indexPattern,
        () -> {
          try {
            for (var index : getFilteredIndices(indexPattern)) {
              esClient.indices().refresh(new RefreshRequest(index), requestOptions);
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          return true;
        });
  }

  public long getNumberOfDocumentsFor(String... indexPatterns) {
    return executeWithRetries(
        "Count number of documents in " + Arrays.asList(indexPatterns),
        () -> esClient.count(new CountRequest(indexPatterns), requestOptions).getCount());
  }

  public Set<String> getIndexNames(String namePattern) {
    return executeWithRetries(
        "Get indices for " + namePattern,
        () -> {
          try {
            final GetIndexResponse response =
                esClient.indices().get(new GetIndexRequest(namePattern), RequestOptions.DEFAULT);
            return Set.of(response.getIndices());
          } catch (ElasticsearchException e) {
            if (e.status().equals(RestStatus.NOT_FOUND)) {
              return Set.of();
            }
            throw e;
          }
        });
  }

  public boolean createIndex(CreateIndexRequest createIndexRequest) {
    return executeWithRetries(
        "CreateIndex " + createIndexRequest.index(),
        () -> {
          if (!indicesExist(createIndexRequest.index())) {
            return esClient.indices().create(createIndexRequest, requestOptions).isAcknowledged();
          }
          return true;
        });
  }

  public boolean createOrUpdateDocument(String name, String id, Map source) {
    return executeWithRetries(
        () -> {
          final IndexResponse response =
              esClient.index(
                  new IndexRequest(name).id(id).source(source, XContentType.JSON), requestOptions);
          final DocWriteResponse.Result result = response.getResult();
          return result.equals(DocWriteResponse.Result.CREATED)
              || result.equals(DocWriteResponse.Result.UPDATED);
        });
  }

  public boolean createOrUpdateDocument(String name, String id, String source) {
    return executeWithRetries(
        () -> {
          final IndexResponse response =
              esClient.index(
                  new IndexRequest(name).id(id).source(source, XContentType.JSON), requestOptions);
          final DocWriteResponse.Result result = response.getResult();
          return result.equals(DocWriteResponse.Result.CREATED)
              || result.equals(DocWriteResponse.Result.UPDATED);
        });
  }

  public boolean documentExists(String name, String id) {
    return executeWithGivenRetries(
        10,
        String.format("Exists document from %s with id %s", name, id),
        () -> esClient.exists(new GetRequest(name).id(id), requestOptions),
        null);
  }

  public Map<String, Object> getDocument(String name, String id) {
    return executeWithGivenRetries(
        10,
        String.format("Get document from %s with id %s", name, id),
        () -> {
          final GetRequest request = new GetRequest(name).id(id);
          final GetResponse response = esClient.get(request, requestOptions);
          if (response.isExists()) {
            return response.getSourceAsMap();
          } else {
            return null;
          }
        },
        null);
  }

  public boolean deleteDocumentsByQuery(String indexName, QueryBuilder query) {
    return executeWithRetries(
        () -> {
          final DeleteByQueryRequest request = new DeleteByQueryRequest(indexName).setQuery(query);
          final BulkByScrollResponse response =
              esClient.deleteByQuery(request, RequestOptions.DEFAULT);
          return response.getBulkFailures().isEmpty() && response.getDeleted() > 0;
        });
  }

  public boolean deleteDocument(String name, String id) {
    return executeWithRetries(
        () -> {
          final DeleteResponse response =
              esClient.delete(new DeleteRequest(name).id(id), requestOptions);
          final DocWriteResponse.Result result = response.getResult();
          return result.equals(DocWriteResponse.Result.DELETED);
        });
  }

  private boolean templatesExist(final String templatePattern) throws IOException {
    return esClient
        .indices()
        .existsIndexTemplate(
            new ComposableIndexTemplateExistRequest(templatePattern), requestOptions);
  }

  public boolean createTemplate(PutComposableIndexTemplateRequest request) {
    return executeWithRetries(
        "CreateTemplate " + request.name(),
        () -> {
          if (!templatesExist(request.name())) {
            return esClient.indices().putIndexTemplate(request, requestOptions).isAcknowledged();
          }
          return true;
        });
  }

  public boolean deleteTemplatesFor(final String templateNamePattern) {
    return executeWithRetries(
        "DeleteTemplate " + templateNamePattern,
        () -> {
          if (templatesExist(templateNamePattern)) {
            return esClient
                .indices()
                .deleteIndexTemplate(
                    new DeleteComposableIndexTemplateRequest(templateNamePattern), requestOptions)
                .isAcknowledged();
          }
          return true;
        });
  }

  private boolean indicesExist(final String indexPattern) throws IOException {
    return esClient
        .indices()
        .exists(
            new GetIndexRequest(indexPattern)
                .indicesOptions(IndicesOptions.fromOptions(true, false, true, false)),
            requestOptions);
  }

  private Set<String> getFilteredIndices(final String indexPattern) throws IOException {
    return Arrays.stream(
            esClient
                .indices()
                .get(new GetIndexRequest(indexPattern), RequestOptions.DEFAULT)
                .getIndices())
        .sequential()
        .collect(Collectors.toSet());
  }

  public boolean deleteIndicesFor(final String indexPattern) {
    return executeWithRetries(
        "DeleteIndices " + indexPattern,
        () -> {
          for (var index : getFilteredIndices(indexPattern)) {
            esClient.indices().delete(new DeleteIndexRequest(index), RequestOptions.DEFAULT);
          }
          return true;
        });
  }

  protected Map<String, String> getIndexSettingsFor(String indexName, String... fields) {
    return executeWithRetries(
        "GetIndexSettings " + indexName,
        () -> {
          final Map<String, String> settings = new HashMap<>();
          final GetSettingsResponse response =
              esClient
                  .indices()
                  .getSettings(new GetSettingsRequest().indices(indexName), requestOptions);
          for (String field : fields) {
            settings.put(field, response.getSetting(indexName, field));
          }
          return settings;
        });
  }

  public String getOrDefaultRefreshInterval(String indexName, String defaultValue) {
    final Map<String, String> settings = getIndexSettingsFor(indexName, REFRESH_INTERVAL);
    String refreshInterval = getOrDefaultForNullValue(settings, REFRESH_INTERVAL, defaultValue);
    if (refreshInterval.trim().equals(NO_REFRESH)) {
      refreshInterval = defaultValue;
    }
    return refreshInterval;
  }

  public String getOrDefaultNumbersOfReplica(String indexName, String defaultValue) {
    final Map<String, String> settings = getIndexSettingsFor(indexName, NUMBERS_OF_REPLICA);
    String numbersOfReplica = getOrDefaultForNullValue(settings, NUMBERS_OF_REPLICA, defaultValue);
    if (numbersOfReplica.trim().equals(NO_REPLICA)) {
      numbersOfReplica = defaultValue;
    }
    return numbersOfReplica;
  }

  public boolean setIndexSettingsFor(Settings settings, String indexPattern) {
    return executeWithRetries(
        "SetIndexSettings " + indexPattern,
        () ->
            esClient
                .indices()
                .putSettings(
                    new UpdateSettingsRequest(indexPattern).settings(settings), requestOptions)
                .isAcknowledged());
  }

  public boolean addPipeline(String name, String definition) {
    final BytesReference content = new BytesArray(definition.getBytes());
    return executeWithRetries(
        "AddPipeline " + name,
        () ->
            esClient
                .ingest()
                .putPipeline(
                    new PutPipelineRequest(name, content, XContentType.JSON), requestOptions)
                .isAcknowledged());
  }

  public boolean removePipeline(String name) {
    return executeWithRetries(
        "RemovePipeline " + name,
        () ->
            esClient
                .ingest()
                .deletePipeline(new DeletePipelineRequest(name), requestOptions)
                .isAcknowledged());
  }

  public void reindex(final ReindexRequest reindexRequest) {
    reindex(reindexRequest, true);
  }

  public void reindex(final ReindexRequest reindexRequest, boolean checkDocumentCount) {
    executeWithRetries(
        "Reindex "
            + Arrays.asList(reindexRequest.getSearchRequest().indices())
            + " -> "
            + reindexRequest.getDestination().index(),
        () -> {
          final String srcIndices = reindexRequest.getSearchRequest().indices()[0];
          final long srcCount = getNumberOfDocumentsFor(srcIndices);
          if (checkDocumentCount) {
            final String dstIndex = reindexRequest.getDestination().indices()[0];
            final long dstCount = getNumberOfDocumentsFor(dstIndex + "*");
            if (srcCount == dstCount) {
              LOGGER.info("Reindex of {} -> {} is already done.", srcIndices, dstIndex);
              return true;
            }
          }
          final String taskId =
              esClient.submitReindexTask(reindexRequest, requestOptions).getTask();
          TimeUnit.of(ChronoUnit.MILLIS).sleep(2_000);
          return waitUntilTaskIsCompleted(taskId, srcCount);
        },
        done -> !done);
  }

  // Returns if task is completed under this conditions:
  // - If the response is empty we can immediately return false to force a new reindex in outer
  // retry loop
  // - If the response has a status with uncompleted flag and a sum of changed documents
  // (created,updated and deleted documents) not equal to to total documents
  //   we need to wait and poll again the task status
  private boolean waitUntilTaskIsCompleted(String taskId, long srcCount) {
    final String[] taskIdParts = taskId.split(":");
    final String nodeId = taskIdParts[0];
    final Long smallTaskId = Long.parseLong(taskIdParts[1]);
    final Optional<GetTaskResponse> taskResponse =
        executeWithGivenRetries(
            Integer.MAX_VALUE,
            "GetTaskInfo{" + nodeId + "},{" + smallTaskId + "}",
            () -> {
              elasticsearchTask.checkForErrorsOrFailures(nodeId, smallTaskId.intValue());
              return esClient.tasks().get(new GetTaskRequest(nodeId, smallTaskId), requestOptions);
            },
            elasticsearchTask::needsToPollAgain);
    if (taskResponse.isPresent()) {
      final long total = elasticsearchTask.getTotal(taskResponse.get());
      LOGGER.info("Source docs: {}, Migrated docs: {}", srcCount, total);
      return total == srcCount;
    } else {
      // need to reindex again
      return false;
    }
  }

  public int doWithEachSearchResult(
      SearchRequest searchRequest, Consumer<SearchHit> searchHitConsumer) {
    return executeWithRetries(
        () -> {
          int doneOnSearchHits = 0;
          searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
          SearchResponse response = esClient.search(searchRequest, requestOptions);

          String scrollId = null;
          while (response.getHits().getHits().length > 0) {
            Arrays.stream(response.getHits().getHits()).sequential().forEach(searchHitConsumer);
            doneOnSearchHits += response.getHits().getHits().length;

            scrollId = response.getScrollId();
            final SearchScrollRequest scrollRequest =
                new SearchScrollRequest(scrollId)
                    .scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
            response = esClient.scroll(scrollRequest, requestOptions);
          }
          if (scrollId != null) {
            final ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            clearScrollRequest.addScrollId(scrollId);
            esClient.clearScroll(clearScrollRequest, requestOptions);
          }
          return doneOnSearchHits;
        });
  }

  public <T> List<T> searchWithScroll(
      SearchRequest searchRequest, Class<T> resultClass, ObjectMapper objectMapper) {
    final long totalHits =
        executeWithRetries(
            "Count search results",
            () -> esClient.search(searchRequest, requestOptions).getHits().getTotalHits().value);
    return executeWithRetries(
        "Search with scroll",
        () -> scroll(searchRequest, resultClass, objectMapper),
        resultList -> resultList.size() != totalHits);
  }

  private <T> List<T> scroll(SearchRequest searchRequest, Class<T> clazz, ObjectMapper objectMapper)
      throws IOException {
    final List<T> results = new ArrayList<>();
    searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
    SearchResponse response = esClient.search(searchRequest, requestOptions);

    String scrollId = null;
    while (response.getHits().getHits().length > 0) {
      results.addAll(
          CollectionUtil.map(
              response.getHits().getHits(),
              searchHit -> searchHitToObject(searchHit, clazz, objectMapper)));

      scrollId = response.getScrollId();
      final SearchScrollRequest scrollRequest =
          new SearchScrollRequest(scrollId).scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
      response = esClient.scroll(scrollRequest, requestOptions);
    }
    if (scrollId != null) {
      final ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
      clearScrollRequest.addScrollId(scrollId);
      esClient.clearScroll(clearScrollRequest, requestOptions);
    }
    return results;
  }

  private <T> T searchHitToObject(SearchHit searchHit, Class<T> clazz, ObjectMapper objectMapper) {
    try {
      return objectMapper.readValue(searchHit.getSourceAsString(), clazz);
    } catch (JsonProcessingException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error while reading entity of type %s from Elasticsearch!", clazz.getName()),
          e);
    }
  }

  // ------------------- Retry part ------------------
  private <T> T executeWithRetries(CheckedSupplier<T> supplier) {
    return executeWithRetries("", supplier, null);
  }

  private <T> T executeWithRetries(String operationName, CheckedSupplier<T> supplier) {
    return executeWithRetries(operationName, supplier, null);
  }

  private <T> T executeWithRetries(
      String operationName, CheckedSupplier<T> supplier, Predicate<T> retryPredicate) {
    return executeWithGivenRetries(numberOfRetries, operationName, supplier, retryPredicate);
  }

  private <T> T executeWithGivenRetries(
      int retries, String operationName, CheckedSupplier<T> operation, Predicate<T> predicate) {
    try {
      final RetryPolicy<T> retryPolicy =
          new RetryPolicy<T>()
              .handle(IOException.class, ElasticsearchException.class)
              .withDelay(Duration.ofSeconds(delayIntervalInSeconds))
              .withMaxAttempts(retries)
              .onRetry(
                  e ->
                      LOGGER.info(
                          "Retrying #{} {} due to {}",
                          e.getAttemptCount(),
                          operationName,
                          e.getLastFailure()))
              .onAbort(e -> LOGGER.error("Abort {} by {}", operationName, e.getFailure()))
              .onRetriesExceeded(
                  e ->
                      LOGGER.error(
                          "Retries {} exceeded for {}", e.getAttemptCount(), operationName));
      if (predicate != null) {
        retryPolicy.handleResultIf(predicate);
      }
      return Failsafe.with(retryPolicy).get(operation);
    } catch (Exception e) {
      throw new TasklistRuntimeException(
          "Couldn't execute operation "
              + operationName
              + " on elasticsearch for "
              + numberOfRetries
              + " attempts with "
              + delayIntervalInSeconds
              + " seconds waiting.",
          e);
    }
  }

  public boolean createComponentTemplate(final PutComponentTemplateRequest request) {
    return executeWithRetries(
        "CreateComponentTemplate " + request.name(),
        () -> {
          if (!templatesExist(request.name())) {
            return esClient
                .cluster()
                .putComponentTemplate(request, requestOptions)
                .isAcknowledged();
          }
          return false;
        });
  }

  public boolean putLifeCyclePolicy(final PutLifecyclePolicyRequest putLifecyclePolicyRequest) {
    return executeWithRetries(
        String.format("Put LifeCyclePolicy %s ", putLifecyclePolicyRequest.getName()),
        () ->
            esClient
                .indexLifecycle()
                .putLifecyclePolicy(putLifecyclePolicyRequest, requestOptions)
                .isAcknowledged(),
        null);
  }
}
