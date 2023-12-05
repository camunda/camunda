/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.os;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.tasklist.util.OpenSearchUtil;
import jakarta.json.stream.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedSupplier;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.*;
import org.opensearch.client.opensearch.ingest.Processor;
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
  public static final String SCROLL_KEEP_ALIVE_MS = "60000ms";
  public static final int DEFAULT_NUMBER_OF_RETRIES =
      30 * 10; // 30*10 with 2 seconds = 10 minutes retry loop
  public static final int DEFAULT_DELAY_INTERVAL_IN_SECONDS = 2;
  private static final Logger LOGGER = LoggerFactory.getLogger(RetryOpenSearchClient.class);
  @Autowired private OpenSearchClient openSearchClient;
  private int numberOfRetries = DEFAULT_NUMBER_OF_RETRIES;
  private int delayIntervalInSeconds = DEFAULT_DELAY_INTERVAL_IN_SECONDS;

  @Autowired private OpenSearchInternalTask openSearchInternalTask;

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
    final CountResponse countResponse =
        executeWithRetries(
            "Count number of documents in " + Arrays.asList(indexPatterns),
            () -> openSearchClient.count(c -> c.index(List.of(indexPatterns))),
            (c) -> c.shards().failures().size() > 0);
    return countResponse.count();
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

  public boolean documentExists(String name, String id) {
    return executeWithGivenRetries(
        10,
        String.format("Exists document from %s with id %s", name, id),
        () -> openSearchClient.exists(e -> e.index(name).id(id)).value(),
        null);
  }

  public Map<String, Object> getDocument(String name, String id) {
    return (Map<String, Object>)
        executeWithGivenRetries(
            10,
            String.format("Get document from %s with id %s", name, id),
            () -> {
              final GetRequest request = new GetRequest.Builder().index(name).id(id).build();
              final GetResponse response2 = openSearchClient.get(request, Object.class);
              if (response2.found()) {
                return response2.source();
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

  public IndexSettings getIndexSettingsFor(String indexName, String... fields) {
    return executeWithRetries(
        "GetIndexSettings " + indexName,
        () -> {
          final GetIndicesSettingsResponse response =
              openSearchClient.indices().getSettings(s -> s.index(indexName).flatSettings(true));

          return response.result().get(indexName).settings();
        });
  }

  public String getOrDefaultRefreshInterval(String indexName, String defaultValue) {
    final IndexSettings settings = getIndexSettingsFor(indexName, REFRESH_INTERVAL);
    String refreshInterval;
    if (settings.refreshInterval() == null) {
      refreshInterval = defaultValue;
    } else {
      refreshInterval = settings.refreshInterval().time();
    }
    if (refreshInterval.trim().equals(NO_REFRESH)) {
      refreshInterval = defaultValue;
    }
    return refreshInterval;
  }

  public String getOrDefaultNumbersOfReplica(String indexName, String defaultValue) {
    final IndexSettings settings = getIndexSettingsFor(indexName, NUMBERS_OF_REPLICA);

    String numbersOfReplica;
    if (settings.numberOfReplicas() == null) {
      numbersOfReplica = defaultValue;
    } else {
      numbersOfReplica = settings.numberOfReplicas();
    }
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

  public boolean addPipeline(String name, List<String> processorDefinitions) {
    return executeWithRetries(
        "AddPipeline " + name,
        () -> {
          final List<Processor> processors =
              processorDefinitions.stream()
                  .map(
                      definition -> {
                        final JsonpMapper mapper = openSearchClient._transport().jsonpMapper();
                        final JsonParser parser =
                            mapper
                                .jsonProvider()
                                .createParser(new ByteArrayInputStream(definition.getBytes()));
                        return Processor._DESERIALIZER.deserialize(parser, mapper);
                      })
                  .collect(Collectors.toList());

          return openSearchClient
              .ingest()
              .putPipeline(i -> i.id(name).processors(processors))
              .acknowledged();
        });
  }

  public boolean removePipeline(String name) {
    return executeWithRetries(
        "RemovePipeline " + name,
        () -> openSearchClient.ingest().deletePipeline(dp -> dp.id(name)).acknowledged());
  }

  public void reindex(final ReindexRequest reindexRequest) {
    reindex(reindexRequest, true);
  }

  private void refreshAndRetryOnShardFailures(final String indexPattern) {
    executeWithRetries(
        "Refresh " + indexPattern,
        () -> openSearchClient.indices().refresh(r -> r.index(indexPattern)),
        (r) -> r.shards().failures().size() > 0);
  }

  public void reindex(final ReindexRequest reindexRequest, boolean checkDocumentCount) {
    executeWithRetries(
        "Reindex "
            + Arrays.asList(reindexRequest.source().index())
            + " -> "
            + reindexRequest.dest().index(),
        () -> {
          final var srcIndices = reindexRequest.source().index().get(0);
          final var dstIndex = reindexRequest.dest().index();
          final var srcCount = getNumberOfDocumentsFor(srcIndices);

          final var taskIds =
              openSearchInternalTask.getRunningReindexTasksIdsFor(srcIndices, dstIndex);
          final String taskId;
          if (taskIds == null || taskIds.isEmpty()) {
            if (checkDocumentCount) {
              refreshAndRetryOnShardFailures(dstIndex + "*");
              final var dstCount = getNumberOfDocumentsFor(dstIndex + "*");
              if (srcCount == dstCount) {
                LOGGER.info("Reindex of {} -> {} is already done.", srcIndices, dstIndex);
                return true;
              }
            }
            taskId = openSearchClient.reindex(reindexRequest).task();
          } else {
            LOGGER.info(
                "There is an already running reindex task for [{}] -> [{}]. Will not submit another reindex task but wait for completion of this task",
                srcIndices,
                dstIndex);
            taskId = taskIds.get(0);
          }
          TimeUnit.of(ChronoUnit.MILLIS).sleep(2_000);
          if (checkDocumentCount) {
            return waitUntilTaskIsCompleted(taskId, srcCount);
          } else {
            return waitUntilTaskIsCompleted(taskId);
          }
        },
        done -> !done);
  }

  private boolean waitUntilTaskIsCompleted(String taskId) {
    return waitUntilTaskIsCompleted(taskId, null);
  }

  // Returns if task is completed under this conditions:
  // - If the response is empty we can immediately return false to force a new reindex in outer
  // retry loop
  // - If the response has a status with uncompleted flag and a sum of changed documents
  // (created,updated and deleted documents) not equal to to total documents
  //   we need to wait and poll again the task status
  private boolean waitUntilTaskIsCompleted(String taskId, Long srcCount) {
    final GetTasksResponse taskResponse =
        executeWithGivenRetries(
            Integer.MAX_VALUE,
            "GetTaskInfo{" + taskId + "}",
            () -> {
              final GetTasksResponse tasksResponse =
                  openSearchClient.tasks().get(t -> t.taskId(taskId));
              openSearchInternalTask.checkForErrorsOrFailures(tasksResponse);
              return tasksResponse;
            },
            openSearchInternalTask::needsToPollAgain);
    if (taskResponse != null) {
      final long total = openSearchInternalTask.getTotal(taskResponse);
      LOGGER.info("Source docs: {}, Migrated docs: {}", srcCount, total);
      return total == srcCount;
    } else {
      // need to reindex again
      return false;
    }
  }

  public <T> List<T> searchWithScroll(
      SearchRequest searchRequest, Class<T> resultClass, ObjectMapper objectMapper) {
    final long totalHits =
        executeWithRetries(
            "Count search results",
            () -> openSearchClient.search(searchRequest, resultClass).hits().total().value());
    return executeWithRetries(
        "Search with scroll",
        () -> scroll(searchRequest, resultClass, objectMapper),
        resultList -> resultList.size() != totalHits);
  }

  private <T> List<T> scroll(SearchRequest searchRequest, Class<T> clazz, ObjectMapper objectMapper)
      throws IOException {
    final List<T> results = new ArrayList<>();
    SearchResponse<T> response = openSearchClient.search(searchRequest, clazz);

    String scrollId = null;
    while (response.hits().hits().size() > 0) {
      results.addAll(CollectionUtil.map(response.hits().hits(), Hit::source));

      scrollId = response.scrollId();
      final ScrollRequest scrollRequest =
          new ScrollRequest.Builder()
              .scrollId(scrollId)
              .scroll(s -> s.time(SCROLL_KEEP_ALIVE_MS))
              .build();
      response = openSearchClient.scroll(scrollRequest, clazz);
    }
    OpenSearchUtil.clearScroll(scrollId, openSearchClient);
    return results;
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
              + " on opensearch for "
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

  public int doWithEachSearchResult(
      SearchRequest.Builder searchRequest, Consumer<Hit> searchHitConsumer) {
    return executeWithRetries(
        () -> {
          int doneOnSearchHits = 0;
          searchRequest.scroll(Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS)));
          SearchResponse response = openSearchClient.search(searchRequest.build(), Object.class);

          String scrollId = null;
          while (response.hits().hits().size() > 0) {
            response.hits().hits().stream().forEach(searchHitConsumer);
            doneOnSearchHits += response.hits().hits().size();

            scrollId = response.scrollId();
            final ScrollRequest scrollRequest =
                new ScrollRequest.Builder()
                    .scrollId(scrollId)
                    .scroll(Time.of(t -> t.time(SCROLL_KEEP_ALIVE_MS)))
                    .build();

            response = openSearchClient.scroll(scrollRequest, Object.class);
          }
          OpenSearchUtil.clearScroll(scrollId, openSearchClient);
          return doneOnSearchHits;
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
