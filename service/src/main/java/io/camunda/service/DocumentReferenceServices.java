/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.VARIABLE_READ_AUTHORIZATION;

import io.camunda.search.clients.DocumentReferenceSearchClient;
import io.camunda.search.entities.DocumentReferenceEntity;
import io.camunda.search.query.DocumentReferenceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public final class DocumentReferenceServices
    extends SearchQueryService<
        DocumentReferenceServices, DocumentReferenceQuery, DocumentReferenceEntity> {

  private final DocumentReferenceSearchClient documentReferenceSearchClient;

  public DocumentReferenceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final DocumentReferenceSearchClient documentReferenceSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.documentReferenceSearchClient = documentReferenceSearchClient;
  }

  @Override
  public SearchQueryResult<DocumentReferenceEntity> search(
      final DocumentReferenceQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            documentReferenceSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, VARIABLE_READ_AUTHORIZATION))
                .searchDocumentReferences(query));
  }
}
