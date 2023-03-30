/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.FailsafeExecutor;
import net.jodah.failsafe.RetryPolicy;
import org.camunda.optimize.plugin.ElasticsearchCustomHeaderProvider;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.RequestOptionsProvider;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
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
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.rest.RestStatus;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * This Client serves as the main elasticsearch client to be used from application code.
 * <p>
 * The exposed methods correspond to the interface of the {@link RestHighLevelClient},
 * any requests passed are expected to contain just the {@link IndexMappingCreator#getIndexName()} value as index name.
 * The client will care about injecting the current index prefix.
 * <p>
 * For low level operations it still exposes the underlying {@link RestHighLevelClient},
 * as well as the {@link OptimizeIndexNameService}.
 */
@Slf4j
public class OptimizeElasticsearchClient implements ConfigurationReloadable {
  private static final int DEFAULT_SNAPSHOT_IN_PROGRESS_RETRY_DELAY = 30;

  // we had to introduce our own options due to a regression with the client's behaviour with the 7.16
  // see https://discuss.elastic.co/t/regression-client-7-16-x-indicesclient-exists-indicesoptions-are-not-present-in-request-anymore/298017
  public static final IndicesOptions INDICES_EXIST_OPTIONS = new IndicesOptions(
    EnumSet.of(IndicesOptions.Option.FORBID_CLOSED_INDICES, IndicesOptions.Option.IGNORE_THROTTLED),
    EnumSet.of(IndicesOptions.WildcardStates.OPEN)
  );

  @Getter
  private RestHighLevelClient highLevelClient;
  @Getter
  private OptimizeIndexNameService indexNameService;

  private RequestOptionsProvider requestOptionsProvider;

  @Setter
  private int snapshotInProgressRetryDelaySeconds = DEFAULT_SNAPSHOT_IN_PROGRESS_RETRY_DELAY;

  public OptimizeElasticsearchClient(final RestHighLevelClient highLevelClient,
                                     final OptimizeIndexNameService indexNameService) {
    this(highLevelClient, indexNameService, new RequestOptionsProvider());
  }

  public OptimizeElasticsearchClient(final RestHighLevelClient highLevelClient,
                                     final OptimizeIndexNameService indexNameService,
                                     final RequestOptionsProvider requestOptionsProvider) {
    this.highLevelClient = highLevelClient;
    this.indexNameService = indexNameService;
    this.requestOptionsProvider = requestOptionsProvider;
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    try {
      highLevelClient.close();
      final ConfigurationService configurationService = context.getBean(ConfigurationService.class);
      this.highLevelClient = ElasticsearchHighLevelRestClientBuilder.build(configurationService);
      this.indexNameService = context.getBean(OptimizeIndexNameService.class);
      final ElasticsearchCustomHeaderProvider customHeaderProvider =
        context.getBean(ElasticsearchCustomHeaderProvider.class);
      this.requestOptionsProvider = new RequestOptionsProvider(customHeaderProvider.getPlugins(), configurationService);
    } catch (IOException e) {
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
    return Arrays.asList(highLevelClient.indices().get(
      new GetIndexRequest(convertToPrefixedAliasNames(
        new GetIndexRequest("*").indices())).indicesOptions(INDICES_EXIST_OPTIONS), requestOptions()
    ).getIndices());
  }

  public final void deleteByQuery(final DeleteByQueryRequest deleteByQueryRequest) throws IOException {
    applyIndexPrefixes(deleteByQueryRequest);

    highLevelClient.deleteByQuery(deleteByQueryRequest, requestOptions());
  }

  public final GetAliasesResponse getAlias(final GetAliasesRequest getAliasesRequest)
    throws IOException {
    getAliasesRequest.indices(convertToPrefixedAliasNames(getAliasesRequest.indices()));
    getAliasesRequest.aliases(convertToPrefixedAliasNames(getAliasesRequest.aliases()));
    return highLevelClient.indices().getAlias(getAliasesRequest, requestOptions());
  }

  public final boolean exists(final String indexName) throws IOException {
    return exists(new GetIndexRequest(convertToPrefixedAliasNames(new String[]{indexName})));
  }

  public final boolean exists(final IndexMappingCreator indexMappingCreator) throws IOException {
    return exists(
      new GetIndexRequest(indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(indexMappingCreator))
    );
  }

  public final boolean exists(final GetIndexRequest getRequest) throws IOException {
    return highLevelClient.indices()
      .exists(
        new GetIndexRequest(convertToPrefixedAliasNames(getRequest.indices())).indicesOptions(INDICES_EXIST_OPTIONS),
        requestOptions()
      );
  }

  public final boolean templateExists(final String indexName) throws IOException {
    return highLevelClient.indices().existsTemplate(
      new IndexTemplatesExistRequest(convertToPrefixedAliasNames(new String[]{indexName})),
      requestOptions()
    );
  }

  public final GetResponse get(final GetRequest getRequest) throws IOException {
    getRequest.index(indexNameService.getOptimizeIndexAliasForIndex(getRequest.index()));

    return highLevelClient.get(getRequest, requestOptions());
  }

  public final IndexResponse index(final IndexRequest indexRequest) throws IOException {
    applyIndexPrefix(indexRequest);

    return highLevelClient.index(indexRequest, requestOptions());
  }

  public final GetMappingsResponse getMapping(final GetMappingsRequest getMappingsRequest) throws IOException {
    getMappingsRequest.indices(
      convertToPrefixedAliasNames(getMappingsRequest.indices())
    );
    return highLevelClient.indices().getMapping(getMappingsRequest, requestOptions());
  }

  public final MultiGetResponse mget(final MultiGetRequest multiGetRequest)
    throws IOException {
    multiGetRequest.getItems()
      .forEach(item -> item.index(indexNameService.getOptimizeIndexAliasForIndex(item.index())));

    return highLevelClient.mget(multiGetRequest, requestOptions());
  }

  public final SearchResponse scroll(final SearchScrollRequest searchScrollRequest)
    throws IOException {
    // nothing to modify here, still exposing to not force usage of highLevelClient for this common use case
    return highLevelClient.scroll(searchScrollRequest, requestOptions());
  }

  public final ClearScrollResponse clearScroll(final ClearScrollRequest clearScrollRequest) throws IOException {
    // nothing to modify here, still exposing to not force usage of highLevelClient for this common use case
    return highLevelClient.clearScroll(clearScrollRequest, requestOptions());
  }

  public final SearchResponse search(final SearchRequest searchRequest)
    throws IOException {
    applyIndexPrefixes(searchRequest);
    return highLevelClient.search(searchRequest, requestOptions());
  }

  public final UpdateResponse update(final UpdateRequest updateRequest)
    throws IOException {
    applyIndexPrefix(updateRequest);

    return highLevelClient.update(updateRequest, requestOptions());
  }

  public final BulkByScrollResponse updateByQuery(final UpdateByQueryRequest updateByQueryRequest) throws IOException {
    applyIndexPrefixes(updateByQueryRequest);
    return highLevelClient.updateByQuery(updateByQueryRequest, requestOptions());
  }

  public final RolloverResponse rollover(RolloverRequest rolloverRequest) throws IOException {
    rolloverRequest = applyAliasPrefixAndRolloverConditions(rolloverRequest);
    return highLevelClient.indices().rollover(rolloverRequest, requestOptions());
  }

  public void deleteIndex(final IndexMappingCreator indexMappingCreator) {
    final String indexAlias = indexNameService.getOptimizeIndexAliasForIndex(indexMappingCreator);
    final String[] allIndicesForAlias = getAllIndicesForAlias(indexAlias).toArray(String[]::new);
    deleteIndexByRawIndexNames(allIndicesForAlias);
  }

  public Set<String> getAllIndicesForAlias(final String aliasName) {
    GetAliasesRequest aliasesRequest = new GetAliasesRequest().aliases(aliasName);
    try {
      return highLevelClient
        .indices()
        .getAlias(aliasesRequest, requestOptions())
        .getAliases()
        .keySet();
    } catch (Exception e) {
      String message = String.format("Could not retrieve index names for alias {%s}.", aliasName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public void refresh(final RefreshRequest refreshRequest) {
    applyIndexPrefixes(refreshRequest);
    try {
      highLevelClient.indices().refresh(refreshRequest, requestOptions());
    } catch (IOException e) {
      log.error("Could not refresh Optimize indexes!", e);
      throw new OptimizeRuntimeException("Could not refresh Optimize indexes!", e);
    }
  }

  public String getElasticsearchVersion() throws IOException {
    return highLevelClient.info(requestOptions()).getVersion().getNumber();
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
    return highLevelClient.indices().getSettings(
      new GetSettingsRequest().indices(getIndexNameService().getIndexPrefix() + "*"),
      requestOptions()
    );
  }

  public ClusterHealthResponse getClusterHealth(final ClusterHealthRequest request) throws IOException {
    return highLevelClient.cluster().health(request, requestOptions());
  }

  public TaskSubmissionResponse submitReindexTask(final ReindexRequest request) throws IOException {
    return highLevelClient.submitReindexTask(request, requestOptions());
  }

  public TaskSubmissionResponse submitUpdateTask(final UpdateByQueryRequest request) throws IOException {
    return highLevelClient.submitUpdateByQueryTask(request, requestOptions());
  }

  public TaskSubmissionResponse submitDeleteTask(final DeleteByQueryRequest request) throws IOException {
    return highLevelClient.submitDeleteByQueryTask(request, requestOptions());
  }

  public ListTasksResponse getTaskList(final ListTasksRequest request) throws IOException {
    return highLevelClient.tasks().list(request, requestOptions());
  }

  public void verifyRepositoryExists(final GetRepositoriesRequest getRepositoriesRequest) throws
                                                                                          IOException,
                                                                                          ElasticsearchStatusException {
    highLevelClient.snapshot().getRepository(getRepositoriesRequest, requestOptions());
  }

  public GetSnapshotsResponse getSnapshots(final GetSnapshotsRequest getSnapshotsRequest) throws
                                                                                          IOException,
                                                                                          ElasticsearchStatusException {
    return highLevelClient.snapshot().get(getSnapshotsRequest, requestOptions());
  }

  public void triggerSnapshotAsync(final CreateSnapshotRequest createSnapshotRequest,
                                   final ActionListener<CreateSnapshotResponse> listener) {
    highLevelClient.snapshot().createAsync(createSnapshotRequest, requestOptions(), listener);
  }

  public void deleteSnapshotAsync(final DeleteSnapshotRequest deleteSnapshotRequest,
                                  final ActionListener<AcknowledgedResponse> listener) {
    highLevelClient.snapshot().deleteAsync(deleteSnapshotRequest, requestOptions(), listener);
  }

  public long countWithoutPrefix(final CountRequest request) throws IOException {
    return highLevelClient.count(request, requestOptions()).getCount();
  }

  public Response performRequest(final Request request) throws IOException {
    request.setOptions(requestOptions());
    return getLowLevelClient().performRequest(request);
  }

  /**
   * Deletes an index and retries in recoverable situations (e.g. snapshot in progress).
   * This expects the plain index name to be provided, no automatic prefixing or other modifications will be applied.
   *
   * @param indexNames plain index names to delete
   */
  public void deleteIndexByRawIndexNames(final String... indexNames) {
    final String indexNamesString = Arrays.toString(indexNames);
    log.debug("Deleting indices [{}].", indexNamesString);
    try {
      esClientSnapshotFailsafe("DeleteIndex: " + indexNamesString)
        .get(() -> highLevelClient.indices().delete(new DeleteIndexRequest(indexNames), requestOptions()));
    } catch (FailsafeException failsafeException) {
      final Throwable cause = failsafeException.getCause();
      if (cause instanceof ElasticsearchStatusException) {
        throw (ElasticsearchStatusException) cause;
      } else {
        String errorMessage = String.format("Could not delete index [%s]!", indexNamesString);
        throw new OptimizeRuntimeException(errorMessage, cause);
      }
    }
    log.debug("Successfully deleted index [{}].", indexNamesString);
  }

  public void deleteIndexTemplateByIndexTemplateName(final String indexTemplateName) {
    final String prefixedIndexTemplateName = indexNameService.getOptimizeIndexAliasForIndex(indexTemplateName);
    log.debug("Deleting index template [{}].", prefixedIndexTemplateName);
    try {
      highLevelClient.indices().deleteTemplate(
        new DeleteIndexTemplateRequest(prefixedIndexTemplateName), requestOptions()
      );
    } catch (IOException e) {
      final String errorMessage = String.format("Could not delete index template [%s]!", prefixedIndexTemplateName);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    log.debug("Successfully deleted index template [{}].", prefixedIndexTemplateName);
  }

  public void applyIndexPrefixes(final IndicesRequest.Replaceable request) {
    final String[] indices = request.indices();
    request.indices(
      convertToPrefixedAliasNames(indices)
    );
  }

  public final SearchResponse searchWithoutPrefixing(final SearchRequest searchRequest)
    throws IOException {
    return highLevelClient.search(searchRequest, requestOptions());
  }

  private FailsafeExecutor<Object> esClientSnapshotFailsafe(final String operation) {
    return Failsafe.with(createSnapshotRetryPolicy(operation, this.snapshotInProgressRetryDelaySeconds));
  }

  private RetryPolicy<Object> createSnapshotRetryPolicy(final String operation, final int delay) {
    return new RetryPolicy<>()
      .handleIf(failure -> {
        if (failure instanceof ElasticsearchStatusException) {
          final ElasticsearchStatusException statusException = (ElasticsearchStatusException) failure;
          return statusException.status() == RestStatus.BAD_REQUEST
            && statusException.getMessage().contains("snapshot_in_progress_exception");
        } else {
          return false;
        }
      })
      .withDelay(Duration.ofSeconds(delay))
      // no retry limit
      .withMaxRetries(-1)
      .onFailedAttempt(e -> {
        log.warn(
          "Execution of {} failed due to a pending snapshot operation, details: {}",
          operation, e.getLastFailure().getMessage()
        );
        log.info("Will retry the operation in {} seconds...", delay);
      });
  }

  private void applyIndexPrefix(final DocWriteRequest<?> request) {
    request.index(indexNameService.getOptimizeIndexAliasForIndex(request.index()));
  }

  private String[] convertToPrefixedAliasNames(final String[] indices) {
    return Arrays.stream(indices)
      .map(index -> {
        final boolean hasExcludePrefix = '-' == index.charAt(0);
        final String rawIndexName = hasExcludePrefix ? index.substring(1) : index;
        final String prefixedIndexName = indexNameService.getOptimizeIndexAliasForIndex(rawIndexName);
        return hasExcludePrefix ? "-" + prefixedIndexName : prefixedIndexName;
      })
      .toArray(String[]::new);
  }

  private RolloverRequest applyAliasPrefixAndRolloverConditions(final RolloverRequest request) {
    RolloverRequest requestWithPrefix = new RolloverRequest(
      indexNameService.getOptimizeIndexAliasForIndex(request.getAlias()), null);
    for (Condition<?> condition : request.getConditions().values()) {
      if (condition instanceof MaxSizeCondition) {
        requestWithPrefix.addMaxIndexSizeCondition(((MaxSizeCondition) condition).value());
      } else {
        log.warn("Rollover condition not supported: {}", condition.name());
      }
    }
    return requestWithPrefix;
  }

}
