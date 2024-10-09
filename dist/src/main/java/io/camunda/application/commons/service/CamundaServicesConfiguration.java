/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.clients.DecisionDefinitionSearchClient;
import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.clients.FormSearchClient;
import io.camunda.search.clients.IncidentSearchClient;
import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.clients.ProcessInstanceSearchClient;
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
      final ActivateJobsHandler<JobActivationResponse> activateJobsHandler) {
    return new JobServices<>(brokerClient, activateJobsHandler, null);
  }

  @Bean
  public DecisionDefinitionServices decisionDefinitionServices(
      final BrokerClient brokerClient,
      final DecisionDefinitionSearchClient decisionDefinitionSearchClient,
      final DecisionRequirementSearchClient decisionRequirementSearchClient) {
    return new DecisionDefinitionServices(
        brokerClient, decisionDefinitionSearchClient, decisionRequirementSearchClient, null);
  }

  @Bean
  public DecisionInstanceServices decisionInstanceServices(
      final BrokerClient brokerClient,
      final DecisionInstanceSearchClient decisionInstanceSearchClient) {
    return new DecisionInstanceServices(brokerClient, decisionInstanceSearchClient, null);
  }

  @Bean
  public ProcessDefinitionServices processDefinitionServices(
      final BrokerClient brokerClient,
      final ProcessDefinitionSearchClient processDefinitionSearchClient) {
    return new ProcessDefinitionServices(brokerClient, processDefinitionSearchClient, null);
  }

  @Bean
  public ProcessInstanceServices processInstanceServices(
      final BrokerClient brokerClient,
      final ProcessInstanceSearchClient processInstanceSearchClient) {
    return new ProcessInstanceServices(brokerClient, processInstanceSearchClient, null);
  }

  @Bean
  public DecisionRequirementsServices decisionRequirementsServices(
      final BrokerClient brokerClient,
      final DecisionRequirementSearchClient decisionRequirementSearchClient) {
    return new DecisionRequirementsServices(brokerClient, decisionRequirementSearchClient, null);
  }

  @Bean
  public FlowNodeInstanceServices flownodeInstanceServices(
      final BrokerClient brokerClient,
      final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient) {
    return new FlowNodeInstanceServices(brokerClient, flowNodeInstanceSearchClient, null);
  }

  @Bean
  public IncidentServices incidentServices(
      final BrokerClient brokerClient, final IncidentSearchClient incidentSearchClient) {
    return new IncidentServices(brokerClient, incidentSearchClient, null);
  }

  @Bean
  public UserServices userServices(
      final BrokerClient brokerClient, final UserSearchClient userSearchClient) {
    return new UserServices(brokerClient, userSearchClient, null);
  }

  @Bean
  public UserTaskServices userTaskServices(
      final BrokerClient brokerClient, final UserTaskSearchClient userTaskSearchClient) {
    return new UserTaskServices(brokerClient, userTaskSearchClient, null);
  }

  @Bean
  public VariableServices variableServices(
      final BrokerClient brokerClient, final VariableSearchClient variableSearchClient) {
    return new VariableServices(brokerClient, variableSearchClient, null);
  }

  @Bean
  public MessageServices messageServices(final BrokerClient brokerClient) {
    return new MessageServices(brokerClient, null);
  }

  @Bean
  public DocumentServices documentServices(final BrokerClient brokerClient) {
    return new DocumentServices(brokerClient, null);
  }

  @Bean
  public AuthorizationServices authorizationServices(
      final BrokerClient brokerClient, final AuthorizationSearchClient authorizationSearchClient) {
    return new AuthorizationServices(brokerClient, authorizationSearchClient, null);
  }

  @Bean
  public ClockServices clockServices(final BrokerClient brokerClient) {
    return new ClockServices(brokerClient, null);
  }

  @Bean
  public ResourceServices resourceServices(final BrokerClient brokerClient) {
    return new ResourceServices(brokerClient, null);
  }

  @Bean
  public ElementInstanceServices elementServices(final BrokerClient brokerClient) {
    return new ElementInstanceServices(brokerClient, null);
  }

  @Bean
  public SignalServices signalServices(final BrokerClient brokerClient) {
    return new SignalServices(brokerClient, null);
  }

  @Bean
  public FormServices formServices(
      final BrokerClient brokerClient, final FormSearchClient formSearchClient) {
    return new FormServices(brokerClient, formSearchClient, null);
  }

  @Bean
  public SearchClients searchClients(final DocumentBasedSearchClient searchClient) {
    return new SearchClients(searchClient);
  }
}
