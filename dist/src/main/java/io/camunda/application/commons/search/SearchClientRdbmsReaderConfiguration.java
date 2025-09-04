/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.BatchOperationItemReader;
import io.camunda.search.clients.reader.BatchOperationReader;
import io.camunda.search.clients.reader.CorrelatedMessagesReader;
import io.camunda.search.clients.reader.DecisionDefinitionReader;
import io.camunda.search.clients.reader.DecisionInstanceReader;
import io.camunda.search.clients.reader.DecisionRequirementsReader;
import io.camunda.search.clients.reader.FlowNodeInstanceReader;
import io.camunda.search.clients.reader.FormReader;
import io.camunda.search.clients.reader.GroupMemberReader;
import io.camunda.search.clients.reader.GroupReader;
import io.camunda.search.clients.reader.IncidentReader;
import io.camunda.search.clients.reader.JobReader;
import io.camunda.search.clients.reader.MappingRuleReader;
import io.camunda.search.clients.reader.MessageSubscriptionReader;
import io.camunda.search.clients.reader.ProcessDefinitionReader;
import io.camunda.search.clients.reader.ProcessDefinitionStatisticsReader;
import io.camunda.search.clients.reader.ProcessInstanceReader;
import io.camunda.search.clients.reader.ProcessInstanceStatisticsReader;
import io.camunda.search.clients.reader.RoleMemberReader;
import io.camunda.search.clients.reader.RoleReader;
import io.camunda.search.clients.reader.SequenceFlowReader;
import io.camunda.search.clients.reader.TenantMemberReader;
import io.camunda.search.clients.reader.TenantReader;
import io.camunda.search.clients.reader.UsageMetricsReader;
import io.camunda.search.clients.reader.UsageMetricsTUReader;
import io.camunda.search.clients.reader.UserReader;
import io.camunda.search.clients.reader.UserTaskReader;
import io.camunda.search.clients.reader.VariableReader;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
@ConditionalOnSecondaryStorageType(SecondaryStorageType.rdbms)
public class SearchClientRdbmsReaderConfiguration {

  @Bean
  public AuthorizationReader authorizationReader(final RdbmsService rdbmsService) {
    return rdbmsService.getAuthorizationReader();
  }

  @Bean
  public BatchOperationReader batchOperationReader(final RdbmsService rdbmsService) {
    return rdbmsService.getBatchOperationReader();
  }

  @Bean
  public BatchOperationItemReader batchOperationItemReader(final RdbmsService rdbmsService) {
    return rdbmsService.getBatchOperationItemReader();
  }

  @Bean
  public DecisionDefinitionReader decisionDefinitionReader(final RdbmsService rdbmsService) {
    return rdbmsService.getDecisionDefinitionReader();
  }

  @Bean
  public DecisionInstanceReader decisionInstanceReader(final RdbmsService rdbmsService) {
    return rdbmsService.getDecisionInstanceReader();
  }

  @Bean
  public DecisionRequirementsReader decisionRequirementsReader(final RdbmsService rdbmsService) {
    return rdbmsService.getDecisionRequirementsReader();
  }

  @Bean
  public FlowNodeInstanceReader flowNodeInstanceReader(final RdbmsService rdbmsService) {
    return rdbmsService.getFlowNodeInstanceReader();
  }

  @Bean
  public FormReader formReader(final RdbmsService rdbmsService) {
    return rdbmsService.getFormReader();
  }

  @Bean
  public GroupReader groupReader(final RdbmsService rdbmsService) {
    return rdbmsService.getGroupReader();
  }

  @Bean
  public GroupMemberReader groupMemberReader(final RdbmsService rdbmsService) {
    return rdbmsService.getGroupMemberReader();
  }

  @Bean
  public IncidentReader incidentReader(final RdbmsService rdbmsService) {
    return rdbmsService.getIncidentReader();
  }

  @Bean
  public JobReader jobReader(final RdbmsService rdbmsService) {
    return rdbmsService.getJobReader();
  }

  @Bean
  public MappingRuleReader mappingRuleReader(final RdbmsService rdbmsService) {
    return rdbmsService.getMappingRuleReader();
  }

  @Bean
  public MessageSubscriptionReader messageSubscriptionReader(final RdbmsService rdbmsService) {
    return rdbmsService.getMessageSubscriptionReader();
  }

  @Bean
  public CorrelatedMessagesReader correlatedMessagesReader(final RdbmsService rdbmsService) {
    return rdbmsService.getCorrelatedMessagesReader();
  }

  @Bean
  public ProcessDefinitionReader processDefinitionReader(final RdbmsService rdbmsService) {
    return rdbmsService.getProcessDefinitionReader();
  }

  @Bean
  public ProcessDefinitionStatisticsReader processDefinitionStatisticsReader(final RdbmsService rdbmsService) {
    return rdbmsService.getProcessDefinitionStatisticsReader();
  }

  @Bean
  public ProcessInstanceReader processInstanceReader(final RdbmsService rdbmsService) {
    return rdbmsService.getProcessInstanceReader();
  }

  @Bean
  public ProcessInstanceStatisticsReader processInstanceStatisticsReader(final RdbmsService rdbmsService) {
    return rdbmsService.getProcessInstanceStatisticsReader();
  }

  @Bean
  public RoleReader roleReader(final RdbmsService rdbmsService) {
    return rdbmsService.getRoleReader();
  }

  @Bean
  public RoleMemberReader roleMemberReader(final RdbmsService rdbmsService) {
    return rdbmsService.getRoleMemberReader();
  }

  @Bean
  public SequenceFlowReader sequenceFlowReader(final RdbmsService rdbmsService) {
    return rdbmsService.getSequenceFlowReader();
  }

  @Bean
  public TenantReader tenantReader(final RdbmsService rdbmsService) {
    return rdbmsService.getTenantReader();
  }

  @Bean
  public TenantMemberReader tenantMemberReader(final RdbmsService rdbmsService) {
    return rdbmsService.getTenantMemberReader();
  }

  @Bean
  public UsageMetricsReader usageMetricsReader(final RdbmsService rdbmsService) {
    return rdbmsService.getUsageMetricsReader();
  }

  @Bean
  public UsageMetricsTUReader usageMetricsTUReader(final RdbmsService rdbmsService) {
    return rdbmsService.getUsageMetricTUDbReader();
  }

  @Bean
  public UserReader userReader(final RdbmsService rdbmsService) {
    return rdbmsService.getUserReader();
  }

  @Bean
  public UserTaskReader userTaskReader(final RdbmsService rdbmsService) {
    return rdbmsService.getUserTaskReader();
  }

  @Bean
  public VariableReader variableReader(final RdbmsService rdbmsService) {
    return rdbmsService.getVariableReader();
  }
}