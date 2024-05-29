/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.data.clients.DataStoreClient;
import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.core.DataStoreSearchResponse;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;

public class StubbedDataStoreClient implements DataStoreClient {

  private SearchRequestHandler<?> searchRequestHandler;
  private final List<DataStoreSearchRequest> searchRequests = new ArrayList<>();

  public StubbedDataStoreClient() {}

  @Override
  public <T> Either<Exception, DataStoreSearchResponse<T>> search(
      final DataStoreSearchRequest searchRequest, final Class<T> documentClass) {
    searchRequests.add(searchRequest);

    try {
      final DataStoreSearchResponse response = searchRequestHandler.handle(searchRequest);
      return Either.right(response);
    } catch (final Exception e) {
      return Either.left(e);
    }
  }

  public DataStoreSearchRequest getSingleSearchRequest() {
    assertThat(searchRequests).hasSize(1);
    return searchRequests.get(0);
  }

  public List<DataStoreSearchRequest> getSearchRequests() {
    return searchRequests;
  }

  public <DocumentT> void registerHandler(
      final SearchRequestHandler<DocumentT> searchRequestHandler) {
    this.searchRequestHandler = searchRequestHandler;
  }

  public interface RequestStub<DocumentT> extends SearchRequestHandler<DocumentT> {
    void registerWith(StubbedDataStoreClient client);
  }

  @FunctionalInterface
  public interface SearchRequestHandler<DocumentT> {
    DataStoreSearchResponse<DocumentT> handle(DataStoreSearchRequest request) throws Exception;
  }
}
