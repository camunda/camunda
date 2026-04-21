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
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.Optional;

public final class FormServices extends SearchQueryService<FormServices, FormQuery, FormEntity> {

  private final FormSearchClient formSearchClient;

  public FormServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final FormSearchClient formSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.formSearchClient = formSearchClient;
  }

  @Override
  public SearchQueryResult<FormEntity> search(
      final FormQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            formSearchClient
                .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
                .searchForms(query));
  }

  public FormEntity getByKey(final Long key, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            formSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        CamundaAuthentication.anonymous()))
                .getForm(key));
  }

  public Optional<FormEntity> getLatestVersionByFormIdAndTenantId(
      final String formId, final String tenantId, final CamundaAuthentication authentication) {
    return search(
            SearchQueryBuilders.formSearchQuery()
                .filter(f -> f.formIds(formId).tenantId(tenantId))
                .sort(s -> s.version().desc())
                .build(),
            authentication)
        .items()
        .stream()
        .findFirst();
  }
}
