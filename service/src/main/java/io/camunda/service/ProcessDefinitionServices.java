/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.with;
import static io.camunda.security.auth.Authorization.withResourceId;
import static io.camunda.service.authorization.Authorizations.PROCESS_DEFINITION_READ_AUTHORIZATION;

import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
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
    return processDefinitionSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, with(PROCESS_DEFINITION_READ_AUTHORIZATION)))
        .searchProcessDefinitions(query);
  }

  public List<ProcessFlowNodeStatisticsEntity> elementStatistics(
      final ProcessDefinitionStatisticsFilter filter) {
    return processDefinitionSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, with(PROCESS_DEFINITION_READ_AUTHORIZATION)))
        .processDefinitionFlowNodeStatistics(filter);
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
    return processDefinitionSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication,
                withResourceId(
                    PROCESS_DEFINITION_READ_AUTHORIZATION,
                    ProcessDefinitionEntity::processDefinitionId)))
        .getProcessDefinitionByKey(processDefinitionKey);
  }

  public Optional<String> getProcessDefinitionXml(final Long processDefinitionKey) {
    final var processDefinition = getByKey(processDefinitionKey);
    return Optional.ofNullable(processDefinition).map(ProcessDefinitionEntity::bpmnXml);
  }

  public Optional<FormEntity> getProcessDefinitionForm(final long processDefinitionKey) {
    return Optional.ofNullable(getByKey(processDefinitionKey))
        .map(ProcessDefinitionEntity::formId)
        .flatMap(f -> formServices.withAuthentication(authentication).getLatestVersionByFormId(f));
  }
}
