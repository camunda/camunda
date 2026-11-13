/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.core.auth.RequiredAuthorization.withRequiredAuthorization;
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
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionMessageSubscriptionStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerResumeProcessDefinitionRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerSuspendProcessDefinitionRequest;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ProcessDefinitionServices
    extends SearchQueryService<
        ProcessDefinitionServices, ProcessDefinitionQuery, ProcessDefinitionEntity> {

  private final ProcessDefinitionSearchClient processDefinitionSearchClient;
  private final FormServices formServices;

  public ProcessDefinitionServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ProcessDefinitionSearchClient processDefinitionSearchClient,
      final FormServices formServices,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
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
                        withRequiredAuthorization(
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
    final var processDefinition = getByKey(processDefinitionKey, authentication);
    final var formId = processDefinition.formId();
    if (formId == null || formId.isBlank()) {
      return Optional.empty();
    }

    return Optional.of(
        formServices
            .getLatestVersionByFormIdAndTenantId(
                formId, processDefinition.tenantId(), CamundaAuthentication.anonymous())
            .orElseThrow(
                () ->
                    ErrorMapper.mapSearchError(
                        new CamundaSearchException(
                            "Start form '%s' not found for process definition key '%d'"
                                .formatted(formId, processDefinitionKey),
                            CamundaSearchException.Reason.NOT_FOUND))));
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

  public CompletableFuture<ProcessDefinitionSuspensionRecord> suspendProcessDefinitions(
      final ProcessDefinitionStateChangeRequest request,
      final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerSuspendProcessDefinitionRequest()
            .setProcessDefinitionKey(request.processDefinitionKey())
            .setBpmnProcessId(request.processDefinitionId())
            .setVersion(request.processDefinitionVersion())
            .setTenantId(request.tenantId())
            .setIncludeProcessInstances(request.includeProcessInstances()),
        authentication);
  }

  public CompletableFuture<ProcessDefinitionSuspensionRecord> resumeProcessDefinitions(
      final ProcessDefinitionStateChangeRequest request,
      final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerResumeProcessDefinitionRequest()
            .setProcessDefinitionKey(request.processDefinitionKey())
            .setBpmnProcessId(request.processDefinitionId())
            .setVersion(request.processDefinitionVersion())
            .setTenantId(request.tenantId())
            .setIncludeProcessInstances(request.includeProcessInstances()),
        authentication);
  }

  /**
   * Selects the process definition versions to suspend or resume: either a single version by key
   * (the ID, version and tenant are then unset) or by ID with an optional version ({@code -1} for
   * all versions) and tenant.
   */
  public record ProcessDefinitionStateChangeRequest(
      long processDefinitionKey,
      String processDefinitionId,
      int processDefinitionVersion,
      String tenantId,
      boolean includeProcessInstances) {}
}
