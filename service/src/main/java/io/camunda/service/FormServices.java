/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.with;
import static io.camunda.service.authorization.Authorizations.UNSPECIFIED_AUTHORIZATION;

import io.camunda.search.clients.FormSearchClient;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.SearchQueryResult;
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
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.formSearchClient = formSearchClient;
  }

  @Override
  public FormServices withAuthentication(final CamundaAuthentication authentication) {
    return new FormServices(
        brokerClient, securityContextProvider, formSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<FormEntity> search(final FormQuery query) {
    return formSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, with(UNSPECIFIED_AUTHORIZATION)))
        .searchForms(query);
  }

  public FormEntity getByKey(final Long key) {
    return formSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, with(UNSPECIFIED_AUTHORIZATION)))
        .getFormByKey(key);
  }

  public Optional<FormEntity> getLatestVersionByFormId(final String formId) {
    return search(FormQuery.of(b -> b.filter(f -> f.formIds(formId)).sort(s -> s.version().desc())))
        .items()
        .stream()
        .findFirst();
  }
}
