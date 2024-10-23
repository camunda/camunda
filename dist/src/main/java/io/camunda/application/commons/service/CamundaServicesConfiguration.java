/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import io.camunda.application.commons.service.ServiceSecurityConfiguration.ServiceSecurityProperties;
import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.clients.DecisionDefinitionSearchClient;
import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.clients.FormSearchClient;
import io.camunda.search.clients.IncidentSearchClient;
import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.RoleSearchClient;
import io.camunda.search.clients.SearchClients;
import io.camunda.search.clients.UserSearchClient;
import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.clients.VariableSearchClient;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.ClockServices;
import io.camunda.service.DecisionDefinitionServices;
import io.camunda.service.DecisionInstanceServices;
import io.camunda.service.DecisionRequirementsServices;
import io.camunda.service.DocumentServices;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.FlowNodeInstanceServices;
import io.camunda.service.FormServices;
import io.camunda.service.IncidentServices;
import io.camunda.service.JobServices;
import io.camunda.service.MessageServices;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ResourceServices;
import io.camunda.service.RoleServices;
import io.camunda.service.SignalServices;
import io.camunda.service.UserServices;
import io.camunda.service.UserTaskServices;
import io.camunda.service.VariableServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResponse;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
public class CamundaServicesConfiguration {

  @Bean
  public JobServices<JobActivationResponse> jobServices(
      final BrokerClient brokerClient,
      final ServiceSecurityProperties securityConfiguration,
      final ActivateJobsHandler<JobActivationResponse> activateJobsHandler) {
    return new JobServices<>(brokerClient, securityConfiguration, activateJobsHandler, null);
  }

  @Bean
  public DecisionDefinitionServices decisionDefinitionServices(
      final BrokerClient brokerClient,
      final ServiceSecurityProperties securityConfiguration,
      final DecisionDefinitionSearchClient decisionDefinitionSearchClient,
      final DecisionRequirementSearchClient decisionRequirementSearchClient) {
    return new DecisionDefinitionServices(
        brokerClient,
        securityConfiguration,
        decisionDefinitionSearchClient,
        decisionRequirementSearchClient,
        null);
  }

  @Bean
  public DecisionInstanceServices decisionInstanceServices(
      final BrokerClient brokerClient,
      final ServiceSecurityProperties securityConfiguration,
      final DecisionInstanceSearchClient decisionInstanceSearchClient) {
    return new DecisionInstanceServices(
        brokerClient, securityConfiguration, decisionInstanceSearchClient, null);
  }

  @Bean
  public ProcessDefinitionServices processDefinitionServices(
      final BrokerClient brokerClient,
      final ServiceSecurityProperties securityConfiguration,
      final ProcessDefinitionSearchClient processDefinitionSearchClient) {
    return new ProcessDefinitionServices(
        brokerClient, securityConfiguration, processDefinitionSearchClient, null);
  }

  @Bean
  public ProcessInstanceServices processInstanceServices(
      final BrokerClient brokerClient,
      final ServiceSecurityProperties securityConfiguration,
      final ProcessInstanceSearchClient processInstanceSearchClient) {
    return new ProcessInstanceServices(
        brokerClient, securityConfiguration, processInstanceSearchClient, null);
  }

  @Bean
  public DecisionRequirementsServices decisionRequirementsServices(
      final BrokerClient brokerClient,
      final ServiceSecurityProperties securityConfiguration,
      final DecisionRequirementSearchClient decisionRequirementSearchClient) {
    return new DecisionRequirementsServices(
        brokerClient, securityConfiguration, decisionRequirementSearchClient, null);
  }

  @Bean
  public FlowNodeInstanceServices flownodeInstanceServices(
      final BrokerClient brokerClient,
      final ServiceSecurityProperties securityConfiguration,
      final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient) {
    return new FlowNodeInstanceServices(
        brokerClient, securityConfiguration, flowNodeInstanceSearchClient, null);
  }

  @Bean
  public IncidentServices incidentServices(
      final BrokerClient brokerClient,
      final ServiceSecurityProperties securityConfiguration,
      final IncidentSearchClient incidentSearchClient) {
    return new IncidentServices(brokerClient, securityConfiguration, incidentSearchClient, null);
  }

  @Bean
  public RoleServices roleServices(
      final BrokerClient brokerClient,
      final ServiceSecurityProperties securityConfiguration,
      final RoleSearchClient roleSearchClient) {
    return new RoleServices(brokerClient, securityConfiguration, roleSearchClient, null);
  }

  @Bean
  public UserServices userServices(
      final BrokerClient brokerClient,
      final ServiceSecurityProperties securityConfiguration,
      final UserSearchClient userSearchClient) {
    return new UserServices(brokerClient, securityConfiguration, userSearchClient, null);
  }

  @Bean
  public UserTaskServices userTaskServices(
      final BrokerClient brokerClient,
      final ServiceSecurityProperties securityConfiguration,
      final UserTaskSearchClient userTaskSearchClient) {
    return new UserTaskServices(brokerClient, securityConfiguration, userTaskSearchClient, null);
  }

  @Bean
  public VariableServices variableServices(
      final BrokerClient brokerClient,
      final ServiceSecurityProperties securityConfiguration,
      final VariableSearchClient variableSearchClient) {
    return new VariableServices(brokerClient, securityConfiguration, variableSearchClient, null);
  }

  @Bean
  public MessageServices messageServices(
      final BrokerClient brokerClient, final ServiceSecurityProperties securityConfiguration) {
    return new MessageServices(brokerClient, securityConfiguration, null);
  }

  @Bean
  public DocumentServices documentServices(
      final BrokerClient brokerClient, final ServiceSecurityProperties securityConfiguration) {
    return new DocumentServices(brokerClient, securityConfiguration, null);
  }

  @Bean
  public AuthorizationServices authorizationServices(
      final BrokerClient brokerClient,
      final ServiceSecurityProperties securityConfiguration,
      final AuthorizationSearchClient authorizationSearchClient) {
    return new AuthorizationServices(
        brokerClient, securityConfiguration, authorizationSearchClient, null);
  }

  @Bean
  public ClockServices clockServices(
      final BrokerClient brokerClient, final ServiceSecurityProperties securityConfiguration) {
    return new ClockServices(brokerClient, securityConfiguration, null);
  }

  @Bean
  public ResourceServices resourceServices(
      final BrokerClient brokerClient, final ServiceSecurityProperties securityConfiguration) {
    return new ResourceServices(brokerClient, securityConfiguration, null);
  }

  @Bean
  public ElementInstanceServices elementServices(
      final BrokerClient brokerClient, final ServiceSecurityProperties securityConfiguration) {
    return new ElementInstanceServices(brokerClient, securityConfiguration, null);
  }

  @Bean
  public SignalServices signalServices(
      final BrokerClient brokerClient, final ServiceSecurityProperties securityConfiguration) {
    return new SignalServices(brokerClient, securityConfiguration, null);
  }

  @Bean
  public FormServices formServices(
      final BrokerClient brokerClient,
      final ServiceSecurityProperties securityConfiguration,
      final FormSearchClient formSearchClient) {
    return new FormServices(brokerClient, securityConfiguration, formSearchClient, null);
  }
}
