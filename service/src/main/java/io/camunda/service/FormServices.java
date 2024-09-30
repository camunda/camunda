/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.FormSearchClient;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.security.auth.Authentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public final class FormServices extends SearchQueryService<FormServices, FormQuery, FormEntity> {

  private final FormSearchClient formSearchClient;

  public FormServices(
      final BrokerClient brokerClient,
      final FormSearchClient formSearchClient,
      final Authentication authentication) {
    super(brokerClient, authentication);
    this.formSearchClient = formSearchClient;
  }

  @Override
  public FormServices withAuthentication(final Authentication authentication) {
    return new FormServices(brokerClient, formSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<FormEntity> search(final FormQuery query) {
    return formSearchClient.searchForms(query, authentication);
  }

  public FormEntity getByKey(final Long key) {
    final SearchQueryResult<FormEntity> result =
        search(SearchQueryBuilders.formSearchQuery().filter(f -> f.formKeys(key)).build());

    if (result.total() < 1) {
      throw new NotFoundException(String.format("Form with formKey=%d not found", key));
    } else if (result.total() > 1) {
      throw new CamundaSearchException(String.format("Found form with key %d more than once", key));
    } else {
      return result.items().stream().findFirst().orElseThrow();
    }
  }
}
