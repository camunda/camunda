/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import io.camunda.document.store.EnvironmentConfigurationLoader;
import io.camunda.document.store.SimpleDocumentStoreRegistry;
import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.clients.BatchOperationSearchClient;
import io.camunda.search.clients.DecisionDefinitionSearchClient;
import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.clients.FormSearchClient;
import io.camunda.search.clients.GroupSearchClient;
import io.camunda.search.clients.IncidentSearchClient;
import io.camunda.search.clients.JobSearchClient;
import io.camunda.search.clients.MappingRuleSearchClient;
import io.camunda.search.clients.MessageSubscriptionSearchClient;
import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.RoleSearchClient;
import io.camunda.search.clients.SequenceFlowSearchClient;
import io.camunda.search.clients.TenantSearchClient;
import io.camunda.search.clients.UsageMetricsSearchClient;
import io.camunda.search.clients.UserSearchClient;
import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.clients.VariableSearchClient;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.service.AdHocSubProcessActivityServices;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.BatchOperationServices;
import io.camunda.service.ClockServices;
import io.camunda.service.DecisionDefinitionServices;
import io.camunda.service.DecisionInstanceServices;
import io.camunda.service.DecisionRequirementsServices;
import io.camunda.service.DocumentServices;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.FormServices;
import io.camunda.service.GroupServices;
import io.camunda.service.IncidentServices;
import io.camunda.service.JobServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.MessageServices;
import io.camunda.service.MessageSubscriptionServices;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ResourceServices;
import io.camunda.service.RoleServices;
import io.camunda.service.SignalServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UsageMetricsServices;
import io.camunda.service.UserServices;
import io.camunda.service.UserTaskServices;
import io.camunda.service.VariableServices;
import io.camunda.service.cache.ProcessCache;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResult;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
public class CamundaServicesConfiguration {

  @Bean
  public UsageMetricsServices usageMetricsServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final UsageMetricsSearchClient usageMetricsSearchClient) {
    return new UsageMetricsServices(
        brokerClient, securityContextProvider, usageMetricsSearchClient, null);
  }

  @Bean
  public JobServices<JobActivationResult> jobServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ActivateJobsHandler<JobActivationResult> activateJobsHandler,
      final JobSearchClient jobSearchClient) {
    return new JobServices<>(
        brokerClient, securityContextProvider, activateJobsHandler, jobSearchClient, null);
  }

  @Bean
  public DecisionDefinitionServices decisionDefinitionServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final DecisionDefinitionSearchClient decisionDefinitionSearchClient,
      final DecisionRequirementsServices decisionRequirementsServices) {
    return new DecisionDefinitionServices(
        brokerClient,
        securityContextProvider,
        decisionDefinitionSearchClient,
        decisionRequirementsServices,
        null);
  }

  @Bean
  public DecisionInstanceServices decisionInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final DecisionInstanceSearchClient decisionInstanceSearchClient) {
    return new DecisionInstanceServices(
        brokerClient, securityContextProvider, decisionInstanceSearchClient, null);
  }

  @Bean
  public ProcessDefinitionServices processDefinitionServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ProcessDefinitionSearchClient processDefinitionSearchClient,
      final FormServices formServices) {
    return new ProcessDefinitionServices(
        brokerClient, securityContextProvider, processDefinitionSearchClient, formServices, null);
  }

  @Bean
  public ProcessInstanceServices processInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ProcessInstanceSearchClient processInstanceSearchClient,
      final SequenceFlowSearchClient sequenceFlowSearchClient,
      final IncidentServices incidentServices) {
    return new ProcessInstanceServices(
        brokerClient,
        securityContextProvider,
        processInstanceSearchClient,
        sequenceFlowSearchClient,
        incidentServices,
        null);
  }

  @Bean
  public DecisionRequirementsServices decisionRequirementsServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final DecisionRequirementSearchClient decisionRequirementSearchClient) {
    return new DecisionRequirementsServices(
        brokerClient, securityContextProvider, decisionRequirementSearchClient, null);
  }

  @Bean
  public ElementInstanceServices elementInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient,
      final ProcessCache processCache) {
    return new ElementInstanceServices(
        brokerClient, securityContextProvider, flowNodeInstanceSearchClient, processCache, null);
  }

  @Bean
  public AdHocSubProcessActivityServices adHocSubProcessActivityServices(
      final BrokerClient brokerClient, final SecurityContextProvider securityContextProvider) {
    return new AdHocSubProcessActivityServices(brokerClient, securityContextProvider, null);
  }

  @Bean
  public IncidentServices incidentServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final IncidentSearchClient incidentSearchClient) {
    return new IncidentServices(brokerClient, securityContextProvider, incidentSearchClient, null);
  }

  @Bean
  public RoleServices roleServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final RoleSearchClient roleSearchClient) {
    return new RoleServices(brokerClient, securityContextProvider, roleSearchClient, null);
  }

  @Bean
  public TenantServices tenantServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final TenantSearchClient tenantSearchClient) {
    return new TenantServices(brokerClient, securityContextProvider, tenantSearchClient, null);
  }

  @Bean
  public GroupServices groupServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final GroupSearchClient groupSearchClient) {
    return new GroupServices(brokerClient, securityContextProvider, groupSearchClient, null);
  }

  @Bean
  public UserServices userServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final UserSearchClient userSearchClient,
      final PasswordEncoder passwordEncoder) {
    return new UserServices(
        brokerClient, securityContextProvider, userSearchClient, null, passwordEncoder);
  }

  @Bean
  public UserTaskServices userTaskServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final UserTaskSearchClient userTaskSearchClient,
      final FormServices formServices,
      final ElementInstanceServices elementInstanceServices,
      final VariableServices variableServices,
      final ProcessCache processCache) {
    return new UserTaskServices(
        brokerClient,
        securityContextProvider,
        userTaskSearchClient,
        formServices,
        elementInstanceServices,
        variableServices,
        processCache,
        null);
  }

  @Bean
  public VariableServices variableServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final VariableSearchClient variableSearchClient) {
    return new VariableServices(brokerClient, securityContextProvider, variableSearchClient, null);
  }

  @Bean
  public MessageServices messageServices(
      final BrokerClient brokerClient, final SecurityContextProvider securityContextProvider) {
    return new MessageServices(brokerClient, securityContextProvider, null);
  }

  @Bean
  public DocumentServices documentServices(
      final BrokerClient brokerClient, final SecurityContextProvider securityContextProvider) {
    return new DocumentServices(
        brokerClient,
        securityContextProvider,
        null,
        new SimpleDocumentStoreRegistry(new EnvironmentConfigurationLoader()));
  }

  @Bean
  public AuthorizationServices authorizationServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AuthorizationSearchClient authorizationSearchClient) {
    return new AuthorizationServices(
        brokerClient, securityContextProvider, authorizationSearchClient, null);
  }

  @Bean
  public ClockServices clockServices(
      final BrokerClient brokerClient, final SecurityContextProvider securityContextProvider) {
    return new ClockServices(brokerClient, securityContextProvider, null);
  }

  @Bean
  public ResourceServices resourceServices(
      final BrokerClient brokerClient, final SecurityContextProvider securityContextProvider) {
    return new ResourceServices(brokerClient, securityContextProvider, null);
  }

  @Bean
  public SignalServices signalServices(
      final BrokerClient brokerClient, final SecurityContextProvider securityContextProvider) {
    return new SignalServices(brokerClient, securityContextProvider, null);
  }

  @Bean
  public BatchOperationServices batchOperationServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final BatchOperationSearchClient batchOperationSearchClient) {
    return new BatchOperationServices(
        brokerClient, securityContextProvider, batchOperationSearchClient, null);
  }

  @Bean
  public FormServices formServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final FormSearchClient formSearchClient) {
    return new FormServices(brokerClient, securityContextProvider, formSearchClient, null);
  }

  @Bean
  public MappingRuleServices mappingRuleServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final MappingRuleSearchClient mappingRuleSearchClient) {
    return new MappingRuleServices(
        brokerClient, securityContextProvider, mappingRuleSearchClient, null);
  }

  @Bean
  public MessageSubscriptionServices messageSubscriptionServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final MessageSubscriptionSearchClient messageSubscriptionSearchClient) {
    return new MessageSubscriptionServices(
        brokerClient, securityContextProvider, messageSubscriptionSearchClient, null);
  }

  @Bean
  public SecurityContextProvider securityContextProvider() {
    return new SecurityContextProvider();
  }

  @Bean
  public AuthorizationChecker authorizationChecker(final AuthorizationReader authorizationReader) {
    return new AuthorizationChecker(authorizationReader);
  }

  @Bean
  public ProcessCache processCache(
      final GatewayRestConfiguration configuration,
      final ProcessDefinitionServices processDefinitionServices,
      final BrokerTopologyManager brokerTopologyManager,
      final MeterRegistry meterRegistry) {

    final var cacheConfiguration =
        new ProcessCache.Configuration(
            configuration.getProcessCache().getMaxSize(),
            configuration.getProcessCache().getExpirationIdleMillis());

    return new ProcessCache(
        cacheConfiguration, processDefinitionServices, brokerTopologyManager, meterRegistry);
  }
}
