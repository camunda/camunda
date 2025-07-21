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
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
import java.util.Optional;

public class ProcessDefinitionServices
    extends SearchQueryService<
        ProcessDefinitionServices, ProcessDefinitionQuery, ProcessDefinitionEntity> {

  private final ProcessDefinitionSearchClient processDefinitionSearchClient;
  private final FormServices formServices;

  public ProcessDefinitionServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ProcessDefinitionSearchClient processDefinitionSearchClient,
      final FormServices formServices,
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.processDefinitionSearchClient = processDefinitionSearchClient;
    this.formServices = formServices;
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> search(final ProcessDefinitionQuery query) {
    return executeSearchRequest(
        () ->
            processDefinitionSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        Authorization.of(a -> a.processDefinition().readProcessDefinition())))
                .searchProcessDefinitions(query));
  }

  public List<ProcessFlowNodeStatisticsEntity> elementStatistics(
      final ProcessDefinitionStatisticsFilter filter) {
    return executeSearchRequest(
        () ->
            processDefinitionSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        Authorization.of(a -> a.processDefinition().readProcessDefinition())))
                .processDefinitionFlowNodeStatistics(filter));
  }

  @Override
  public ProcessDefinitionServices withAuthentication(final CamundaAuthentication authentication) {
    return new ProcessDefinitionServices(
        brokerClient,
        securityContextProvider,
        processDefinitionSearchClient,
        formServices,
        authentication);
  }

  public ProcessDefinitionEntity getByKey(final Long processDefinitionKey) {
    final var result =
        executeSearchRequest(
            () ->
                processDefinitionSearchClient
                    .withSecurityContext(
                        securityContextProvider.provideSecurityContext(authentication))
                    .searchProcessDefinitions(
                        processDefinitionSearchQuery(
                            q ->
                                q.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                                    .singleResult()))
                    .items()
                    .getFirst());
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());
    if (!securityContextProvider.isAuthorized(
        result.processDefinitionId(), authentication, authorization)) {
      throw ErrorMapper.createForbiddenException(authorization);
    }
    return result;
  }

  public Optional<FormEntity> getProcessDefinitionStartForm(final long processDefinitionKey) {
    return Optional.ofNullable(getByKey(processDefinitionKey))
        .filter(p -> p.formId() != null && !p.formId().isEmpty())
        .flatMap(
            p ->
                formServices
                    .withAuthentication(authentication)
                    .getLatestVersionByFormIdAndTenantId(p.formId(), p.tenantId()));
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
