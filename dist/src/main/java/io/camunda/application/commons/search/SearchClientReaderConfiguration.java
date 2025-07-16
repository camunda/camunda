/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.condition.ConditionalOnSecondaryStorageType;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.reader.AuthorizationDocumentReader;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.BatchOperationDocumentReader;
import io.camunda.search.clients.reader.BatchOperationItemDocumentReader;
import io.camunda.search.clients.reader.BatchOperationItemReader;
import io.camunda.search.clients.reader.BatchOperationReader;
import io.camunda.search.clients.reader.DecisionDefinitionDocumentReader;
import io.camunda.search.clients.reader.DecisionDefinitionReader;
import io.camunda.search.clients.reader.DecisionInstanceDocumentReader;
import io.camunda.search.clients.reader.DecisionInstanceReader;
import io.camunda.search.clients.reader.DecisionRequirementsDocumentReader;
import io.camunda.search.clients.reader.DecisionRequirementsReader;
import io.camunda.search.clients.reader.FlowNodeInstanceDocumentReader;
import io.camunda.search.clients.reader.FlowNodeInstanceReader;
import io.camunda.search.clients.reader.FormDocumentReader;
import io.camunda.search.clients.reader.FormReader;
import io.camunda.search.clients.reader.GroupDocumentReader;
import io.camunda.search.clients.reader.GroupMemberDocumentReader;
import io.camunda.search.clients.reader.GroupMemberReader;
import io.camunda.search.clients.reader.GroupReader;
import io.camunda.search.clients.reader.IncidentDocumentReader;
import io.camunda.search.clients.reader.IncidentReader;
import io.camunda.search.clients.reader.JobDocumentReader;
import io.camunda.search.clients.reader.JobReader;
import io.camunda.search.clients.reader.MappingRuleDocumentReader;
import io.camunda.search.clients.reader.MappingRuleReader;
import io.camunda.search.clients.reader.MessageSubscriptionDocumentReader;
import io.camunda.search.clients.reader.MessageSubscriptionReader;
import io.camunda.search.clients.reader.ProcessDefinitionDocumentReader;
import io.camunda.search.clients.reader.ProcessDefinitionReader;
import io.camunda.search.clients.reader.ProcessDefinitionStatisticsDocumentReader;
import io.camunda.search.clients.reader.ProcessDefinitionStatisticsReader;
import io.camunda.search.clients.reader.ProcessInstanceDocumentReader;
import io.camunda.search.clients.reader.ProcessInstanceReader;
import io.camunda.search.clients.reader.ProcessInstanceStatisticsDocumentReader;
import io.camunda.search.clients.reader.ProcessInstanceStatisticsReader;
import io.camunda.search.clients.reader.RoleDocumentReader;
import io.camunda.search.clients.reader.RoleMemberDocumentReader;
import io.camunda.search.clients.reader.RoleMemberReader;
import io.camunda.search.clients.reader.RoleReader;
import io.camunda.search.clients.reader.SequenceFlowDocumentReader;
import io.camunda.search.clients.reader.SequenceFlowReader;
import io.camunda.search.clients.reader.TenantDocumentReader;
import io.camunda.search.clients.reader.TenantMemberDocumentReader;
import io.camunda.search.clients.reader.TenantMemberReader;
import io.camunda.search.clients.reader.TenantReader;
import io.camunda.search.clients.reader.UsageMetricsDocumentReader;
import io.camunda.search.clients.reader.UsageMetricsReader;
import io.camunda.search.clients.reader.UserDocumentReader;
import io.camunda.search.clients.reader.UserReader;
import io.camunda.search.clients.reader.UserTaskDocumentReader;
import io.camunda.search.clients.reader.UserTaskReader;
import io.camunda.search.clients.reader.VariableDocumentReader;
import io.camunda.search.clients.reader.VariableReader;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
@ConditionalOnSecondaryStorageType({DatabaseConfig.ELASTICSEARCH, DatabaseConfig.OPENSEARCH})
public class SearchClientReaderConfiguration {

  @Bean
  public SearchClientBasedQueryExecutor searchClientBasedQueryExecutor(
      final DocumentBasedSearchClient searchClient,
      final ConnectConfiguration connectConfiguration) {
    final var descriptors =
        new IndexDescriptors(
            connectConfiguration.getIndexPrefix(),
            connectConfiguration.getTypeEnum().isElasticSearch());
    final var transformers = ServiceTransformers.newInstance(descriptors);
    return new SearchClientBasedQueryExecutor(searchClient, transformers);
  }

  @Bean
  public AuthorizationReader authorizationReader(final SearchClientBasedQueryExecutor executor) {
    return new AuthorizationDocumentReader(executor);
  }

  @Bean
  public BatchOperationReader batchOperationReader(final SearchClientBasedQueryExecutor executor) {
    return new BatchOperationDocumentReader(executor);
  }

  @Bean
  public BatchOperationItemReader batchOperationItemReader(
      final SearchClientBasedQueryExecutor executor) {
    return new BatchOperationItemDocumentReader(executor);
  }

  @Bean
  public DecisionDefinitionReader decisionDefinitionReader(
      final SearchClientBasedQueryExecutor executor) {
    return new DecisionDefinitionDocumentReader(executor);
  }

  @Bean
  public DecisionInstanceReader decisionInstanceReader(
      final SearchClientBasedQueryExecutor executor) {
    return new DecisionInstanceDocumentReader(executor);
  }

  @Bean
  public DecisionRequirementsReader decisionRequirementsReader(
      final SearchClientBasedQueryExecutor executor) {
    return new DecisionRequirementsDocumentReader(executor);
  }

  @Bean
  public FlowNodeInstanceReader flowNodeInstanceReader(
      final SearchClientBasedQueryExecutor executor) {
    return new FlowNodeInstanceDocumentReader(executor);
  }

  @Bean
  public FormReader formReader(final SearchClientBasedQueryExecutor executor) {
    return new FormDocumentReader(executor);
  }

  @Bean
  public GroupReader groupReader(
      final SearchClientBasedQueryExecutor executor,
      final TenantMemberReader tenantMemberReader,
      final RoleMemberReader roleMemberReader) {
    return new GroupDocumentReader(
        executor,
        (TenantMemberDocumentReader) tenantMemberReader,
        (RoleMemberDocumentReader) roleMemberReader);
  }

  @Bean
  public GroupMemberReader groupMemberReader(final SearchClientBasedQueryExecutor executor) {
    return new GroupMemberDocumentReader(executor);
  }

  @Bean
  public IncidentReader incidentReader(final SearchClientBasedQueryExecutor executor) {
    return new IncidentDocumentReader(executor);
  }

  @Bean
  public JobReader jobReader(final SearchClientBasedQueryExecutor executor) {
    return new JobDocumentReader(executor);
  }

  @Bean
  public MappingRuleReader mappingRuleReader(
      final SearchClientBasedQueryExecutor executor,
      final RoleMemberReader roleMemberReader,
      final TenantMemberReader tenantMemberReader,
      final GroupMemberReader groupMemberReader) {
    return new MappingRuleDocumentReader(
        executor,
        (RoleMemberDocumentReader) roleMemberReader,
        (TenantMemberDocumentReader) tenantMemberReader,
        (GroupMemberDocumentReader) groupMemberReader);
  }

  @Bean
  public MessageSubscriptionReader messageSubscriptionReader(
      final SearchClientBasedQueryExecutor executor) {
    return new MessageSubscriptionDocumentReader(executor);
  }

  @Bean
  public ProcessDefinitionReader processDefinitionReader(
      final SearchClientBasedQueryExecutor executor) {
    return new ProcessDefinitionDocumentReader(executor);
  }

  @Bean
  public ProcessDefinitionStatisticsReader processDefinitionStatisticsReader(
      final SearchClientBasedQueryExecutor executor, final IncidentReader incidentReader) {
    return new ProcessDefinitionStatisticsDocumentReader(
        executor, (IncidentDocumentReader) incidentReader);
  }

  @Bean
  public ProcessInstanceReader processInstanceReader(
      final SearchClientBasedQueryExecutor executor, final IncidentReader incidentReader) {
    return new ProcessInstanceDocumentReader(executor, (IncidentDocumentReader) incidentReader);
  }

  @Bean
  public ProcessInstanceStatisticsReader processInstanceStatisticsReader(
      final SearchClientBasedQueryExecutor executor) {
    return new ProcessInstanceStatisticsDocumentReader(executor);
  }

  @Bean
  public RoleReader roleReader(
      final SearchClientBasedQueryExecutor executor, final TenantMemberReader tenantMemberReader) {
    return new RoleDocumentReader(executor, (TenantMemberDocumentReader) tenantMemberReader);
  }

  @Bean
  public RoleMemberReader roleMemberReader(final SearchClientBasedQueryExecutor executor) {
    return new RoleMemberDocumentReader(executor);
  }

  @Bean
  public SequenceFlowReader sequenceFlowReader(final SearchClientBasedQueryExecutor executor) {
    return new SequenceFlowDocumentReader(executor);
  }

  @Bean
  public TenantReader tenantReader(final SearchClientBasedQueryExecutor executor) {
    return new TenantDocumentReader(executor);
  }

  @Bean
  public TenantMemberReader tenantMemberReaderReader(
      final SearchClientBasedQueryExecutor executor) {
    return new TenantMemberDocumentReader(executor);
  }

  @Bean
  public UsageMetricsReader usageMetricsReader(final SearchClientBasedQueryExecutor executor) {
    return new UsageMetricsDocumentReader(executor);
  }

  @Bean
  public UserReader userReader(
      final SearchClientBasedQueryExecutor executor,
      final RoleMemberReader roleMemberReader,
      final TenantMemberReader tenantMemberReader,
      final GroupMemberReader groupMemberReader) {
    return new UserDocumentReader(
        executor,
        (RoleMemberDocumentReader) roleMemberReader,
        (TenantMemberDocumentReader) tenantMemberReader,
        (GroupMemberDocumentReader) groupMemberReader);
  }

  @Bean
  public UserTaskReader userTaskReader(final SearchClientBasedQueryExecutor executor) {
    return new UserTaskDocumentReader(executor);
  }

  @Bean
  public VariableReader variableReader(final SearchClientBasedQueryExecutor executor) {
    return new VariableDocumentReader(executor);
  }
}
