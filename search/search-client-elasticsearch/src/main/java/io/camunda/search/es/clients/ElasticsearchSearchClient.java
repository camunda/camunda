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
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.core.SearchDeleteRequest;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchGetResponse;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.core.SearchWriteResponse;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.es.transformers.search.SearchDeleteRequestTransformer;
import io.camunda.search.es.transformers.search.SearchGetRequestTransformer;
import io.camunda.search.es.transformers.search.SearchGetResponseTransformer;
import io.camunda.search.es.transformers.search.SearchIndexRequestTransformer;
import io.camunda.search.es.transformers.search.SearchRequestTransformer;
import io.camunda.search.es.transformers.search.SearchResponseTransformer;
import io.camunda.search.es.transformers.search.SearchWriteResponseTransformer;
import io.camunda.search.exception.SearchQueryExecutionException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchSearchClient
    implements DocumentBasedSearchClient, DocumentBasedWriteClient, AutoCloseable {

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
    } catch (final IOException | ElasticsearchException ioe) {
      throw new SearchQueryExecutionException("Failed to execute search query", ioe);
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
      throw new SearchQueryExecutionException("Failed to execute findAll query", e);
    } finally {
      clearScroll(scrollId);
    }
    return result;
  }

  @Override
  public <T> SearchGetResponse<T> get(
      final SearchGetRequest getRequest, final Class<T> documentClass) {
    try {
      final var requestTransformer = getSearchGetRequestTranformer();
      final var request = requestTransformer.apply(getRequest);
      final var rawGetResponse = client.get(request, documentClass);
      final SearchGetResponseTransformer<T> getResponseTranformer =
          getSearchGetResponseTransformer();
      return getResponseTranformer.apply(rawGetResponse);
    } catch (final IOException | ElasticsearchException ioe) {
      LOGGER.debug("Failed to execute get request", ioe);
      throw new SearchQueryExecutionException("Failed to execute get request", ioe);
    }
  }

  @Override
  public <T> SearchWriteResponse index(final SearchIndexRequest<T> indexRequest) {
    try {
      final SearchIndexRequestTransformer<T> requestTransformer = getSearchIndexRequestTranformer();
      final var request = requestTransformer.apply(indexRequest);
      final var rawIndexResponse = client.index(request);
      final var indexResponseTransformer = getSearchWriteResponseTranformer();
      return indexResponseTransformer.apply(rawIndexResponse);
    } catch (final IOException | ElasticsearchException ioe) {
      LOGGER.debug("Failed to execute index request", ioe);
      throw new SearchQueryExecutionException("Failed to execute index request", ioe);
    }
  }

  @Override
  public SearchWriteResponse delete(final SearchDeleteRequest deleteRequest) {
    try {
      final var requestTransformer = getSearchDeleteRequestTranformer();
      final var request = requestTransformer.apply(deleteRequest);
      final var rawDeleteRequest = client.delete(request);
      final var deleteResponseTransformer = getSearchWriteResponseTranformer();
      return deleteResponseTransformer.apply(rawDeleteRequest);
    } catch (final IOException | ElasticsearchException ioe) {
      LOGGER.debug("Failed to execute delete request", ioe);
      throw new SearchQueryExecutionException("Failed to execute delete request", ioe);
    }
  }

  private <T> ScrollResponse<T> scroll(final String scrollId, final Class<T> documentClass)
      throws IOException {
    return client.scroll(r -> r.scrollId(scrollId), documentClass);
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

  private SearchGetRequestTransformer getSearchGetRequestTranformer() {
    final SearchTransfomer<SearchGetRequest, GetRequest> transformer =
        transformers.getTransformer(SearchGetRequest.class);
    return (SearchGetRequestTransformer) transformer;
  }

  private <T> SearchGetResponseTransformer<T> getSearchGetResponseTransformer() {
    final SearchTransfomer<GetResponse<T>, SearchGetResponse<T>> transformer =
        transformers.getTransformer(SearchGetResponse.class);
    return (SearchGetResponseTransformer<T>) transformer;
  }

  private <T> SearchIndexRequestTransformer<T> getSearchIndexRequestTranformer() {
    final SearchTransfomer<SearchIndexRequest<T>, IndexRequest<T>> transformer =
        transformers.getTransformer(SearchIndexRequest.class);
    return (SearchIndexRequestTransformer<T>) transformer;
  }

  private SearchDeleteRequestTransformer getSearchDeleteRequestTranformer() {
    final SearchTransfomer<SearchDeleteRequest, DeleteRequest> transformer =
        transformers.getTransformer(SearchDeleteRequest.class);
    return (SearchDeleteRequestTransformer) transformer;
  }

  private SearchWriteResponseTransformer getSearchWriteResponseTranformer() {
    final SearchTransfomer<WriteResponseBase, SearchWriteResponse> transformer =
        transformers.getTransformer(SearchWriteResponse.class);
    return (SearchWriteResponseTransformer) transformer;
  }

  @Override
  public void close() throws Exception {
    if (client != null) {
      try {
        client._transport().close();
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
