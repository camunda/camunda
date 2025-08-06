/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.condition.ConditionalOnSecondaryStorageType;
import io.camunda.search.clients.CamundaSearchClients;
import io.camunda.search.clients.auth.ResourceAccessDelegatingController;
import io.camunda.search.clients.impl.NoDBSearchClientsProxy;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.BatchOperationItemReader;
import io.camunda.search.clients.reader.BatchOperationReader;
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
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.clients.reader.SequenceFlowReader;
import io.camunda.search.clients.reader.TenantMemberReader;
import io.camunda.search.clients.reader.TenantReader;
import io.camunda.search.clients.reader.UsageMetricsReader;
import io.camunda.search.clients.reader.UsageMetricsTUReader;
import io.camunda.search.clients.reader.UserReader;
import io.camunda.search.clients.reader.UserTaskReader;
import io.camunda.search.clients.reader.VariableReader;
import io.camunda.search.clients.reader.impl.NoopAuthorizationReader;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;
import io.camunda.security.reader.ResourceAccessController;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
public class SearchClientDatabaseConfiguration {

  @Bean
  @ConditionalOnSecondaryStorageType(DatabaseConfig.ELASTICSEARCH)
  public ElasticsearchSearchClient elasticsearchSearchClient(
      final ConnectConfiguration configuration) {
    final var connector = new ElasticsearchConnector(configuration);
    final var elasticsearch = connector.createClient();
    return new ElasticsearchSearchClient(elasticsearch);
  }

  @Bean
  @ConditionalOnSecondaryStorageType(DatabaseConfig.OPENSEARCH)
  public OpensearchSearchClient opensearchSearchClient(final ConnectConfiguration configuration) {
    final var connector = new OpensearchConnector(configuration);
    final var opensearch = connector.createClient();
    return new OpensearchSearchClient(opensearch);
  }

  @Bean
  @ConditionalOnSecondaryStorageDisabled
  public NoDBSearchClientsProxy noDBSearchClientsProxy() {
    return new NoDBSearchClientsProxy();
  }

  @Bean
  @ConditionalOnSecondaryStorageDisabled
  public AuthorizationReader authorizationReader() {
    return new NoopAuthorizationReader();
  }

  @Bean
  @ConditionalOnSecondaryStorageEnabled
  public CamundaSearchClients searchClients(
      final SearchClientReaders searchClientReaders,
      final List<ResourceAccessController> resourceAccessControllers) {
    return new CamundaSearchClients(
        searchClientReaders,
        new ResourceAccessDelegatingController(resourceAccessControllers),
        null);
  }

  @Bean
  @ConditionalOnSecondaryStorageEnabled
  public SearchClientReaders searchClientReaders(
      final AuthorizationReader authorizationReader,
      final BatchOperationReader batchOperationReader,
      final BatchOperationItemReader batchOperationItemReader,
      final DecisionDefinitionReader decisionDefinitionReader,
      final DecisionInstanceReader decisionInstanceReader,
      final DecisionRequirementsReader decisionRequirementsReader,
      final FlowNodeInstanceReader flowNodeInstanceReader,
      final FormReader formReader,
      final GroupReader groupReader,
      final GroupMemberReader groupMemberReader,
      final IncidentReader incidentReader,
      final JobReader jobReader,
      final MappingRuleReader mappingRuleReader,
      final MessageSubscriptionReader messageSubscriptionReader,
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessDefinitionStatisticsReader processDefinitionFlowNodeStatisticsReader,
      final ProcessInstanceReader processInstanceReader,
      final ProcessInstanceStatisticsReader processInstanceFlowNodeStatisticsReader,
      final RoleReader roleReader,
      final RoleMemberReader roleMemberReader,
      final SequenceFlowReader sequenceFlowReader,
      final TenantReader tenantReader,
      final TenantMemberReader tenantMemberReader,
      final UsageMetricsReader usageMetricsReader,
      final UsageMetricsTUReader usageMetricsTUReader,
      final UserReader userReader,
      final UserTaskReader userTaskReader,
      final VariableReader variableReader) {
    return new SearchClientReaders(
        authorizationReader,
        batchOperationReader,
        batchOperationItemReader,
        decisionDefinitionReader,
        decisionInstanceReader,
        decisionRequirementsReader,
        flowNodeInstanceReader,
        formReader,
        groupReader,
        groupMemberReader,
        incidentReader,
        jobReader,
        mappingRuleReader,
        messageSubscriptionReader,
        processDefinitionReader,
        processDefinitionFlowNodeStatisticsReader,
        processInstanceReader,
        processInstanceFlowNodeStatisticsReader,
        roleReader,
        roleMemberReader,
        sequenceFlowReader,
        tenantReader,
        tenantMemberReader,
        usageMetricsReader,
        usageMetricsTUReader,
        userReader,
        userTaskReader,
        variableReader);
  }
}
