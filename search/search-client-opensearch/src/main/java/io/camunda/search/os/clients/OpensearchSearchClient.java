/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.clients;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.search.os.transformers.search.SearchResponseTransformer;
import io.camunda.search.transformers.SearchTransfomer;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
<<<<<<< HEAD
=======
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
>>>>>>> 36c1918d (fix: Align API logging levels to C8 recommendations)
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

public final class OpensearchSearchClient implements CamundaSearchClient {

  private final OpenSearchClient client;
  private final OpensearchTransformers transformers;

  public OpensearchSearchClient(final OpenSearchClient client) {
    this(client, new OpensearchTransformers());
  }

  public OpensearchSearchClient(
      final OpenSearchClient client, final OpensearchTransformers transformers) {
    this.client = client;
    this.transformers = transformers;
  }

  @Override
  public <T> Either<Exception, SearchQueryResponse<T>> search(
      final SearchQueryRequest searchRequest, final Class<T> documentClass) {
    try {
      final var requestTransformer = getSearchRequestTransformer();
      final var request = requestTransformer.apply(searchRequest);
      final SearchResponse<T> rawSearchResponse = client.search(request, documentClass);
      final SearchResponseTransformer<T> searchResponseTransformer = getSearchResponseTransformer();
<<<<<<< HEAD
      final SearchQueryResponse<T> response = searchResponseTransformer.apply(rawSearchResponse);
      return Either.right(response);
    } catch (final IOException ioe) {
      return Either.left(ioe);
    } catch (final OpenSearchException e) {
      return Either.left(e);
    }
  }

  private SearchTransfomer<SearchQueryRequest, SearchRequest> getSearchRequestTransformer() {
    return transformers.getTransformer(SearchQueryRequest.class);
=======
      return searchResponseTransformer.apply(rawSearchResponse);
    } catch (final IOException | OpenSearchException e) {
      logException(ErrorMessages.ERROR_FAILED_SEARCH_QUERY, e);
      throw new CamundaSearchException(
          ErrorMessages.ERROR_FAILED_SEARCH_QUERY, e, searchExceptionToReason(e));
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
      while (!items.isEmpty() && pageSize == items.size()) {
        final ScrollResponse<T> scrollResponse = scroll(scrollId, documentClass);
        scrollId = scrollResponse.scrollId();
        items = scrollResponse.hits().hits().stream().map(Hit::source).toList();
        result.addAll(items);
      }
    } catch (final IOException | OpenSearchException e) {
      logException(ErrorMessages.ERROR_FAILED_FIND_ALL_QUERY, e);
      throw new CamundaSearchException(
          ErrorMessages.ERROR_FAILED_FIND_ALL_QUERY, e, searchExceptionToReason(e));
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
      final var rawSearchResponse = client.get(request, documentClass);
      final SearchGetResponseTransformer<T> searchResponseTransformer =
          getSearchGetResponseTransformer();
      return searchResponseTransformer.apply(rawSearchResponse);
    } catch (final IOException | OpenSearchException e) {
      logException(ErrorMessages.ERROR_FAILED_GET_REQUEST, e);
      throw new CamundaSearchException(
          ErrorMessages.ERROR_FAILED_GET_REQUEST, e, searchExceptionToReason(e));
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
    } catch (final IOException | OpenSearchException e) {
      LOGGER.error(ErrorMessages.ERROR_FAILED_INDEX_REQUEST, e);
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
    } catch (final IOException | OpenSearchException e) {
      logException(ErrorMessages.ERROR_FAILED_DELETE_REQUEST, e);
      throw new CamundaSearchException(
          ErrorMessages.ERROR_FAILED_DELETE_REQUEST, e, searchExceptionToReason(e));
    }
  }

  private <T> ScrollResponse<T> scroll(final String scrollId, final Class<T> documentClass)
      throws IOException {
    return client.scroll(
        r -> r.scrollId(scrollId).scroll(s -> s.time(SCROLL_KEEP_ALIVE_TIME)), documentClass);
  }

  private void clearScroll(final String scrollId) {
    if (scrollId != null) {
      try {
        client.clearScroll(r -> r.scrollId(scrollId));
      } catch (final IOException | OpenSearchException e) {
        logException("Failed to clear scroll.", e);
      }
    }
  }

  private SearchRequestTransformer getSearchRequestTransformer() {
    final SearchTransfomer<SearchQueryRequest, SearchRequest> transformer =
        transformers.getTransformer(SearchQueryRequest.class);
    return (SearchRequestTransformer) transformer;
>>>>>>> 36c1918d (fix: Align API logging levels to C8 recommendations)
  }

  private <T> SearchResponseTransformer<T> getSearchResponseTransformer() {
    return new SearchResponseTransformer<>(transformers);
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
<<<<<<< HEAD
=======

  private static CamundaSearchException.Reason searchExceptionToReason(final Exception e) {
    if (e instanceof ConnectException) {
      return CamundaSearchException.Reason.CONNECTION_FAILED;
    }
    if (e instanceof OpenSearchException) {
      return CamundaSearchException.Reason.SEARCH_SERVER_FAILED;
    }
    return CamundaSearchException.Reason.SEARCH_CLIENT_FAILED;
  }

  static void logException(String msg, Exception exception) {
    if (exception instanceof SocketTimeoutException) {
      LOGGER.warn("{}: {}", msg, exception.getMessage());
    } else {
      LOGGER.error(msg, exception);
    }
  }
>>>>>>> 36c1918d (fix: Align API logging levels to C8 recommendations)
}
