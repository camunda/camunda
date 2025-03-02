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
import io.camunda.security.auth.Authentication;
import io.camunda.service.ApiServices;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.util.ExceptionUtil;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public abstract class SearchQueryService<T extends ApiServices<T>, Q extends SearchQueryBase, D>
    extends ApiServices<T> {

  protected SearchQueryService(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
  }

  public abstract SearchQueryResult<D> search(final Q query);

  protected <E> E getSingleResultOrThrow(
      final SearchQueryResult<E> searchQueryResult,
      final Object key,
      final String entityTypeLabel) {
    if (searchQueryResult.total() < 1) {
      throw new CamundaSearchException(
          ExceptionUtil.ERROR_NOT_FOUND_ENTITY_BY_KEY.formatted(entityTypeLabel, key),
          CamundaSearchException.Reason.NOT_FOUND);
    } else if (searchQueryResult.total() > 1) {
      throw new CamundaSearchException(
          ExceptionUtil.ERROR_NOT_UNIQUE_ENTITY.formatted(entityTypeLabel, key),
          CamundaSearchException.Reason.NOT_UNIQUE);

    } else {
      return searchQueryResult.items().stream().findFirst().orElseThrow();
    }
  }
}
