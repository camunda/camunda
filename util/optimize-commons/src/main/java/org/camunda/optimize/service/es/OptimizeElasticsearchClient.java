/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es;

import lombok.Getter;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.admin.indices.rollover.Condition;
import org.elasticsearch.action.admin.indices.rollover.MaxAgeCondition;
import org.elasticsearch.action.admin.indices.rollover.MaxDocsCondition;
import org.elasticsearch.action.admin.indices.rollover.MaxSizeCondition;
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
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.client.indices.rollover.RolloverRequest;
import org.elasticsearch.client.indices.rollover.RolloverResponse;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;

import java.io.IOException;
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
public class OptimizeElasticsearchClient {

  @Getter
  private final RestHighLevelClient highLevelClient;
  @Getter
  private final OptimizeIndexNameService indexNameService;

  public OptimizeElasticsearchClient(final RestHighLevelClient highLevelClient,
                                     final OptimizeIndexNameService indexNameService) {
    this.highLevelClient = highLevelClient;
    this.indexNameService = indexNameService;
  }

  public final BulkResponse bulk(final BulkRequest bulkRequest, final RequestOptions options) throws IOException {
    bulkRequest.requests().forEach(this::applyIndexPrefix);

    return highLevelClient.bulk(bulkRequest, options);
  }

  public final void close() throws IOException {
    highLevelClient.close();
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

  public final boolean exists(final GetIndexRequest getRequest, final RequestOptions options) throws IOException {
    return highLevelClient.indices().exists(getRequest, options);
  }

  public final GetResponse get(final GetRequest getRequest, final RequestOptions options) throws IOException {
    getRequest.index(indexNameService.getOptimizeIndexAliasForIndex(getRequest.index()));

    return highLevelClient.get(getRequest, options);
  }

  public final RestClient getLowLevelClient() {
    return getHighLevelClient().getLowLevelClient();
  }

  public final IndexResponse index(final IndexRequest indexRequest, final RequestOptions options) throws IOException {
    applyIndexPrefix(indexRequest);

    return highLevelClient.index(indexRequest, options);
  }

  public final GetMappingsResponse getMapping(final GetMappingsRequest getMappingsRequest,
                                              final RequestOptions options) throws IOException {
    getMappingsRequest.indices(
      Arrays.stream(getMappingsRequest.indices())
        .map(indexNameService::getOptimizeIndexAliasForIndex)
        .toArray(String[]::new)
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
                                               final RequestOptions options)
    throws IOException {
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
    rolloverRequest = applyAliasPrefix(rolloverRequest);
    return highLevelClient.indices().rollover(rolloverRequest, RequestOptions.DEFAULT);
  }

  private void applyIndexPrefix(final DocWriteRequest<?> request) {
    request.index(indexNameService.getOptimizeIndexAliasForIndex(request.index()));
  }

  private void applyIndexPrefixes(final IndicesRequest.Replaceable request) {
    request.indices(
      Arrays.stream(request.indices())
        .map(indexNameService::getOptimizeIndexAliasForIndex)
        .toArray(String[]::new)
    );
  }

  private RolloverRequest applyAliasPrefix(final RolloverRequest request) {
    RolloverRequest requestWithPrefix = new RolloverRequest(
      indexNameService.getOptimizeIndexAliasForIndex(request.getAlias()), null);
    for (Condition condition : request.getConditions().values()) {
      if (condition instanceof MaxAgeCondition) {
        requestWithPrefix.addMaxIndexAgeCondition(((MaxAgeCondition) condition).value());
      } else if (condition instanceof MaxDocsCondition) {
        requestWithPrefix.addMaxIndexDocsCondition(((MaxDocsCondition) condition).value());
      } else if (condition instanceof MaxSizeCondition) {
        requestWithPrefix.addMaxIndexSizeCondition(((MaxSizeCondition) condition).value());
      }
    }
    return requestWithPrefix;
  }

}
