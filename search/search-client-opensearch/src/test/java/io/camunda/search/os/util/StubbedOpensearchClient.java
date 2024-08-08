/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

public class StubbedOpensearchClient extends OpenSearchClient {

  private SearchRequestHandler<?> searchRequestHandler;
  private final List<SearchRequest> searchRequests = new ArrayList<>();

  public StubbedOpensearchClient() {
    super(null, null);
  }

  @Override
  public <TDocument> SearchResponse<TDocument> search(
      final SearchRequest searchRequest, final Class<TDocument> documentClass)
      throws IOException, OpenSearchException {
    searchRequests.add(searchRequest);

    try {
      return (SearchResponse<TDocument>) searchRequestHandler.handle(searchRequest);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public SearchRequest getSingleSearchRequest() {
    assertThat(searchRequests).hasSize(1);
    return searchRequests.get(0);
  }

  public List<SearchRequest> getSearchRequests() {
    return searchRequests;
  }

  public <DocumentT> void registerHandler(
      final SearchRequestHandler<DocumentT> searchRequestHandler) {
    this.searchRequestHandler = searchRequestHandler;
  }

  public interface RequestStub<DocumentT> extends SearchRequestHandler<DocumentT> {
    void registerWith(final StubbedOpensearchClient client);
  }

  @FunctionalInterface
  public interface SearchRequestHandler<DocumentT> {
    SearchResponse<DocumentT> handle(final SearchRequest request) throws Exception;
  }
}
