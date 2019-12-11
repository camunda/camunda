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
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
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
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DEFAULT_INDEX_TYPE;

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
    bulkRequest.requests().forEach(docWriteRequest -> docWriteRequest.type(DEFAULT_INDEX_TYPE));

    return highLevelClient.bulk(bulkRequest, options);
  }

  public final void close() throws IOException {
    highLevelClient.close();
  }

  public final CountResponse count(final CountRequest countRequest, final RequestOptions options) throws IOException {
    applyIndexPrefixes(countRequest);
    return highLevelClient.count(countRequest, options);
  }

  public final DeleteResponse delete(final DeleteRequest deleteRequest, final RequestOptions options) throws IOException {
    applyIndexPrefix(deleteRequest);
    deleteRequest.type(DEFAULT_INDEX_TYPE);

    return highLevelClient.delete(deleteRequest, options);
  }

  public final BulkByScrollResponse deleteByQuery(final DeleteByQueryRequest deleteByQueryRequest,
                                                  final RequestOptions options)
    throws IOException {
    applyIndexPrefixes(deleteByQueryRequest);

    return highLevelClient.deleteByQuery(deleteByQueryRequest, options);
  }

  public final boolean exists(final GetIndexRequest getRequest, final RequestOptions options) throws IOException {
    applyIndexPrefixes(getRequest);

    return highLevelClient.indices().exists(getRequest, options);
  }

  public final GetResponse get(final GetRequest getRequest, final RequestOptions options) throws IOException {
    getRequest.index(indexNameService.getOptimizeIndexAliasForIndex(getRequest.index()));
    getRequest.type(DEFAULT_INDEX_TYPE);

    return highLevelClient.get(getRequest, options);
  }

  public final RestClient getLowLevelClient() {
    return getHighLevelClient().getLowLevelClient();
  }

  public final IndexResponse index(final IndexRequest indexRequest, final RequestOptions options) throws IOException {
    applyIndexPrefix(indexRequest);
    if (indexRequest.type() == null) {
      indexRequest.type(DEFAULT_INDEX_TYPE);
    }

    return highLevelClient.index(indexRequest, options);
  }

  public final MultiGetResponse mget(final MultiGetRequest multiGetRequest, final RequestOptions options)
    throws IOException {
    multiGetRequest.getItems()
      .forEach(item -> item.index(indexNameService.getOptimizeIndexAliasForIndex(item.index())));
    multiGetRequest.getItems().forEach(item -> item.type(DEFAULT_INDEX_TYPE));

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
    if (searchRequest.types().length != 0) {
      List<String> typesToSearch = new ArrayList<>();
      typesToSearch.add(DEFAULT_INDEX_TYPE);
      typesToSearch.addAll(Arrays.asList(searchRequest.types()));
      searchRequest.types(typesToSearch.toArray(new String[0]));
    }

    return highLevelClient.search(searchRequest, options);
  }

  public final UpdateResponse update(final UpdateRequest updateRequest, final RequestOptions options)
    throws IOException {
    applyIndexPrefix(updateRequest);
    updateRequest.type(DEFAULT_INDEX_TYPE);

    return highLevelClient.update(updateRequest, options);
  }

  public final BulkByScrollResponse updateByQuery(final UpdateByQueryRequest updateByQueryRequest,
                                                  final RequestOptions options) throws IOException {
    applyIndexPrefixes(updateByQueryRequest);
    List<String> typesToSearch = new ArrayList<>();
    typesToSearch.add(DEFAULT_INDEX_TYPE);
    if (updateByQueryRequest.getDocTypes() != null) {
      typesToSearch.addAll(Arrays.asList(updateByQueryRequest.getDocTypes()));
    }
    updateByQueryRequest.setDocTypes(typesToSearch.toArray(new String[typesToSearch.size()]));

    return highLevelClient.updateByQuery(updateByQueryRequest, options);
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

}
