/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.FailsafeExecutor;
import net.jodah.failsafe.RetryPolicy;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.rollover.Condition;
import org.elasticsearch.action.admin.indices.rollover.MaxSizeCondition;
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
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.client.indices.IndexTemplatesExistRequest;
import org.elasticsearch.client.indices.rollover.RolloverRequest;
import org.elasticsearch.client.indices.rollover.RolloverResponse;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.rest.RestStatus;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;

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

  @Getter
  private RestHighLevelClient highLevelClient;
  @Getter
  private OptimizeIndexNameService indexNameService;

  @Setter
  private int snapshotInProgressRetryDelaySeconds = DEFAULT_SNAPSHOT_IN_PROGRESS_RETRY_DELAY;

  public OptimizeElasticsearchClient(final RestHighLevelClient highLevelClient,
                                     final OptimizeIndexNameService indexNameService) {
    this.highLevelClient = highLevelClient;
    this.indexNameService = indexNameService;
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    try {
      highLevelClient.close();
      this.highLevelClient = ElasticsearchHighLevelRestClientBuilder.build(context.getBean(ConfigurationService.class));
      this.indexNameService = context.getBean(OptimizeIndexNameService.class);
    } catch (IOException e) {
      log.error("There was an error closing Elasticsearch Client {}", highLevelClient);
    }
  }

  public final void close() throws IOException {
    highLevelClient.close();
  }

  public final RestClient getLowLevelClient() {
    return getHighLevelClient().getLowLevelClient();
  }

  public final BulkResponse bulk(final BulkRequest bulkRequest, final RequestOptions options) throws IOException {
    bulkRequest.requests().forEach(this::applyIndexPrefix);

    return highLevelClient.bulk(bulkRequest, options);
  }

  public final CountResponse count(final CountRequest countRequest, final RequestOptions options) throws IOException {
    applyIndexPrefixes(countRequest);
    return highLevelClient.count(countRequest, options);
  }

  public final DeleteResponse delete(final DeleteRequest deleteRequest, final RequestOptions options) throws
                                                                                                      IOException {
    applyIndexPrefix(deleteRequest);

    return highLevelClient.delete(deleteRequest, options);
  }

  public final BulkByScrollResponse deleteByQuery(final DeleteByQueryRequest deleteByQueryRequest,
                                                  final RequestOptions options)
    throws IOException {
    applyIndexPrefixes(deleteByQueryRequest);

    return highLevelClient.deleteByQuery(deleteByQueryRequest, options);
  }

  public final GetAliasesResponse getAlias(final GetAliasesRequest getAliasesRequest, final RequestOptions options)
    throws IOException {
    getAliasesRequest.indices(convertToPrefixedAliasNames(getAliasesRequest.indices()));
    getAliasesRequest.aliases(convertToPrefixedAliasNames(getAliasesRequest.aliases()));
    return highLevelClient.indices().getAlias(getAliasesRequest, options);
  }

  public final boolean exists(final String indexName) throws IOException {
    return exists(new GetIndexRequest(convertToPrefixedAliasNames(new String[]{indexName})), RequestOptions.DEFAULT);
  }

  public final boolean exists(final GetIndexRequest getRequest, final RequestOptions options) throws IOException {
    final GetIndexRequest prefixedGetRequest = new GetIndexRequest(convertToPrefixedAliasNames(getRequest.indices()));
    return highLevelClient.indices().exists(prefixedGetRequest, options);
  }

  public final boolean exists(final IndexMappingCreator indexMappingCreator) throws IOException {
    return highLevelClient.indices().exists(
      new GetIndexRequest(indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(indexMappingCreator)),
      RequestOptions.DEFAULT
    );
  }

  public final boolean templateExists(final String indexName) throws IOException {
    return highLevelClient.indices().existsTemplate(
      new IndexTemplatesExistRequest(convertToPrefixedAliasNames(new String[]{indexName})),
      RequestOptions.DEFAULT
    );
  }

  public final GetResponse get(final GetRequest getRequest, final RequestOptions options) throws IOException {
    getRequest.index(indexNameService.getOptimizeIndexAliasForIndex(getRequest.index()));

    return highLevelClient.get(getRequest, options);
  }

  public final IndexResponse index(final IndexRequest indexRequest, final RequestOptions options) throws IOException {
    applyIndexPrefix(indexRequest);

    return highLevelClient.index(indexRequest, options);
  }

  public final GetMappingsResponse getMapping(final GetMappingsRequest getMappingsRequest,
                                              final RequestOptions options) throws IOException {
    getMappingsRequest.indices(
      convertToPrefixedAliasNames(getMappingsRequest.indices())
    );
    return highLevelClient.indices().getMapping(getMappingsRequest, options);
  }

  public final MultiGetResponse mget(final MultiGetRequest multiGetRequest, final RequestOptions options)
    throws IOException {
    multiGetRequest.getItems()
      .forEach(item -> item.index(indexNameService.getOptimizeIndexAliasForIndex(item.index())));

    return highLevelClient.mget(multiGetRequest, options);
  }

  public final SearchResponse scroll(final SearchScrollRequest searchScrollRequest, final RequestOptions options)
    throws IOException {
    // nothing to modify here, still exposing to not force usage of highLevelClient for this common use case
    return highLevelClient.scroll(searchScrollRequest, options);
  }

  public final ClearScrollResponse clearScroll(final ClearScrollRequest clearScrollRequest,
                                               final RequestOptions options) throws IOException {
    // nothing to modify here, still exposing to not force usage of highLevelClient for this common use case
    return highLevelClient.clearScroll(clearScrollRequest, options);
  }

  public final SearchResponse search(final SearchRequest searchRequest, final RequestOptions options)
    throws IOException {
    applyIndexPrefixes(searchRequest);
    return highLevelClient.search(searchRequest, options);
  }

  public final UpdateResponse update(final UpdateRequest updateRequest, final RequestOptions options)
    throws IOException {
    applyIndexPrefix(updateRequest);

    return highLevelClient.update(updateRequest, options);
  }

  public final BulkByScrollResponse updateByQuery(final UpdateByQueryRequest updateByQueryRequest,
                                                  final RequestOptions options) throws IOException {
    applyIndexPrefixes(updateByQueryRequest);
    return highLevelClient.updateByQuery(updateByQueryRequest, options);
  }

  public final RolloverResponse rollover(RolloverRequest rolloverRequest) throws IOException {
    rolloverRequest = applyAliasPrefixAndRolloverConditions(rolloverRequest);
    return highLevelClient.indices().rollover(rolloverRequest, RequestOptions.DEFAULT);
  }

  public void deleteIndex(final IndexMappingCreator indexMappingCreator) {
    String indexName = indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(indexMappingCreator);
    deleteIndexByRawIndexNames(indexName);
  }

  public void refresh(final RefreshRequest refreshRequest) {
    applyIndexPrefixes(refreshRequest);
    try {
      getHighLevelClient().indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Could not refresh Optimize indexes!", e);
      throw new OptimizeRuntimeException("Could not refresh Optimize indexes!", e);
    }
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
        .get(() -> highLevelClient.indices().delete(new DeleteIndexRequest(indexNames), RequestOptions.DEFAULT));
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
        new DeleteIndexTemplateRequest(prefixedIndexTemplateName), RequestOptions.DEFAULT
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

  public final SearchResponse searchWithoutPrefixing(final SearchRequest searchRequest, final RequestOptions options)
    throws IOException {
    return highLevelClient.search(searchRequest, options);
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
