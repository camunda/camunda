/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query;

import io.camunda.data.clients.DataStoreClient;
import io.camunda.service.ApiServices;
import io.camunda.service.auth.Authentication;
import io.camunda.service.query.search.SearchQueryBase;
import io.camunda.service.query.search.SearchQueryExecutor;
import io.camunda.service.query.search.SearchQueryResult;

public abstract class SearchQueryService<
        T extends ApiServices<T>, Q extends SearchQueryBase<?, ?>, D>
    extends ApiServices<T> {

  protected final SearchQueryExecutor executor;

  protected SearchQueryService(
      final DataStoreClient dataStoreClient, final Authentication authentication) {
    super(dataStoreClient, authentication);
    executor = new SearchQueryExecutor(dataStoreClient, authentication);
  }

  public abstract SearchQueryResult<D> search(final Q query);
}
