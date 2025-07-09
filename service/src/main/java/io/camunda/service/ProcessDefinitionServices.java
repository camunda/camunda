/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.processDefinitionSearchQuery;

import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
import java.util.Optional;

public class ProcessDefinitionServices
    extends SearchQueryService<
        ProcessDefinitionServices, ProcessDefinitionQuery, ProcessDefinitionEntity> {

  private final ProcessDefinitionSearchClient processDefinitionSearchClient;

  public ProcessDefinitionServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ProcessDefinitionSearchClient processDefinitionSearchClient,
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.processDefinitionSearchClient = processDefinitionSearchClient;
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> search(final ProcessDefinitionQuery query) {
    return processDefinitionSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication,
                Authorization.of(a -> a.processDefinition().readProcessDefinition())))
        .searchProcessDefinitions(query);
  }

  public List<ProcessFlowNodeStatisticsEntity> elementStatistics(
      final ProcessDefinitionStatisticsFilter filter) {
    return processDefinitionSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication,
                Authorization.of(a -> a.processDefinition().readProcessDefinition())))
        .processDefinitionFlowNodeStatistics(filter);
  }

  @Override
  public ProcessDefinitionServices withAuthentication(final CamundaAuthentication authentication) {
    return new ProcessDefinitionServices(
        brokerClient, securityContextProvider, processDefinitionSearchClient, authentication);
  }

  public ProcessDefinitionEntity getByKey(final Long processDefinitionKey) {
    final var result =
        processDefinitionSearchClient
            .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
            .searchProcessDefinitions(
                processDefinitionSearchQuery(
                    q ->
                        q.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                            .singleResult()))
            .items()
            .getFirst();
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());
    if (!securityContextProvider.isAuthorized(
        result.processDefinitionId(), authentication, authorization)) {
      throw new ForbiddenException(authorization);
    }
    return result;
  }

  public Optional<String> getProcessDefinitionXml(final Long processDefinitionKey) {
    final var processDefinition = getByKey(processDefinitionKey);
    final var xml = processDefinition.bpmnXml();
    if (xml == null || xml.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(xml);
    }
  }
}
