/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.util;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StubbedElasticsearchClient extends ElasticsearchClient {

  private SearchRequestHandler<?> searchRequestHandler;
  private final List<SearchRequest> searchRequests = new ArrayList<>();

  public StubbedElasticsearchClient() {
    super(null, null);
  }

  @Override
  public <TDocument> SearchResponse<TDocument> search(
      final SearchRequest searchRequest, final Class<TDocument> documentClass)
      throws IOException, ElasticsearchException {
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
    void registerWith(final StubbedElasticsearchClient client);
  }

  @FunctionalInterface
  public interface SearchRequestHandler<DocumentT> {
    SearchResponse<DocumentT> handle(final SearchRequest request) throws Exception;
  }
}
