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
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.util.ExceptionUtil;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.Optional;

public final class FormServices extends SearchQueryService<FormServices, FormQuery, FormEntity> {

  private final FormSearchClient formSearchClient;

  public FormServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final FormSearchClient formSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.formSearchClient = formSearchClient;
  }

  @Override
  public FormServices withAuthentication(final Authentication authentication) {
    return new FormServices(
        brokerClient, securityContextProvider, formSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<FormEntity> search(final FormQuery query) {
    return formSearchClient
        .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
        .searchForms(query);
  }

  public FormEntity getByKey(final Long key) {
    final SearchQueryResult<FormEntity> result =
        search(SearchQueryBuilders.formSearchQuery().filter(f -> f.formKeys(key)).build());

    if (result.total() < 1) {
      throw new CamundaSearchException(
          ExceptionUtil.ERROR_NOT_FOUND_FORM_BY_KEY.formatted(key),
          CamundaSearchException.Reason.NOT_FOUND);
    } else if (result.total() > 1) {
      throw new CamundaSearchException(
          ExceptionUtil.ERROR_NOT_UNIQUE_FORM.formatted(key),
          CamundaSearchException.Reason.NOT_UNIQUE);
    } else {
      return result.items().stream().findFirst().orElseThrow();
    }
  }

  public Optional<FormEntity> getLatestVersionByFormId(final String formId) {
    final Optional<FormEntity> result =
        search(
                SearchQueryBuilders.formSearchQuery()
                    .filter(f -> f.formIds(formId))
                    .sort(s -> s.version().desc())
                    .build())
            .items()
            .stream()
            .findFirst();

    if (result.isPresent()) {
      return result;
    } else {
      return Optional.empty();
    }
  }
}
