/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.core.auth.RequiredAuthorization.withRequiredAuthorization;
import static io.camunda.service.authorization.Authorizations.VARIABLE_READ_AUTHORIZATION;

import io.camunda.search.clients.VariableSearchClient;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableNameQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;

public final class VariableServices
    extends SearchQueryService<VariableServices, VariableQuery, VariableEntity> {

  private final VariableSearchClient variableSearchClient;
  private final UpdateMetadataEnricher updateMetadataEnricher;

  public VariableServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final VariableSearchClient variableSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    this(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        variableSearchClient,
        null,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  public VariableServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final VariableSearchClient variableSearchClient,
      final AuditLogServices auditLogServices,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.variableSearchClient = variableSearchClient;
    updateMetadataEnricher = new UpdateMetadataEnricher(auditLogServices);
  }

  @Override
  public SearchQueryResult<VariableEntity> search(
      final VariableQuery query, final CamundaAuthentication authentication) {
    return updateMetadataEnricher.enrichPage(
        searchRaw(query, authentication),
        AuditLogEntityType.VARIABLE,
        item -> item.variableKey().toString(),
        (item, metadata) -> item.withUpdateMetadata(metadata.updatedBy(), metadata.updatedAt()),
        authentication);
  }

  SearchQueryResult<VariableEntity> searchRaw(
      final VariableQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            variableSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, VARIABLE_READ_AUTHORIZATION))
                .searchVariables(query));
  }

  public List<String> searchVariableNames(
      final VariableNameQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            variableSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, VARIABLE_READ_AUTHORIZATION))
                .searchVariableNames(query));
  }

  public VariableEntity getByKey(final Long key, final CamundaAuthentication authentication) {
    return updateMetadataEnricher.enrichItem(
        getByKeyRaw(key, authentication),
        AuditLogEntityType.VARIABLE,
        item -> item.variableKey().toString(),
        (item, metadata) -> item.withUpdateMetadata(metadata.updatedBy(), metadata.updatedAt()),
        authentication);
  }

  VariableEntity getByKeyRaw(final Long key, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            variableSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withRequiredAuthorization(
                            VARIABLE_READ_AUTHORIZATION, VariableEntity::processDefinitionId)))
                .getVariable(key));
  }
}
