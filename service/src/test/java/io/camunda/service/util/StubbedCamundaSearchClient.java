/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;

public class StubbedCamundaSearchClient implements CamundaSearchClient {

  private SearchRequestHandler<?> searchRequestHandler;
  private final List<SearchQueryRequest> searchRequests = new ArrayList<>();

  public StubbedCamundaSearchClient() {}

  @Override
  public <T> Either<Exception, SearchQueryResponse<T>> search(
      final SearchQueryRequest searchRequest, final Class<T> documentClass) {
    searchRequests.add(searchRequest);

    try {
      final SearchQueryResponse response = searchRequestHandler.handle(searchRequest);
      return Either.right(response);
    } catch (final Exception e) {
      return Either.left(e);
    }
  }

  public SearchQueryRequest getSingleSearchRequest() {
    assertThat(searchRequests).hasSize(1);
    return searchRequests.get(0);
  }

  public List<SearchQueryRequest> getSearchRequests() {
    return searchRequests;
  }

  public <DocumentT> void registerHandler(
      final SearchRequestHandler<DocumentT> searchRequestHandler) {
    this.searchRequestHandler = searchRequestHandler;
  }

  @Override
  public void close() throws Exception {
    // noop
  }

  public interface RequestStub<DocumentT> extends SearchRequestHandler<DocumentT> {
    void registerWith(final StubbedCamundaSearchClient client);
  }

  @FunctionalInterface
  public interface SearchRequestHandler<DocumentT> {
    SearchQueryResponse<DocumentT> handle(final SearchQueryRequest request) throws Exception;
  }
}
