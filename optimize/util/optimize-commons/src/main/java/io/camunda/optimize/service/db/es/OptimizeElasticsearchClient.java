/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DATA_SOURCE;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static java.util.stream.Collectors.groupingBy;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.DataImportSourceType;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.schema.RequestOptionsProvider;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.FailsafeExecutor;
import net.jodah.failsafe.RetryPolicy;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksRequest;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksResponse;
import org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesRequest;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotRequest;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotResponse;
import org.elasticsearch.action.admin.cluster.snapshots.delete.DeleteSnapshotRequest;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsRequest;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.rollover.Condition;
import org.elasticsearch.action.admin.indices.rollover.MaxSizeCondition;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.client.indices.IndexTemplatesExistRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.client.indices.rollover.RolloverRequest;
import org.elasticsearch.client.indices.rollover.RolloverResponse;
import org.elasticsearch.client.tasks.TaskSubmissionResponse;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.ApplicationContext;

/**
 * This Client serves as the main elasticsearch client to be used from application code.
 *
 * <p>The exposed methods correspond to the interface of the {@link RestHighLevelClient}, any
 * requests passed are expected to contain just the {@link IndexMappingCreator#getIndexName()} value
 * as index name. The client will care about injecting the current index prefix.
 *
 * <p>For low level operations it still exposes the underlying {@link RestHighLevelClient}, as well
 * as the {@link OptimizeIndexNameService}.
 */
@Slf4j
public class OptimizeElasticsearchClient extends DatabaseClient {

  // we had to introduce our own options due to a regression with the client's behaviour with the
  // 7.16
  // see
  // https://discuss.elastic.co/t/regression-client-7-16-x-indicesclient-exists-indicesoptions-are-not-present-in-request-anymore/298017
  public static final IndicesOptions INDICES_EXIST_OPTIONS =
      new IndicesOptions(
          EnumSet.of(
              IndicesOptions.Option.FORBID_CLOSED_INDICES, IndicesOptions.Option.IGNORE_THROTTLED),
          EnumSet.of(IndicesOptions.WildcardStates.OPEN));
  private static final int DEFAULT_SNAPSHOT_IN_PROGRESS_RETRY_DELAY = 30;
  private final ObjectMapper objectMapper;
  @Getter private RestHighLevelClient highLevelClient;
  private RequestOptionsProvider requestOptionsProvider;

  @Setter
  private int snapshotInProgressRetryDelaySeconds = DEFAULT_SNAPSHOT_IN_PROGRESS_RETRY_DELAY;

  public OptimizeElasticsearchClient(
      final RestHighLevelClient highLevelClient,
      final OptimizeIndexNameService indexNameService,
      final ObjectMapper objectMapper) {
    this(highLevelClient, indexNameService, new RequestOptionsProvider(), objectMapper);
  }

  public OptimizeElasticsearchClient(
      final RestHighLevelClient highLevelClient,
      final OptimizeIndexNameService indexNameService,
      final RequestOptionsProvider requestOptionsProvider,
      final ObjectMapper objectMapper) {
    this.highLevelClient = highLevelClient;
    this.indexNameService = indexNameService;
    this.requestOptionsProvider = requestOptionsProvider;
    this.objectMapper = objectMapper;
  }

  // to avoid cross-dependency, we copied that method from the ElasticsearchWriterUtil
  private static Script createDefaultScriptWithPrimitiveParams(
      final String inlineUpdateScript, final Map<String, Object> params) {
    return new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, inlineUpdateScript, params);
  }

  private static boolean containsNestedDocumentLimitErrorMessage(final BulkResponse bulkResponse) {
    return bulkResponse.buildFailureMessage().contains(NESTED_DOC_LIMIT_MESSAGE);
  }

  private static String getHintForErrorMsg(final BulkResponse bulkResponse) {
    if (containsNestedDocumentLimitErrorMessage(bulkResponse)) {
      // exception potentially related to nested object limit
      return "If you are experiencing failures due to too many nested documents, try carefully increasing the "
          + "configured nested object limit (es.settings.index.nested_documents_limit) or enabling the skipping of "
          + "documents that have reached this limit during import (import.skipDataAfterNestedDocLimitReached). "
          + "See Optimize documentation for details.";
    }
    return "";
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    try {
      highLevelClient.close();
      final ConfigurationService configurationService = context.getBean(ConfigurationService.class);
      highLevelClient = ElasticsearchHighLevelRestClientBuilder.build(configurationService);
      indexNameService = context.getBean(OptimizeIndexNameService.class);
      requestOptionsProvider = new RequestOptionsProvider(configurationService);
    } catch (final IOException e) {
      log.error("There was an error closing Elasticsearch Client {}", highLevelClient);
    }
  }

  public RequestOptions requestOptions() {
    return requestOptionsProvider.getRequestOptions();
  }

  public final void close() throws IOException {
    highLevelClient.close();
  }

  public final RestClient getLowLevelClient() {
    return highLevelClient.getLowLevelClient();
  }

  public final BulkResponse bulk(final BulkRequest bulkRequest) throws IOException {
    bulkRequest.requests().forEach(this::applyIndexPrefix);

    return highLevelClient.bulk(bulkRequest, requestOptions());
  }

  public final CountResponse count(final CountRequest countRequest) throws IOException {
    applyIndexPrefixes(countRequest);
    return highLevelClient.count(countRequest, requestOptions());
  }

  public final DeleteResponse delete(final DeleteRequest deleteRequest) throws IOException {
    applyIndexPrefix(deleteRequest);

    return highLevelClient.delete(deleteRequest, requestOptions());
  }

  public final List<String> getAllIndexNames() throws IOException {
    return Arrays.asList(
        highLevelClient
            .indices()
            .get(
                new GetIndexRequest(convertToPrefixedAliasNames(new GetIndexRequest("*").indices()))
                    .indicesOptions(INDICES_EXIST_OPTIONS),
                requestOptions())
            .getIndices());
  }

  public final void deleteByQuery(final DeleteByQueryRequest deleteByQueryRequest)
      throws IOException {
    applyIndexPrefixes(deleteByQueryRequest);

    highLevelClient.deleteByQuery(deleteByQueryRequest, requestOptions());
  }

  public final boolean exists(final String indexName) throws IOException {
    return exists(new GetIndexRequest(convertToPrefixedAliasNames(new String[] {indexName})));
  }

  public final boolean exists(final IndexMappingCreator indexMappingCreator) throws IOException {
    return exists(
        new GetIndexRequest(
            indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(indexMappingCreator)));
  }

  public final boolean exists(final GetIndexRequest getRequest) throws IOException {
    return highLevelClient
        .indices()
        .exists(
            new GetIndexRequest(convertToPrefixedAliasNames(getRequest.indices()))
                .indicesOptions(INDICES_EXIST_OPTIONS),
            requestOptions());
  }

  public final boolean templateExists(final String indexName) throws IOException {
    return highLevelClient
        .indices()
        .existsTemplate(
            new IndexTemplatesExistRequest(convertToPrefixedAliasNames(new String[] {indexName})),
            requestOptions());
  }

  public final GetResponse get(final GetRequest getRequest) throws IOException {
    getRequest.index(indexNameService.getOptimizeIndexAliasForIndex(getRequest.index()));

    return highLevelClient.get(getRequest, requestOptions());
  }

  public final IndexResponse index(final IndexRequest indexRequest) throws IOException {
    applyIndexPrefix(indexRequest);

    return highLevelClient.index(indexRequest, requestOptions());
  }

  public final GetMappingsResponse getMapping(final GetMappingsRequest getMappingsRequest)
      throws IOException {
    getMappingsRequest.indices(convertToPrefixedAliasNames(getMappingsRequest.indices()));
    return highLevelClient.indices().getMapping(getMappingsRequest, requestOptions());
  }

  public final MultiGetResponse mget(final MultiGetRequest multiGetRequest) throws IOException {
    multiGetRequest
        .getItems()
        .forEach(item -> item.index(indexNameService.getOptimizeIndexAliasForIndex(item.index())));

    return highLevelClient.mget(multiGetRequest, requestOptions());
  }

  public final UpdateResponse update(final UpdateRequest updateRequest) throws IOException {
    applyIndexPrefix(updateRequest);

    return highLevelClient.update(updateRequest, requestOptions());
  }

  public final BulkByScrollResponse updateByQuery(final UpdateByQueryRequest updateByQueryRequest)
      throws IOException {
    applyIndexPrefixes(updateByQueryRequest);
    return highLevelClient.updateByQuery(updateByQueryRequest, requestOptions());
  }

  public final RolloverResponse rollover(RolloverRequest rolloverRequest) throws IOException {
    rolloverRequest = applyAliasPrefixAndRolloverConditions(rolloverRequest);
    return highLevelClient.indices().rollover(rolloverRequest, requestOptions());
  }

  public void deleteIndex(final IndexMappingCreator indexMappingCreator) {
    final String indexAlias = indexNameService.getOptimizeIndexAliasForIndex(indexMappingCreator);
    deleteIndex(indexAlias);
  }

  public void refresh(final RefreshRequest refreshRequest) {
    applyIndexPrefixes(refreshRequest);
    try {
      highLevelClient.indices().refresh(refreshRequest, requestOptions());
    } catch (final IOException e) {
      log.error("Could not refresh Optimize indexes!", e);
      throw new OptimizeRuntimeException("Could not refresh Optimize indexes!", e);
    }
  }

  public <T> void doImportBulkRequestWithList(
      final String importItemName,
      final Collection<T> entityCollection,
      final BiConsumer<BulkRequest, T> addDtoToRequestConsumer,
      final boolean retryRequestIfNestedDocLimitReached) {
    if (entityCollection.isEmpty()) {
      log.warn("Cannot perform bulk request with empty collection of {}.", importItemName);
    } else {
      final BulkRequest bulkRequest = new BulkRequest();
      entityCollection.forEach(dto -> addDtoToRequestConsumer.accept(bulkRequest, dto));
      doBulkRequest(bulkRequest, importItemName, retryRequestIfNestedDocLimitReached);
    }
  }

  private void applyOperationToBulkRequest(
      final BulkRequest bulkRequest, final ImportRequestDto requestDto) {

    validateOperationParams(requestDto);

    switch (requestDto.getType()) {
      case INDEX ->
          bulkRequest.add(
              new IndexRequest()
                  .id(requestDto.getId())
                  .source(serializeToJson(requestDto), XContentType.JSON)
                  .index(requestDto.getIndexName()));
      case UPDATE ->
          bulkRequest.add(
              new UpdateRequest()
                  .id(requestDto.getId())
                  .index(requestDto.getIndexName())
                  .upsert(serializeToJson(requestDto), XContentType.JSON)
                  .script(
                      createDefaultScriptWithPrimitiveParams(
                          requestDto.getScriptData().scriptString(),
                          requestDto.getScriptData().params()))
                  .retryOnConflict(requestDto.getRetryNumberOnConflict()));
    }
  }

  private String serializeToJson(final ImportRequestDto requestDto) {
    try {
      return objectMapper.writeValueAsString(requestDto.getSource());
    } catch (final Exception e) {
      final String error =
          String.format(
              "Failed to serialize source of type %s for ImportRequestDto(name=%s, id=%s, source=%s)!",
              requestDto.getSource() != null ? requestDto.getSource().getClass() : "null",
              requestDto.getImportName(),
              requestDto.getId(),
              requestDto.getSource());
      log.error(error, e);
      throw new OptimizeRuntimeException(error, e);
    }
  }

  public void doBulkRequest(
      final BulkRequest bulkRequest,
      final String itemName,
      final boolean retryRequestIfNestedDocLimitReached) {
    if (retryRequestIfNestedDocLimitReached) {
      doBulkRequestWithNestedDocHandling(bulkRequest, itemName);
    } else {
      doBulkRequestWithoutRetries(bulkRequest, itemName);
    }
  }

  private void doBulkRequestWithoutRetries(final BulkRequest bulkRequest, final String itemName) {
    if (bulkRequest.numberOfActions() > 0) {
      try {
        final BulkResponse bulkResponse = bulk(bulkRequest);
        if (bulkResponse.hasFailures()) {
          throw new OptimizeRuntimeException(
              String.format(
                  "There were failures while performing bulk on %s.%n%s Message: %s",
                  itemName, getHintForErrorMsg(bulkResponse), bulkResponse.buildFailureMessage()));
        }
      } catch (final IOException e) {
        final String reason =
            String.format("There were errors while performing a bulk on %s.", itemName);
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      log.debug("Bulkrequest on {} not executed because it contains no actions.", itemName);
    }
  }

  private void doBulkRequestWithNestedDocHandling(
      final BulkRequest bulkRequest, final String itemName) {
    if (bulkRequest.numberOfActions() > 0) {
      log.info("Executing bulk request on {} items of {}", bulkRequest.requests().size(), itemName);
      try {
        final BulkResponse bulkResponse = bulk(bulkRequest);
        if (bulkResponse.hasFailures()) {
          if (containsNestedDocumentLimitErrorMessage(bulkResponse)) {
            final Set<String> failedItemIds =
                Arrays.stream(bulkResponse.getItems())
                    .filter(BulkItemResponse::isFailed)
                    .filter(
                        responseItem ->
                            responseItem.getFailureMessage().contains(NESTED_DOC_LIMIT_MESSAGE))
                    .map(BulkItemResponse::getId)
                    .collect(Collectors.toSet());
            log.warn(
                "There were failures while performing bulk on {} due to the nested document limit being reached."
                    + " Removing {} failed items and retrying",
                itemName,
                failedItemIds.size());
            bulkRequest.requests().removeIf(request -> failedItemIds.contains(request.id()));
            if (!bulkRequest.requests().isEmpty()) {
              doBulkRequestWithNestedDocHandling(bulkRequest, itemName);
            }
          } else {
            throw new OptimizeRuntimeException(
                String.format(
                    "There were failures while performing bulk on %s. Message: %s",
                    itemName, bulkResponse.buildFailureMessage()));
          }
        }
      } catch (final IOException e) {
        final String reason =
            String.format("There were errors while performing a bulk on %s.", itemName);
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      log.debug("Bulkrequest on {} not executed because it contains no actions.", itemName);
    }
  }

  public void createIndex(final CreateIndexRequest request) throws IOException {
    highLevelClient.indices().create(request, requestOptions());
  }

  public void createMapping(final PutMappingRequest request) throws IOException {
    highLevelClient.indices().putMapping(request, requestOptions());
  }

  public void updateSettings(final UpdateSettingsRequest request) throws IOException {
    highLevelClient.indices().putSettings(request, requestOptions());
  }

  public void createTemplate(final PutIndexTemplateRequest request) throws IOException {
    highLevelClient.indices().putTemplate(request, requestOptions());
  }

  public GetSettingsResponse getIndexSettings() throws IOException {
    return highLevelClient
        .indices()
        .getSettings(
            new GetSettingsRequest().indices(getIndexNameService().getIndexPrefix() + "*"),
            requestOptions());
  }

  public ClusterHealthResponse getClusterHealth(final ClusterHealthRequest request)
      throws IOException {
    return highLevelClient.cluster().health(request, requestOptions());
  }

  public TaskSubmissionResponse submitReindexTask(final ReindexRequest request) throws IOException {
    return highLevelClient.submitReindexTask(request, requestOptions());
  }

  public TaskSubmissionResponse submitUpdateTask(final UpdateByQueryRequest request)
      throws IOException {
    return highLevelClient.submitUpdateByQueryTask(request, requestOptions());
  }

  public TaskSubmissionResponse submitDeleteTask(final DeleteByQueryRequest request)
      throws IOException {
    return highLevelClient.submitDeleteByQueryTask(request, requestOptions());
  }

  public ListTasksResponse getTaskList(final ListTasksRequest request) throws IOException {
    return highLevelClient.tasks().list(request, requestOptions());
  }

  public void verifyRepositoryExists(final GetRepositoriesRequest getRepositoriesRequest)
      throws IOException, ElasticsearchStatusException {
    highLevelClient.snapshot().getRepository(getRepositoriesRequest, requestOptions());
  }

  public GetSnapshotsResponse getSnapshots(final GetSnapshotsRequest getSnapshotsRequest)
      throws IOException, ElasticsearchStatusException {
    return highLevelClient.snapshot().get(getSnapshotsRequest, requestOptions());
  }

  public void triggerSnapshotAsync(
      final CreateSnapshotRequest createSnapshotRequest,
      final ActionListener<CreateSnapshotResponse> listener) {
    highLevelClient.snapshot().createAsync(createSnapshotRequest, requestOptions(), listener);
  }

  public void deleteSnapshotAsync(
      final DeleteSnapshotRequest deleteSnapshotRequest,
      final ActionListener<AcknowledgedResponse> listener) {
    highLevelClient.snapshot().deleteAsync(deleteSnapshotRequest, requestOptions(), listener);
  }

  public long countWithoutPrefix(final CountRequest request)
      throws IOException, InterruptedException {
    final int maxNumberOfRetries = 10;
    final int waitIntervalMillis = 3000;
    int retryAttempts = 0;
    while (retryAttempts < maxNumberOfRetries) {
      final CountResponse countResponse = highLevelClient.count(request, requestOptions());
      if (countResponse.getFailedShards() > 0) {
        log.info(
            "Not all shards returned successful for count response from indices: {}",
            Arrays.asList(request.indices()));
        retryAttempts++;
        Thread.sleep(waitIntervalMillis);
      } else {
        return countResponse.getCount();
      }
    }
    throw new OptimizeRuntimeException(
        String.format(
            "Could not determine count from indices: %s", Arrays.asList(request.indices())));
  }

  public Response performRequest(final Request request) throws IOException {
    request.setOptions(requestOptions());
    return getLowLevelClient().performRequest(request);
  }

  /**
   * Deletes an index and retries in recoverable situations (e.g. snapshot in progress). This
   * expects the plain index name to be provided, no automatic prefixing or other modifications will
   * be applied.
   *
   * @param indexNames plain index names to delete
   */
  public void deleteIndexByRawIndexNames(final String... indexNames) {
    final String indexNamesString = Arrays.toString(indexNames);
    log.debug("Deleting indices [{}].", indexNamesString);
    try {
      esClientSnapshotFailsafe("DeleteIndex: " + indexNamesString)
          .get(
              () ->
                  highLevelClient
                      .indices()
                      .delete(new DeleteIndexRequest(indexNames), requestOptions()));
    } catch (final FailsafeException failsafeException) {
      final Throwable cause = failsafeException.getCause();
      if (cause instanceof final ElasticsearchStatusException elasticsearchStatusException) {
        throw elasticsearchStatusException;
      } else {
        final String errorMessage = String.format("Could not delete index [%s]!", indexNamesString);
        throw new OptimizeRuntimeException(errorMessage, cause);
      }
    }
    log.debug("Successfully deleted index [{}].", indexNamesString);
  }

  public void deleteIndexTemplateByIndexTemplateName(final String indexTemplateName) {
    final String prefixedIndexTemplateName =
        indexNameService.getOptimizeIndexAliasForIndex(indexTemplateName);
    log.debug("Deleting index template [{}].", prefixedIndexTemplateName);
    try {
      highLevelClient
          .indices()
          .deleteTemplate(
              new DeleteIndexTemplateRequest(prefixedIndexTemplateName), requestOptions());
    } catch (final IOException e) {
      final String errorMessage =
          String.format("Could not delete index template [%s]!", prefixedIndexTemplateName);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    log.debug("Successfully deleted index template [{}].", prefixedIndexTemplateName);
  }

  public void applyIndexPrefixes(final IndicesRequest.Replaceable request) {
    final String[] indices = request.indices();
    request.indices(convertToPrefixedAliasNames(indices));
  }

  public final SearchResponse searchWithoutPrefixing(final SearchRequest searchRequest)
      throws IOException {
    return highLevelClient.search(searchRequest, requestOptions());
  }

  private FailsafeExecutor<Object> esClientSnapshotFailsafe(final String operation) {
    return Failsafe.with(createSnapshotRetryPolicy(operation, snapshotInProgressRetryDelaySeconds));
  }

  private RetryPolicy<Object> createSnapshotRetryPolicy(final String operation, final int delay) {
    return new RetryPolicy<>()
        .handleIf(
            failure -> {
              if (failure instanceof final ElasticsearchStatusException statusException) {
                return statusException.status() == RestStatus.BAD_REQUEST
                    && statusException.getMessage().contains("snapshot_in_progress_exception");
              } else {
                return false;
              }
            })
        .withDelay(Duration.ofSeconds(delay))
        // no retry limit
        .withMaxRetries(-1)
        .onFailedAttempt(
            e -> {
              log.warn(
                  "Execution of {} failed due to a pending snapshot operation, details: {}",
                  operation,
                  e.getLastFailure().getMessage());
              log.info("Will retry the operation in {} seconds...", delay);
            });
  }

  private void applyIndexPrefix(final DocWriteRequest<?> request) {
    request.index(indexNameService.getOptimizeIndexAliasForIndex(request.index()));
  }

  @Override
  public Map<String, Set<String>> getAliasesForIndexPattern(final String indexNamePattern)
      throws IOException {
    final GetAliasesRequest getAliasesRequest = new GetAliasesRequest();
    getAliasesRequest.indices(indexNamePattern);
    final GetAliasesResponse aliases = getAlias(getAliasesRequest);
    return aliases.getAliases().entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry ->
                    entry.getValue().stream()
                        .map(AliasMetadata::getAlias)
                        .collect(Collectors.toSet())));
  }

  @Override
  public Set<String> getAllIndicesForAlias(final String aliasName) {
    final GetAliasesRequest aliasesRequest = new GetAliasesRequest().aliases(aliasName);
    try {
      return highLevelClient
          .indices()
          .getAlias(aliasesRequest, requestOptions())
          .getAliases()
          .keySet();
    } catch (final Exception e) {
      final String message =
          String.format("Could not retrieve index names for alias {%s}.", aliasName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public boolean triggerRollover(final String indexAliasName, final int maxIndexSizeGB) {
    final RolloverRequest rolloverRequest = new RolloverRequest(indexAliasName, null);
    rolloverRequest.addMaxIndexSizeCondition(new ByteSizeValue(maxIndexSizeGB, ByteSizeUnit.GB));
    log.info("Executing rollover request on {}", indexAliasName);
    try {
      final RolloverResponse rolloverResponse = rollover(rolloverRequest);
      if (rolloverResponse.isRolledOver()) {
        log.info(
            "Index with alias {} has been rolled over. New index name: {}",
            indexAliasName,
            rolloverResponse.getNewIndex());
      } else {
        log.debug("Index with alias {} has not been rolled over.", indexAliasName);
      }
      return rolloverResponse.isRolledOver();
    } catch (final Exception e) {
      final String message = "Failed to execute rollover request";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public void deleteIndex(final String indexAlias) {
    final String[] allIndicesForAlias = getAllIndicesForAlias(indexAlias).toArray(String[]::new);
    deleteIndexByRawIndexNames(allIndicesForAlias);
  }

  @Override
  public <T> long count(final String[] indexNames, final T query) throws IOException {
    final CountRequest countRequest = new CountRequest(indexNames);
    if (query instanceof final QueryBuilder elasticSearchQuery) {
      countRequest.query(elasticSearchQuery);
      return count(countRequest).getCount();
    } else {
      throw new IllegalArgumentException(
          "The count method requires an ElasticSearch object of type QueryBuilder, "
              + "instead got "
              + query.getClass().getSimpleName());
    }
  }

  @Override
  public final SearchResponse scroll(final SearchScrollRequest searchScrollRequest)
      throws IOException {
    // nothing to modify here, still exposing to not force usage of highLevelClient for this common
    // use case
    return highLevelClient.scroll(searchScrollRequest, requestOptions());
  }

  @Override
  public final SearchResponse search(final SearchRequest searchRequest) throws IOException {
    applyIndexPrefixes(searchRequest);
    return highLevelClient.search(searchRequest, requestOptions());
  }

  @Override
  public final ClearScrollResponse clearScroll(final ClearScrollRequest clearScrollRequest)
      throws IOException {
    // nothing to modify here, still exposing to not force usage of highLevelClient for this common
    // use case
    return highLevelClient.clearScroll(clearScrollRequest, requestOptions());
  }

  @Override
  public String getDatabaseVersion() throws IOException {
    return highLevelClient.info(requestOptions()).getVersion().getNumber();
  }

  @Override
  @SneakyThrows
  public void setDefaultRequestOptions() {
    highLevelClient.info(RequestOptions.DEFAULT);
  }

  @Override
  public void update(final String indexName, final String entityId, final ScriptData script) {

    final UpdateRequest updateRequest =
        new UpdateRequest()
            .index(indexName)
            .id(entityId)
            .script(
                new Script(
                    ScriptType.INLINE,
                    Script.DEFAULT_SCRIPT_LANG,
                    script.scriptString(),
                    script.params()))
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    try {
      update(updateRequest);
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "The error occurs while updating OpenSearch entity %s with id %s",
              indexName, entityId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void executeImportRequestsAsBulk(
      final String bulkRequestName,
      final List<ImportRequestDto> importRequestDtos,
      final Boolean retryFailedRequestsOnNestedDocLimit) {
    final BulkRequest bulkRequest = new BulkRequest();
    final Map<String, List<ImportRequestDto>> requestsByType =
        importRequestDtos.stream()
            .filter(this::validateImportName)
            .collect(groupingBy(ImportRequestDto::getImportName));
    requestsByType.forEach(
        (type, requests) -> {
          log.debug("Adding [{}] requests of type {} to bulk request", requests.size(), type);
          requests.forEach(
              importRequest -> applyOperationToBulkRequest(bulkRequest, importRequest));
        });
    doBulkRequest(bulkRequest, bulkRequestName, retryFailedRequestsOnNestedDocLimit);
  }

  @Override
  public Set<String> performSearchDefinitionQuery(
      final String indexName,
      final String definitionXml,
      final String definitionIdField,
      final int maxPageSize,
      final String engineAlias) {
    log.debug("Performing " + indexName + " search query!");
    final Set<String> result = new HashSet<>();
    final QueryBuilder query = buildBasicSearchDefinitionQuery(definitionXml, engineAlias);

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(query)
            .fetchSource(false)
            .sort(SortBuilders.fieldSort(definitionIdField).order(SortOrder.DESC))
            .size(maxPageSize);
    final SearchRequest searchRequest = new SearchRequest(indexName).source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      // refresh to ensure we see the latest state
      refresh(new RefreshRequest(indexName));
      searchResponse = search(searchRequest);
    } catch (final IOException e) {
      log.error("Was not able to search for " + indexName + "!", e);
      throw new OptimizeRuntimeException("Was not able to search for " + indexName + "!", e);
    }

    log.debug(
        indexName + " search query got [{}] results", searchResponse.getHits().getHits().length);

    for (final SearchHit hit : searchResponse.getHits().getHits()) {
      result.add(hit.getId());
    }
    return result;
  }

  @Override
  public DatabaseType getDatabaseVendor() {
    return DatabaseType.ELASTICSEARCH;
  }

  public final GetAliasesResponse getAlias(final GetAliasesRequest getAliasesRequest)
      throws IOException {
    getAliasesRequest.indices(convertToPrefixedAliasNames(getAliasesRequest.indices()));
    getAliasesRequest.aliases(convertToPrefixedAliasNames(getAliasesRequest.aliases()));
    return highLevelClient.indices().getAlias(getAliasesRequest, requestOptions());
  }

  private QueryBuilder buildBasicSearchDefinitionQuery(
      final String definitionXml, final String engineAlias) {
    return QueryBuilders.boolQuery()
        .mustNot(existsQuery(definitionXml))
        .must(termQuery(DEFINITION_DELETED, false))
        .must(termQuery(DATA_SOURCE + "." + DataSourceDto.Fields.type, DataImportSourceType.ENGINE))
        .must(termQuery(DATA_SOURCE + "." + DataSourceDto.Fields.name, engineAlias));
  }

  private RolloverRequest applyAliasPrefixAndRolloverConditions(final RolloverRequest request) {
    final RolloverRequest requestWithPrefix =
        new RolloverRequest(
            indexNameService.getOptimizeIndexAliasForIndex(request.getAlias()), null);
    for (final Condition<?> condition : request.getConditions().values()) {
      if (condition instanceof final MaxSizeCondition maxSizeCondition) {
        requestWithPrefix.addMaxIndexSizeCondition(maxSizeCondition.value());
      } else {
        log.warn("Rollover condition not supported: {}", condition.name());
      }
    }
    return requestWithPrefix;
  }
}
