/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.clients;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.WriteResponseBase;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.core.SearchDeleteRequest;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchGetResponse;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.core.SearchWriteResponse;
import io.camunda.search.clients.index.IndexAliasRequest;
import io.camunda.search.clients.index.IndexAliasResponse;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.es.transformers.index.IndexAliasRequestTransformer;
import io.camunda.search.es.transformers.index.IndexAliasResponseTransformer;
import io.camunda.search.es.transformers.search.SearchDeleteRequestTransformer;
import io.camunda.search.es.transformers.search.SearchGetRequestTransformer;
import io.camunda.search.es.transformers.search.SearchGetResponseTransformer;
import io.camunda.search.es.transformers.search.SearchIndexRequestTransformer;
import io.camunda.search.es.transformers.search.SearchRequestTransformer;
import io.camunda.search.es.transformers.search.SearchResponseTransformer;
import io.camunda.search.es.transformers.search.SearchWriteResponseTransformer;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.util.ExceptionUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchSearchClient
    implements DocumentBasedSearchClient, DocumentBasedWriteClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchSearchClient.class);
  private static final String SCROLL_KEEP_ALIVE_TIME = "1m";

  private final ElasticsearchClient client;
  private final ElasticsearchTransformers transformers;

  public ElasticsearchSearchClient(final ElasticsearchClient client) {
    this(client, new ElasticsearchTransformers());
  }

  public ElasticsearchSearchClient(
      final ElasticsearchClient client, final ElasticsearchTransformers transformers) {
    this.client = client;
    this.transformers = transformers;
  }

  @Override
  public <T> SearchQueryResponse<T> search(
      final SearchQueryRequest searchRequest, final Class<T> documentClass) {
    try {
      final var requestTransformer = getSearchRequestTransformer();
      final var request = requestTransformer.apply(searchRequest);
      final SearchResponse<T> rawSearchResponse = client.search(request, documentClass);
      final SearchResponseTransformer<T> searchResponseTransformer = getSearchResponseTransformer();
      return searchResponseTransformer.apply(rawSearchResponse);
    } catch (final IOException | ElasticsearchException e) {
      LOGGER.error(ExceptionUtil.ERROR_FAILED_SEARCH_QUERY, e);
      throw new CamundaSearchException(
          ExceptionUtil.ERROR_FAILED_SEARCH_QUERY,
          e,
          CamundaSearchException.Reason.ES_CLIENT_FAILED);
    }
  }

  @Override
  public <T> List<T> findAll(final SearchQueryRequest searchRequest, final Class<T> documentClass) {
    final List<T> result = new ArrayList<>();
    String scrollId = null;
    try {
      final var request =
          getSearchRequestTransformer()
              .toSearchRequestBuilder(searchRequest)
              .scroll(s -> s.time(SCROLL_KEEP_ALIVE_TIME))
              .build();
      final SearchResponse<T> rawSearchResponse = client.search(request, documentClass);
      scrollId = rawSearchResponse.scrollId();
      var items = rawSearchResponse.hits().hits().stream().map(Hit::source).toList();
      result.addAll(items);
      final int pageSize = Optional.ofNullable(searchRequest.size()).orElse(items.size());
      while (!items.isEmpty() && items.size() == pageSize) {
        final ScrollResponse<T> scrollResponse = scroll(scrollId, documentClass);
        scrollId = scrollResponse.scrollId();
        items = scrollResponse.hits().hits().stream().map(Hit::source).toList();
        result.addAll(items);
      }
    } catch (final IOException | ElasticsearchException e) {
      LOGGER.error(ExceptionUtil.ERROR_FAILED_FIND_ALL_QUERY, e);
      throw new CamundaSearchException(
          ExceptionUtil.ERROR_FAILED_FIND_ALL_QUERY,
          e,
          CamundaSearchException.Reason.ES_CLIENT_FAILED);
    } finally {
      clearScroll(scrollId);
    }
    return result;
  }

  @Override
  public <T> SearchGetResponse<T> get(
      final SearchGetRequest getRequest, final Class<T> documentClass) {
    try {
      final var requestTransformer = getSearchGetRequestTransformer();
      final var request = requestTransformer.apply(getRequest);
      final var rawGetResponse = client.get(request, documentClass);
      final SearchGetResponseTransformer<T> getResponseTransformer =
          getSearchGetResponseTransformer();
      return getResponseTransformer.apply(rawGetResponse);
    } catch (final IOException | ElasticsearchException e) {
      LOGGER.error(ExceptionUtil.ERROR_FAILED_GET_REQUEST, e);
      throw new CamundaSearchException(
          ExceptionUtil.ERROR_FAILED_GET_REQUEST,
          e,
          CamundaSearchException.Reason.ES_CLIENT_FAILED);
    }
  }

  @Override
  public IndexAliasResponse getAlias(final IndexAliasRequest request) {
    try {
      final var requestTransformer = getIndexAliasRequestTransformer();
      final var elasticRequest = requestTransformer.apply(request);
      final var response = client.indices().getAlias(elasticRequest);
      return getIndexAliasResponseTransformer().apply(response);
    } catch (final IOException | ElasticsearchException e) {
      LOGGER.error(ExceptionUtil.ERROR_FAILED_GET_ALIAS_REQUEST, e);
      throw new CamundaSearchException(
          ExceptionUtil.ERROR_FAILED_GET_ALIAS_REQUEST,
          e,
          CamundaSearchException.Reason.ES_CLIENT_FAILED);
    }
  }

  @Override
  public <T> SearchWriteResponse index(final SearchIndexRequest<T> indexRequest) {
    try {
      final SearchIndexRequestTransformer<T> requestTransformer =
          getSearchIndexRequestTransformer();
      final var request = requestTransformer.apply(indexRequest);
      final var rawIndexResponse = client.index(request);
      final var indexResponseTransformer = getSearchWriteResponseTransformer();
      return indexResponseTransformer.apply(rawIndexResponse);
    } catch (final IOException | ElasticsearchException e) {
      LOGGER.error(ExceptionUtil.ERROR_FAILED_INDEX_REQUEST, e);
      throw new CamundaSearchException(
          ExceptionUtil.ERROR_FAILED_INDEX_REQUEST,
          e,
          CamundaSearchException.Reason.ES_CLIENT_FAILED);
    }
  }

  @Override
  public SearchWriteResponse delete(final SearchDeleteRequest deleteRequest) {
    try {
      final var requestTransformer = getSearchDeleteRequestTransformer();
      final var request = requestTransformer.apply(deleteRequest);
      final var rawDeleteRequest = client.delete(request);
      final var deleteResponseTransformer = getSearchWriteResponseTransformer();
      return deleteResponseTransformer.apply(rawDeleteRequest);
    } catch (final IOException | ElasticsearchException e) {
      LOGGER.error(ExceptionUtil.ERROR_FAILED_DELETE_REQUEST, e);
      throw new CamundaSearchException(
          ExceptionUtil.ERROR_FAILED_DELETE_REQUEST,
          e,
          CamundaSearchException.Reason.ES_CLIENT_FAILED);
    }
  }

  private <T> ScrollResponse<T> scroll(final String scrollId, final Class<T> documentClass)
      throws IOException {
    return client.scroll(
        r -> r.scrollId(scrollId).scroll(t -> t.time(SCROLL_KEEP_ALIVE_TIME)), documentClass);
  }

  private void clearScroll(final String scrollId) {
    if (scrollId != null) {
      try {
        client.clearScroll(r -> r.scrollId(scrollId));
      } catch (final IOException | ElasticsearchException e) {
        LOGGER.error("Failed to clear scroll.", e);
      }
    }
  }

  private SearchRequestTransformer getSearchRequestTransformer() {
    final SearchTransfomer<SearchQueryRequest, SearchRequest> transformer =
        transformers.getTransformer(SearchQueryRequest.class);
    return (SearchRequestTransformer) transformer;
  }

  private <T> SearchResponseTransformer<T> getSearchResponseTransformer() {
    final SearchTransfomer<SearchResponse<T>, SearchQueryResponse<T>> transformer =
        transformers.getTransformer(SearchQueryResponse.class);
    return (SearchResponseTransformer<T>) transformer;
  }

  private SearchGetRequestTransformer getSearchGetRequestTransformer() {
    final SearchTransfomer<SearchGetRequest, GetRequest> transformer =
        transformers.getTransformer(SearchGetRequest.class);
    return (SearchGetRequestTransformer) transformer;
  }

  private <T> SearchGetResponseTransformer<T> getSearchGetResponseTransformer() {
    final SearchTransfomer<GetResponse<T>, SearchGetResponse<T>> transformer =
        transformers.getTransformer(SearchGetResponse.class);
    return (SearchGetResponseTransformer<T>) transformer;
  }

  private <T> SearchIndexRequestTransformer<T> getSearchIndexRequestTransformer() {
    final SearchTransfomer<SearchIndexRequest<T>, IndexRequest<T>> transformer =
        transformers.getTransformer(SearchIndexRequest.class);
    return (SearchIndexRequestTransformer<T>) transformer;
  }

  private SearchDeleteRequestTransformer getSearchDeleteRequestTransformer() {
    final SearchTransfomer<SearchDeleteRequest, DeleteRequest> transformer =
        transformers.getTransformer(SearchDeleteRequest.class);
    return (SearchDeleteRequestTransformer) transformer;
  }

  private SearchWriteResponseTransformer getSearchWriteResponseTransformer() {
    final SearchTransfomer<WriteResponseBase, SearchWriteResponse> transformer =
        transformers.getTransformer(SearchWriteResponse.class);
    return (SearchWriteResponseTransformer) transformer;
  }

  private IndexAliasRequestTransformer getIndexAliasRequestTransformer() {
    final SearchTransfomer<IndexAliasRequest, GetAliasRequest> transformer =
        transformers.getTransformer(IndexAliasRequest.class);
    return (IndexAliasRequestTransformer) transformer;
  }

  private IndexAliasResponseTransformer getIndexAliasResponseTransformer() {
    final SearchTransfomer<GetAliasResponse, IndexAliasResponse> transformer =
        transformers.getTransformer(IndexAliasResponse.class);
    return (IndexAliasResponseTransformer) transformer;
  }

  @Override
  public void close() {
    if (client != null) {
      try {
        client._transport().close();
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
