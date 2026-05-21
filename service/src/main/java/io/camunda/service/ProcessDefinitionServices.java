/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.MESSAGE_SUBSCRIPTION_READ_AUTHORIZATION;
import static io.camunda.service.authorization.Authorizations.PROCESS_DEFINITION_READ_AUTHORIZATION;
import static io.camunda.service.authorization.Authorizations.PROCESS_INSTANCE_READ_AUTHORIZATION;

import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessDefinitionInstanceStatisticsEntity;
import io.camunda.search.entities.ProcessDefinitionInstanceVersionStatisticsEntity;
import io.camunda.search.entities.ProcessDefinitionMessageSubscriptionStatisticsEntity;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionMessageSubscriptionStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
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
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.processDefinitionSearchClient = processDefinitionSearchClient;
    this.formServices = formServices;
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> search(
      final ProcessDefinitionQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            processDefinitionSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, PROCESS_DEFINITION_READ_AUTHORIZATION))
                .searchProcessDefinitions(query));
  }

  public List<ProcessFlowNodeStatisticsEntity> elementStatistics(
      final ProcessDefinitionStatisticsFilter filter, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            processDefinitionSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, PROCESS_INSTANCE_READ_AUTHORIZATION))
                .processDefinitionFlowNodeStatistics(filter));
  }

  public ProcessDefinitionEntity getByKey(
      final Long processDefinitionKey, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            processDefinitionSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withAuthorization(
                            PROCESS_DEFINITION_READ_AUTHORIZATION,
                            ProcessDefinitionEntity::processDefinitionId)))
                .getProcessDefinition(processDefinitionKey));
  }

  public SearchQueryResult<ProcessDefinitionInstanceStatisticsEntity>
      getProcessDefinitionInstanceStatistics(
          final ProcessDefinitionInstanceStatisticsQuery query,
          final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            processDefinitionSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, PROCESS_INSTANCE_READ_AUTHORIZATION))
                .processDefinitionInstanceStatistics(query));
  }

  public SearchQueryResult<ProcessDefinitionInstanceVersionStatisticsEntity>
      searchProcessDefinitionInstanceVersionStatistics(
          final ProcessDefinitionInstanceVersionStatisticsQuery query,
          final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            processDefinitionSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, PROCESS_INSTANCE_READ_AUTHORIZATION))
                .processDefinitionInstanceVersionStatistics(query));
  }

  public Optional<FormEntity> getProcessDefinitionStartForm(
      final long processDefinitionKey, final CamundaAuthentication authentication) {
    return Optional.ofNullable(getByKey(processDefinitionKey, authentication))
        .filter(p -> p.formId() != null && !p.formId().isEmpty())
        .flatMap(
            p ->
                formServices.getLatestVersionByFormIdAndTenantId(
                    p.formId(), p.tenantId(), CamundaAuthentication.anonymous()));
  }

  public Optional<String> getProcessDefinitionXml(
      final Long processDefinitionKey, final CamundaAuthentication authentication) {
    final var processDefinition = getByKey(processDefinitionKey, authentication);
    return Optional.ofNullable(processDefinition)
        .map(ProcessDefinitionEntity::bpmnXml)
        .filter(xml -> !xml.isEmpty());
  }

  public SearchQueryResult<ProcessDefinitionMessageSubscriptionStatisticsEntity>
      getProcessDefinitionMessageSubscriptionStatistics(
          final ProcessDefinitionMessageSubscriptionStatisticsQuery query,
          final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            processDefinitionSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, MESSAGE_SUBSCRIPTION_READ_AUTHORIZATION))
                .getProcessDefinitionMessageSubscriptionStatistics(query));
  }
}
