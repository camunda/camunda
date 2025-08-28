/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.core;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.query.SearchQueryBase;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.ApiServices;
import io.camunda.service.ApiServicesExecutorProvider;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Supplier;

public abstract class SearchQueryService<T extends ApiServices<T>, Q extends SearchQueryBase, D>
    extends ApiServices<T> {

  protected SearchQueryService(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider) {
    super(brokerClient, securityContextProvider, authentication, executorProvider);
  }

  public abstract SearchQueryResult<D> search(final Q query);

  protected <R> R executeSearchRequest(final Supplier<R> searchRequest) {
    try {
      return searchRequest.get();
    } catch (final CamundaSearchException cse) {
      throw ErrorMapper.mapSearchError(cse);
    }
  }
}
