/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static io.camunda.tasklist.schema.v86.IndexMapping.IndexMappingProperty.createIndexMappingProperty;
import static io.camunda.tasklist.util.CollectionUtil.getOrDefaultForNullValue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.v86.IndexMapping;
import io.camunda.tasklist.store.elasticsearch.dao.response.TaskResponse;
import io.camunda.tasklist.util.CollectionUtil;
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
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
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
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.indexlifecycle.GetLifecyclePolicyRequest;
import org.elasticsearch.client.indexlifecycle.GetLifecyclePolicyResponse;
import org.elasticsearch.client.indexlifecycle.PutLifecyclePolicyRequest;
import org.elasticsearch.client.indices.*;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.cluster.metadata.MappingMetadata;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
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

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private ElasticsearchInternalTask elasticsearchTask;

  @Autowired private TasklistProperties tasklistProperties;
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
    } catch (final IOException e) {
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

  public RetryElasticsearchClient setNumberOfRetries(final int numberOfRetries) {
    this.numberOfRetries = numberOfRetries;
    return this;
  }

  public int getDelayIntervalInSeconds() {
    return delayIntervalInSeconds;
  }

  public RetryElasticsearchClient setDelayIntervalInSeconds(final int delayIntervalInSeconds) {
    this.delayIntervalInSeconds = delayIntervalInSeconds;
    return this;
  }

  public RetryElasticsearchClient setRequestOptions(final RequestOptions requestOptions) {
    this.requestOptions = requestOptions;
    return this;
  }

  public void refresh(final String indexPattern) {
    executeWithRetries(
        "Refresh " + indexPattern,
        () -> {
          try {
            for (final var index : getFilteredIndices(indexPattern)) {
              esClient.indices().refresh(new RefreshRequest(index), requestOptions);
            }
          } catch (final IOException e) {
            throw new RuntimeException(e);
          }
          return true;
        });
  }

  private void refreshAndRetryOnShardFailures(final String indexPattern) {
    executeWithRetries(
        "Refresh " + indexPattern,
        () -> esClient.indices().refresh(new RefreshRequest(indexPattern), requestOptions),
        (r) -> r.getFailedShards() > 0);
  }

  public long getNumberOfDocumentsFor(final String... indexPatterns) {
    final var response =
        executeWithRetries(
            "Count number of documents in " + Arrays.asList(indexPatterns),
            () -> esClient.count(new CountRequest(indexPatterns), requestOptions),
            (c) -> c.getFailedShards() > 0);
    return response.getCount();
  }

  public Set<String> getIndexNames(final String namePattern) {
    return executeWithRetries(
        "Get indices for " + namePattern,
        () -> {
          try {
            final GetIndexResponse response =
                esClient.indices().get(new GetIndexRequest(namePattern), RequestOptions.DEFAULT);
            return Set.of(response.getIndices());
          } catch (final ElasticsearchException e) {
            if (e.status().equals(RestStatus.NOT_FOUND)) {
              return Set.of();
            }
            throw e;
          }
        });
  }

  public Set<String> getAliasesNames(final String namePattern) {
    return executeWithRetries(
        "Get aliases for " + namePattern,
        () -> {
          try {
            final GetAliasesRequest request = new GetAliasesRequest(namePattern);
            final GetAliasesResponse response =
                esClient.indices().getAlias(request, requestOptions);

            final Set<String> returnAliases = new HashSet<>();
            final Map<String, Set<AliasMetadata>> mapAliases = response.getAliases();
            for (final Map.Entry<String, Set<AliasMetadata>> a : mapAliases.entrySet()) {
              returnAliases.addAll(
                  a.getValue().stream().map(m -> m.getAlias()).collect(Collectors.toSet()));
            }

            return returnAliases;
          } catch (final ElasticsearchException e) {
            if (e.status().equals(RestStatus.NOT_FOUND)) {
              return Set.of();
            }
            throw e;
          }
        });
  }

  public boolean createIndex(final CreateIndexRequest createIndexRequest) {
    return executeWithRetries(
        "CreateIndex " + createIndexRequest.index(),
        () -> {
          if (!indicesExist(createIndexRequest.index())) {
            return esClient.indices().create(createIndexRequest, requestOptions).isAcknowledged();
          }

          final String replicas =
              getOrDefaultNumbersOfReplica(createIndexRequest.index(), NO_REPLICA);
          if (!replicas.equals(
              String.valueOf(tasklistProperties.getElasticsearch().getNumberOfReplicas()))) {
            final UpdateSettingsRequest updateSettingsRequest =
                new UpdateSettingsRequest(createIndexRequest.index());
            final Settings settings =
                Settings.builder()
                    .put(
                        NUMBERS_OF_REPLICA,
                        tasklistProperties.getElasticsearch().getNumberOfReplicas())
                    .build();
            updateSettingsRequest.settings(settings);
            esClient.indices().putSettings(updateSettingsRequest, requestOptions).isAcknowledged();
          }

          try {
            if (createIndexRequest.aliases() != null
                && !createIndexRequest.aliases().isEmpty()
                && !aliasExist(
                    createIndexRequest.aliases().iterator().next(), createIndexRequest.index())) {
              final IndicesAliasesRequest request = new IndicesAliasesRequest();
              final IndicesAliasesRequest.AliasActions aliasAction =
                  new IndicesAliasesRequest.AliasActions(
                          IndicesAliasesRequest.AliasActions.Type.ADD)
                      .index(createIndexRequest.index())
                      .alias(createIndexRequest.aliases().iterator().next().name())
                      .writeIndex(false);
              request.addAliasAction(aliasAction);

              esClient.indices().updateAliases(request, RequestOptions.DEFAULT);
              LOGGER.info(
                  "Alias is created. Index: {}, alias: {} ",
                  createIndexRequest.index(),
                  createIndexRequest.aliases().iterator().next().name());

              return true;
            }
          } catch (final Exception ex) {
            LOGGER.error(
                String.format(
                    "Exception occurred when creating an alias. Index: %s, alias: %s, error: %s ",
                    createIndexRequest.index(),
                    createIndexRequest.aliases().iterator().next().name(),
                    ex.getMessage()),
                ex);
          }
          return true;
        });
  }

  private boolean aliasExist(final Alias alias, final String index) throws IOException {
    final GetAliasesRequest aliasExistsReq = new GetAliasesRequest(alias.name()).indices(index);
    return esClient.indices().existsAlias(aliasExistsReq, RequestOptions.DEFAULT);
  }

  public boolean createOrUpdateDocument(final String name, final String id, final Map source) {
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

  public boolean createOrUpdateDocument(final String name, final String id, final String source) {
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

  public boolean documentExists(final String name, final String id) {
    return executeWithGivenRetries(
        10,
        String.format("Exists document from %s with id %s", name, id),
        () -> esClient.exists(new GetRequest(name).id(id), requestOptions),
        null);
  }

  public Map<String, Object> getDocument(final String name, final String id) {
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

  public boolean deleteDocumentsByQuery(final String indexName, final QueryBuilder query) {
    return executeWithRetries(
        () -> {
          final DeleteByQueryRequest request = new DeleteByQueryRequest(indexName).setQuery(query);
          final BulkByScrollResponse response =
              esClient.deleteByQuery(request, RequestOptions.DEFAULT);
          return response.getBulkFailures().isEmpty() && response.getDeleted() > 0;
        });
  }

  public boolean deleteDocument(final String name, final String id) {
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

  public boolean createTemplate(final PutComposableIndexTemplateRequest request) {
    return createTemplate(request, false);
  }

  public boolean createTemplate(
      final PutComposableIndexTemplateRequest request, final boolean overwrite) {
    return executeWithRetries(
        "CreateTemplate " + request.name(),
        () -> {
          if (overwrite || !templatesExist(request.name())) {
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
          for (final var index : getFilteredIndices(indexPattern)) {
            esClient.indices().delete(new DeleteIndexRequest(index), RequestOptions.DEFAULT);
          }
          return true;
        });
  }

  protected Map<String, String> getIndexSettingsFor(
      final String indexName, final String... fields) {
    return executeWithRetries(
        "GetIndexSettings " + indexName,
        () -> {
          final Map<String, String> settings = new HashMap<>();
          final GetSettingsResponse response =
              esClient
                  .indices()
                  .getSettings(new GetSettingsRequest().indices(indexName), requestOptions);
          for (final String field : fields) {
            settings.put(field, response.getSetting(indexName, field));
          }
          return settings;
        });
  }

  protected Map<String, String> getComponentTemplateProperties(
      final String templatePattern, final String... fields) {
    return executeWithRetries(
        "GetComponentTemplateSettings " + templatePattern,
        () -> {
          final Map<String, String> settings = new HashMap<>();
          final GetComponentTemplatesRequest request =
              new GetComponentTemplatesRequest(templatePattern);
          final GetComponentTemplatesResponse response =
              esClient.cluster().getComponentTemplate(request, requestOptions);
          if (response.getComponentTemplates().get(templatePattern) != null) {
            for (final String field : fields) {
              settings.put(
                  field,
                  response
                      .getComponentTemplates()
                      .get(templatePattern)
                      .template()
                      .settings()
                      .get(field));
            }
          }
          return settings;
        });
  }

  public String getOrDefaultRefreshInterval(final String indexName, final String defaultValue) {
    final Map<String, String> settings = getIndexSettingsFor(indexName, REFRESH_INTERVAL);
    String refreshInterval = getOrDefaultForNullValue(settings, REFRESH_INTERVAL, defaultValue);
    if (refreshInterval.trim().equals(NO_REFRESH)) {
      refreshInterval = defaultValue;
    }
    return refreshInterval;
  }

  public String getOrDefaultNumbersOfReplica(final String indexName, final String defaultValue) {
    final Map<String, String> settings = getIndexSettingsFor(indexName, NUMBERS_OF_REPLICA);
    String numbersOfReplica = getOrDefaultForNullValue(settings, NUMBERS_OF_REPLICA, defaultValue);
    if (numbersOfReplica.trim().equals(NO_REPLICA)) {
      numbersOfReplica = defaultValue;
    }
    return numbersOfReplica;
  }

  public String getOrDefaultComponentTemplateNumbersOfReplica(
      final String templatePattern, final String defaultValue) {
    final Map<String, String> settings =
        getComponentTemplateProperties(templatePattern, NUMBERS_OF_REPLICA);
    String numbersOfReplica = getOrDefaultForNullValue(settings, NUMBERS_OF_REPLICA, defaultValue);
    if (numbersOfReplica.trim().equals(NO_REPLICA)) {
      numbersOfReplica = defaultValue;
    }
    return numbersOfReplica;
  }

  public boolean setIndexSettingsFor(final Settings settings, final String indexPattern) {
    return executeWithRetries(
        "SetIndexSettings " + indexPattern,
        () ->
            esClient
                .indices()
                .putSettings(
                    new UpdateSettingsRequest(indexPattern).settings(settings), requestOptions)
                .isAcknowledged());
  }

  public boolean addPipeline(final String name, final String definition) {
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

  public boolean removePipeline(final String name) {
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

  public void reindex(final ReindexRequest reindexRequest, final boolean checkDocumentCount) {
    executeWithRetries(
        "Reindex "
            + Arrays.asList(reindexRequest.getSearchRequest().indices())
            + " -> "
            + reindexRequest.getDestination().index(),
        () -> {
          final var srcIndices = reindexRequest.getSearchRequest().indices()[0];
          final var dstIndex = reindexRequest.getDestination().indices()[0];
          final var srcCount = getNumberOfDocumentsFor(srcIndices);

          final var taskIds = elasticsearchTask.getRunningReindexTasksIdsFor(srcIndices, dstIndex);
          final String taskId;

          if (taskIds == null || taskIds.isEmpty()) {
            // no running reindex task
            if (checkDocumentCount) {
              refreshAndRetryOnShardFailures(dstIndex + "*");
              final var dstCount = getNumberOfDocumentsFor(dstIndex + "*");
              if (srcCount == dstCount) {
                LOGGER.info("Reindex of {} -> {} is already done.", srcIndices, dstIndex);
                return true;
              }
            }
            taskId = esClient.submitReindexTask(reindexRequest, requestOptions).getTask();
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

  private boolean waitUntilTaskIsCompleted(final String taskId) {
    return waitUntilTaskIsCompleted(taskId, null);
  }

  // Returns if task is completed under this conditions:
  // - If the response is empty we can immediately return false to force a new reindex in outer
  // retry loop
  // - If the response has a status with uncompleted flag and a sum of changed documents
  // (created,updated and deleted documents) not equal to to total documents
  //   we need to wait and poll again the task status
  private boolean waitUntilTaskIsCompleted(final String taskId, final Long srcCount) {
    final String[] taskIdParts = taskId.split(":");
    final String nodeId = taskIdParts[0];
    final Long smallTaskId = Long.parseLong(taskIdParts[1]);

    final Optional<TaskResponse> maybeTaskResponse =
        executeWithGivenRetries(
            Integer.MAX_VALUE,
            "GetTaskInfo{" + nodeId + "},{" + smallTaskId + "}",
            () -> {
              final var result = elasticsearchTask.getTaskResponse(taskId);

              if (result.isLeft()) {
                final var exception = result.getLeft();
                final var message = exception.getMessage();
                LOGGER.warn(
                    String.format(
                        "Failed to retrieve TaskInfo {%s},{%d}: %s", nodeId, smallTaskId, message),
                    exception);
                // return empty result so that the entire reindex task gets retried
                return Optional.empty();
              }

              final var taskResponse = result.get();
              elasticsearchTask.checkForErrorsOrFailures(taskResponse);

              LOGGER.info(
                  "TaskId: {}, Progress: {}%",
                  taskId, String.format("%.2f", taskResponse.getProgress() * 100.0D));

              return Optional.of(taskResponse);
            },
            elasticsearchTask::needsToPollAgain);

    if (maybeTaskResponse.isPresent()) {
      final long total = maybeTaskResponse.get().getTaskStatus().getTotal();

      if (srcCount != null) {
        LOGGER.info("Source docs: {}, Migrated docs: {}", srcCount, total);
        return total == srcCount;
      } else {
        LOGGER.info("Migrated docs: {}", total);
        return maybeTaskResponse.get().isCompleted();
      }
    } else {
      // need to reindex again
      return false;
    }
  }

  public int doWithEachSearchResult(
      final SearchRequest searchRequest, final Consumer<SearchHit> searchHitConsumer) {
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
      final SearchRequest searchRequest,
      final Class<T> resultClass,
      final ObjectMapper objectMapper) {
    final long totalHits =
        executeWithRetries(
            "Count search results",
            () -> esClient.search(searchRequest, requestOptions).getHits().getTotalHits().value);
    return executeWithRetries(
        "Search with scroll",
        () -> scroll(searchRequest, resultClass, objectMapper),
        resultList -> resultList.size() != totalHits);
  }

  private <T> List<T> scroll(
      final SearchRequest searchRequest, final Class<T> clazz, final ObjectMapper objectMapper)
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

  private <T> T searchHitToObject(
      final SearchHit searchHit, final Class<T> clazz, final ObjectMapper objectMapper) {
    try {
      return objectMapper.readValue(searchHit.getSourceAsString(), clazz);
    } catch (final JsonProcessingException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error while reading entity of type %s from Elasticsearch!", clazz.getName()),
          e);
    }
  }

  // ------------------- Retry part ------------------
  private <T> T executeWithRetries(final CheckedSupplier<T> supplier) {
    return executeWithRetries("", supplier, null);
  }

  private <T> T executeWithRetries(final String operationName, final CheckedSupplier<T> supplier) {
    return executeWithRetries(operationName, supplier, null);
  }

  private <T> T executeWithRetries(
      final String operationName,
      final CheckedSupplier<T> supplier,
      final Predicate<T> retryPredicate) {
    return executeWithGivenRetries(numberOfRetries, operationName, supplier, retryPredicate);
  }

  private <T> T executeWithGivenRetries(
      final int retries,
      final String operationName,
      final CheckedSupplier<T> operation,
      final Predicate<T> predicate) {
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
      return Failsafe.with(retryPolicy)
          .get(
              () -> {
                try {
                  return operation.get();
                } catch (final ElasticsearchException e) {
                  if (e.status().equals(RestStatus.NOT_FOUND)) {
                    return null;
                  }
                  throw e;
                }
              });
    } catch (final Exception e) {
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
          if (!templatesExist(request.name())
              || !getOrDefaultComponentTemplateNumbersOfReplica(request.name(), NO_REPLICA)
                  .equals(
                      String.valueOf(
                          tasklistProperties.getElasticsearch().getNumberOfReplicas()))) {
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

  public GetLifecyclePolicyResponse getLifeCyclePolicy(
      final GetLifecyclePolicyRequest getLifecyclePolicyRequest) {
    return executeWithRetries(
        String.format("Get LifeCyclePolicy %s", getLifecyclePolicyRequest.getPolicyNames()),
        () ->
            esClient.indexLifecycle().getLifecyclePolicy(getLifecyclePolicyRequest, requestOptions),
        null);
  }

  public Map<String, IndexMapping> getIndexMappings(final String namePattern) {
    return executeWithRetries(
        "Get indices mappings for " + namePattern,
        () -> {
          try {
            final Map<String, IndexMapping> mappingsMap = new HashMap<>();
            final Map<String, MappingMetadata> mappings =
                esClient
                    .indices()
                    .getMapping(
                        new GetMappingsRequest().indices(namePattern), RequestOptions.DEFAULT)
                    .mappings();
            for (final Map.Entry<String, MappingMetadata> entry : mappings.entrySet()) {
              final Map<String, Object> mappingMetadata =
                  objectMapper.readValue(
                      entry.getValue().source().string(),
                      new TypeReference<HashMap<String, Object>>() {});
              final Map<String, Object> properties =
                  (Map<String, Object>) mappingMetadata.getOrDefault("properties", new HashMap<>());
              final Map<String, Object> metaProperties =
                  (Map<String, Object>) mappingMetadata.getOrDefault("_meta", new HashMap<>());
              final String dynamic = (String) mappingMetadata.get("dynamic");
              mappingsMap.put(
                  entry.getKey(),
                  new IndexMapping()
                      .setIndexName(entry.getKey())
                      .setDynamic(dynamic)
                      .setProperties(
                          properties.entrySet().stream()
                              .map(p -> createIndexMappingProperty(p))
                              .collect(Collectors.toSet()))
                      .setMetaProperties(metaProperties));
            }
            return mappingsMap;
          } catch (final ElasticsearchException e) {
            if (e.status().equals(RestStatus.NOT_FOUND)) {
              return Map.of();
            }
            throw e;
          }
        });
  }

  public void putMapping(final PutMappingRequest request) {
    executeWithRetries(
        String.format("Put Mapping %s ", request.indices()),
        () -> esClient.indices().putMapping(request, RequestOptions.DEFAULT),
        null);
  }
}
