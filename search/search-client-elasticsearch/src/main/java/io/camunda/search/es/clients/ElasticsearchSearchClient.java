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
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.WriteResponseBase;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.aggregator.SearchAggregator;
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
import io.camunda.search.es.transformers.search.SearchQueryHitTransformer;
import io.camunda.search.es.transformers.search.SearchRequestTransformer;
import io.camunda.search.es.transformers.search.SearchResponseTransformer;
import io.camunda.search.es.transformers.search.SearchWriteResponseTransformer;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.ErrorMessages;
import io.camunda.zeebe.util.collection.Tuple;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchSearchClient
    implements DocumentBasedSearchClient, DocumentBasedWriteClient {

  private static final int ELASTICSEARCH_QUERY_MAX_PAGE_SIZE = 10_000;
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
      return searchResponseTransformer.apply(
          Tuple.of(rawSearchResponse, searchRequest.aggregations()));
    } catch (final IOException | ElasticsearchException e) {
      LOGGER.warn(ErrorMessages.ERROR_FAILED_SEARCH_QUERY, e);
      throw new CamundaSearchException(
          ErrorMessages.ERROR_FAILED_SEARCH_QUERY, e, searchExceptionToReason(e));
    }
  }

  @Override
  public <T> SearchQueryResponse<T> scroll(
      final SearchQueryRequest searchRequest, final Class<T> documentClass) {
    final List<Hit<T>> totalHits = new ArrayList<>();
    final var queryMaxPageSize = ELASTICSEARCH_QUERY_MAX_PAGE_SIZE;

    String scrollId = null;
    try {
      final var request = transformSearchRequestToScrollRequest(searchRequest, queryMaxPageSize);
      final var rawSearchResponse = client.search(request, documentClass);
      scrollId = rawSearchResponse.scrollId();

      final var searchResponseHits = collectHits(rawSearchResponse.hits());
      totalHits.addAll(searchResponseHits);

      if (totalHits.size() < queryMaxPageSize) {
        // stop early; no need to continue with scroll
        final SearchResponseTransformer<T> searchResponseTransformer =
            getSearchResponseTransformer();
        return searchResponseTransformer.apply(
            Tuple.of(rawSearchResponse, searchRequest.aggregations()));
      }

      List<Hit<T>> scrollResponseHits;
      do {
        final var rawScrollResponse = scroll(scrollId, documentClass);
        scrollId = rawScrollResponse.scrollId();

        scrollResponseHits = collectHits(rawScrollResponse.hits());
        totalHits.addAll(scrollResponseHits);

      } while (!scrollResponseHits.isEmpty() && scrollResponseHits.size() >= queryMaxPageSize);

      return transformScrollResponseHits(totalHits);

    } catch (final IOException | ElasticsearchException e) {
      LOGGER.warn(ErrorMessages.ERROR_FAILED_FIND_ALL_QUERY, e);
      throw new CamundaSearchException(
          ErrorMessages.ERROR_FAILED_FIND_ALL_QUERY, e, searchExceptionToReason(e));
    } finally {
      clearScroll(scrollId);
    }
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
      LOGGER.warn(ErrorMessages.ERROR_FAILED_GET_REQUEST, e);
      throw new CamundaSearchException(
          ErrorMessages.ERROR_FAILED_GET_REQUEST, e, searchExceptionToReason(e));
    }
  }

  private SearchRequest transformSearchRequestToScrollRequest(
      final SearchQueryRequest searchRequest, final int queryMaxSize) {
    return getSearchRequestTransformer()
        .withSearchRequestCustomizer(
            c -> c.size(queryMaxSize).scroll(s -> s.time(SCROLL_KEEP_ALIVE_TIME)))
        .apply(searchRequest);
  }

  private <T> List<Hit<T>> collectHits(final HitsMetadata<T> hitsMetadata) {
    return hitsMetadata.hits().stream().toList();
  }

  private <T> SearchQueryResponse<T> transformScrollResponseHits(final List<Hit<T>> hits) {
    final var searchQueryHitTransformer = new SearchQueryHitTransformer<T>(transformers);
    final var transformedHits = hits.stream().map(searchQueryHitTransformer::apply).toList();
    return new SearchQueryResponse.Builder<T>()
        .totalHits(hits.size())
        .hits(transformedHits)
        .build();
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
      LOGGER.warn(ErrorMessages.ERROR_FAILED_INDEX_REQUEST, e);
      throw new CamundaSearchException(
          ErrorMessages.ERROR_FAILED_INDEX_REQUEST, e, searchExceptionToReason(e));
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
      LOGGER.warn(ErrorMessages.ERROR_FAILED_DELETE_REQUEST, e);
      throw new CamundaSearchException(
          ErrorMessages.ERROR_FAILED_DELETE_REQUEST, e, searchExceptionToReason(e));
    }
  }

  @Override
  public boolean deleteByFieldValue(
      final String dependentSourceIdx,
      final String dependentIdFieldName,
      final long processInstanceKey) {
    final var deleteRequest =
        createDeleteRequest(dependentSourceIdx, dependentIdFieldName, processInstanceKey);
    try {
      client.deleteByQuery(deleteRequest);
      System.out.printf(
          "DELETION SUCCESS FOR: %s, %s, %d%n",
          dependentSourceIdx, dependentIdFieldName, processInstanceKey);
      return true;
    } catch (final IOException | ElasticsearchException e) {
      System.out.printf(
          "DELETION FAILED FOR: %s, %s, %d%n",
          dependentSourceIdx, dependentIdFieldName, processInstanceKey);
      throw new RuntimeException(e);
    }
  }

  private DeleteByQueryRequest createDeleteRequest(
      final String indexName, final String idFieldName, final long processInstanceKey) {
    return new DeleteByQueryRequest.Builder()
        .index(indexName)
        .allowNoIndices(true)
        .ignoreUnavailable(true)
        .query(
            q ->
                q.term(
                    t ->
                        t.field(idFieldName)
                            .value(FieldValue.of(String.valueOf(processInstanceKey)))))
        .build();
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
        LOGGER.warn("Failed to clear scroll.", e);
      }
    }
  }

  private SearchRequestTransformer getSearchRequestTransformer() {
    final SearchTransfomer<SearchQueryRequest, SearchRequest> transformer =
        transformers.getTransformer(SearchQueryRequest.class);
    return (SearchRequestTransformer) transformer;
  }

  private <T> SearchResponseTransformer<T> getSearchResponseTransformer() {
    final SearchTransfomer<Tuple<SearchResponse<T>, List<SearchAggregator>>, SearchQueryResponse<T>>
        transformer = transformers.getTransformer(SearchQueryResponse.class);
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

  private static CamundaSearchException.Reason searchExceptionToReason(final Exception e) {
    if (e instanceof ConnectException
        || e instanceof SocketTimeoutException
        || e.getClass().getSimpleName().equals("ConnectionClosedException")) {
      return CamundaSearchException.Reason.CONNECTION_FAILED;
    }
    if (e instanceof ElasticsearchException) {
      return CamundaSearchException.Reason.SEARCH_SERVER_FAILED;
    }
    return CamundaSearchException.Reason.SEARCH_CLIENT_FAILED;
  }
}
