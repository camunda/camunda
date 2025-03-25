/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import static io.camunda.search.clients.core.RequestBuilders.searchRequest;

import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchGetResponse;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryRequest.Builder;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.transformers.aggregate.SearchAggregationResult;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.util.CloseableSilently;
import java.util.List;
import java.util.function.Function;

public interface DocumentBasedSearchClient extends CloseableSilently {

  <T> SearchQueryResponse<T> search(
      final SearchQueryRequest searchRequest, final Class<T> documentClass);

  default <T> SearchQueryResponse<T> search(
      final Function<Builder, ObjectBuilder<SearchQueryRequest>> fn, final Class<T> documentClass) {
    return search(searchRequest(fn), documentClass);
  }

  <T> List<T> findAll(final SearchQueryRequest searchRequest, final Class<T> documentClass);

  <T> SearchGetResponse<T> get(final SearchGetRequest getRequest, final Class<T> documentClass);

  SearchAggregationResult aggregate(final SearchQueryRequest searchRequest);
}
