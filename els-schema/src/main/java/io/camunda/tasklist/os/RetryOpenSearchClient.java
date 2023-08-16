/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.os;

import static io.camunda.tasklist.util.CollectionUtil.getOrDefaultForNullValue;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedSupplier;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndexResponse;
import org.opensearch.client.opensearch.indices.GetIndicesSettingsResponse;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class RetryOpenSearchClient {

  public static final String REFRESH_INTERVAL = "index.refresh_interval";
  public static final String NO_REFRESH = "-1";
  public static final String NUMBERS_OF_REPLICA = "index.number_of_replicas";
  public static final String NO_REPLICA = "0";
  public static final int SCROLL_KEEP_ALIVE_MS = 60_000;
  public static final int DEFAULT_NUMBER_OF_RETRIES =
      30 * 10; // 30*10 with 2 seconds = 10 minutes retry loop
  public static final int DEFAULT_DELAY_INTERVAL_IN_SECONDS = 2;
  private static final Logger LOGGER = LoggerFactory.getLogger(RetryOpenSearchClient.class);
  @Autowired private OpenSearchClient openSearchClient;
  private int numberOfRetries = DEFAULT_NUMBER_OF_RETRIES;
  private int delayIntervalInSeconds = DEFAULT_DELAY_INTERVAL_IN_SECONDS;

  @Autowired private OpenSearchTask openSearchTask;

  public boolean isHealthy() {
    try {
      final HealthResponse response =
          openSearchClient.cluster().health(h -> h.timeout(t -> t.time("500ms")));
      final HealthStatus status = response.status();
      return !response.timedOut() && !status.equals(HealthStatus.Red);
    } catch (IOException | OpenSearchException e) {
      LOGGER.error(
          String.format(
              "Couldn't connect to OpenSearch due to %s. Return unhealthy state.", e.getMessage()),
          e);
      return false;
    }
  }

  public int getNumberOfRetries() {
    return numberOfRetries;
  }

  public RetryOpenSearchClient setNumberOfRetries(int numberOfRetries) {
    this.numberOfRetries = numberOfRetries;
    return this;
  }

  public int getDelayIntervalInSeconds() {
    return delayIntervalInSeconds;
  }

  public RetryOpenSearchClient setDelayIntervalInSeconds(int delayIntervalInSeconds) {
    this.delayIntervalInSeconds = delayIntervalInSeconds;
    return this;
  }

  public void refresh(final String indexPattern) {
    executeWithRetries(
        "Refresh " + indexPattern,
        () -> {
          try {
            for (var index : getFilteredIndices(indexPattern)) {
              openSearchClient.indices().refresh(r -> r.index(List.of(index)));
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
        () -> openSearchClient.count(c -> c.index(List.of(indexPatterns))).count());
  }

  public Set<String> getIndexNames(String namePattern) {
    return executeWithRetries(
        "Get indices for " + namePattern,
        () -> {
          try {
            final GetIndexResponse response =
                openSearchClient.indices().get(i -> i.index(List.of(namePattern)));
            return response.result().keySet();
          } catch (OpenSearchException e) {
            if (e.status() == 404) {
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
            return openSearchClient.indices().create(createIndexRequest).acknowledged();
          }
          return true;
        });
  }

  public boolean createOrUpdateDocument(String name, String id, Map source) {

    return executeWithRetries(
        () -> {
          final IndexResponse response =
              openSearchClient.index(i -> i.index(name).id(id).document(source));
          final Result result = response.result();
          return result.equals(Result.Created) || result.equals(Result.Updated);
        });
  }

  public boolean createOrUpdateDocument(String name, String id, String source) {
    return executeWithRetries(
        () -> {
          final IndexResponse response =
              openSearchClient.index(i -> i.index(name).id(id).document(source));
          final Result result = response.result();
          return result.equals(Result.Created) || result.equals(Result.Updated);
        });
  }

  public boolean documentExists(String name, String id) {
    return executeWithGivenRetries(
        10,
        String.format("Exists document from %s with id %s", name, id),
        () -> openSearchClient.exists(e -> e.index(name).id(id)).value(),
        null);
  }

  public Map<String, Object> getDocument(String name, String id) {
    return executeWithGivenRetries(
        10,
        String.format("Get document from %s with id %s", name, id),
        () -> {
          final GetRequest request = new GetRequest.Builder().index(name).id(id).build();
          final GetResponse response = openSearchClient.get(request, null);
          if (response.found()) {
            return response.fields();
          } else {
            return null;
          }
        },
        null);
  }

  public boolean deleteDocumentsByQuery(String indexName, Query query) {
    return executeWithRetries(
        () -> {
          final DeleteByQueryRequest request =
              new DeleteByQueryRequest.Builder().index(List.of(indexName)).query(query).build();
          final DeleteByQueryResponse response = openSearchClient.deleteByQuery(request);
          return response.failures().isEmpty() && response.deleted() > 0;
        });
  }

  public boolean deleteDocument(String name, String id) {
    return executeWithRetries(
        () -> {
          final DeleteResponse response =
              openSearchClient.delete(new DeleteRequest.Builder().index(name).id(id).build());
          final Result result = response.result();
          return result.equals(Result.Deleted);
        });
  }

  private boolean templatesExist(final String templatePattern) throws IOException {
    return openSearchClient.indices().existsIndexTemplate(it -> it.name(templatePattern)).value();
  }

  public boolean createTemplate(PutIndexTemplateRequest request) {
    return executeWithRetries(
        "CreateTemplate " + request.name(),
        () -> {
          if (!templatesExist(request.name())) {
            return openSearchClient.indices().putIndexTemplate(request).acknowledged();
          }
          return true;
        });
  }

  public boolean deleteTemplatesFor(final String templateNamePattern) {
    return executeWithRetries(
        "DeleteTemplate " + templateNamePattern,
        () -> {
          if (templatesExist(templateNamePattern)) {
            return openSearchClient
                .indices()
                .deleteIndexTemplate(it -> it.name(templateNamePattern))
                .acknowledged();
          }
          return true;
        });
  }

  private boolean indicesExist(final String indexPattern) throws IOException {
    return openSearchClient
        .indices()
        .exists(e -> e.index(List.of(indexPattern)).ignoreUnavailable(true).allowNoIndices(false))
        .value();
  }

  private Set<String> getFilteredIndices(final String indexPattern) throws IOException {
    return openSearchClient.indices().get(i -> i.index(List.of(indexPattern))).result().keySet();
  }

  public boolean deleteIndicesFor(final String indexPattern) {
    return executeWithRetries(
        "DeleteIndices " + indexPattern,
        () -> {
          for (var index : getFilteredIndices(indexPattern)) {
            openSearchClient.indices().delete(d -> d.index(List.of(indexPattern)));
          }
          return true;
        });
  }

  protected Map<String, String> getIndexSettingsFor(String indexName, String... fields) {
    return executeWithRetries(
        "GetIndexSettings " + indexName,
        () -> {
          final Map<String, String> settings = new HashMap<>();
          final GetIndicesSettingsResponse response =
              openSearchClient.indices().getSettings(s -> s.index(List.of(indexName)));
          for (String field : fields) {
            settings.put(field, response.get(field).toString());
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

  public boolean setIndexSettingsFor(IndexSettings settings, String indexPattern) {
    return executeWithRetries(
        "SetIndexSettings " + indexPattern,
        () ->
            openSearchClient
                .indices()
                .putSettings(s -> s.index(indexPattern).settings(settings))
                .acknowledged());
  }

  public boolean addPipeline(String name, String definition) {
    // final BytesReference content = new BytesArray(definition.getBytes());
    return executeWithRetries(
        "AddPipeline " + name,
        () ->
            openSearchClient
                .ingest()
                .putPipeline(i -> i.id(name).meta(name, JsonData.of(definition)))
                .acknowledged());
  }

  public boolean removePipeline(String name) {
    return executeWithRetries(
        "RemovePipeline " + name,
        () -> openSearchClient.ingest().deletePipeline(dp -> dp.id(name)).acknowledged());
  }

  public void reindex(final ReindexRequest reindexRequest) {
    reindex(reindexRequest, true);
  }

  public void reindex(final ReindexRequest reindexRequest, boolean checkDocumentCount) {
    executeWithRetries(
        "Reindex "
            + Arrays.asList(reindexRequest.source().index())
            + " -> "
            + reindexRequest.dest().index(),
        () -> {
          final String srcIndices = reindexRequest.source().index().get(0);
          final long srcCount = getNumberOfDocumentsFor(srcIndices);
          if (checkDocumentCount) {
            final String dstIndex = reindexRequest.dest().index();
            final long dstCount = getNumberOfDocumentsFor(dstIndex + "*");
            if (srcCount == dstCount) {
              LOGGER.info("Reindex of {} -> {} is already done.", srcIndices, dstIndex);
              return true;
            }
          }
          final String task = openSearchClient.reindex(reindexRequest).task();
          TimeUnit.of(ChronoUnit.MILLIS).sleep(2_000);
          return waitUntilTaskIsCompleted(task, srcCount);
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
    final GetTasksResponse taskResponse =
        executeWithGivenRetries(
            Integer.MAX_VALUE,
            "GetTaskInfo{" + nodeId + "},{" + smallTaskId + "}",
            () -> {
              openSearchTask.checkForErrorsOrFailures(nodeId, smallTaskId.intValue());
              return openSearchClient.tasks().get(t -> t.taskId(String.valueOf(smallTaskId)));
            },
            openSearchTask::needsToPollAgain);
    if (taskResponse != null) {
      final long total = openSearchTask.getTotal(taskResponse);
      LOGGER.info("Source docs: {}, Migrated docs: {}", srcCount, total);
      return total == srcCount;
    } else {
      // need to reindex again
      return false;
    }
  }

  /*public int doWithEachSearchResult(
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
  }*/

  /*public <T> List<T> searchWithScroll(
      SearchRequest searchRequest, Class<T> resultClass, ObjectMapper objectMapper) {
    final long totalHits =
        executeWithRetries(
            "Count search results",
            () -> esClient.search(searchRequest, requestOptions).getHits().getTotalHits().value);
    return executeWithRetries(
        "Search with scroll",
        () -> scroll(searchRequest, resultClass, objectMapper),
        resultList -> resultList.size() != totalHits);
  }*/

  /*private <T> List<T> scroll(SearchRequest searchRequest, Class<T> clazz, ObjectMapper objectMapper)
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

  /*private <T> T searchHitToObject(SearchHit searchHit, Class<T> clazz, ObjectMapper objectMapper) {
    try {
      return objectMapper.readValue(searchHit.getSourceAsString(), clazz);
    } catch (JsonProcessingException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error while reading entity of type %s from Elasticsearch!", clazz.getName()),
          e);
    }
  }*/

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
              .handle(IOException.class, OpenSearchException.class)
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
            return openSearchClient.cluster().putComponentTemplate(request).acknowledged();
          }
          return false;
        });
  }

  // TODO -check lifecycle for openSearch
  /* public boolean putLifeCyclePolicy(final PutLifecyclePolicyRequest putLifecyclePolicyRequest) {

      openSearchClient.indices().

    return executeWithRetries(
        String.format("Put LifeCyclePolicy %s ", putLifecyclePolicyRequest.getName()),
        () ->
            openSearchClient
                .indexLifecycle()
                .putLifecyclePolicy(putLifecyclePolicyRequest, requestOptions)
                .isAcknowledged(),
        null);
  }*/
}
