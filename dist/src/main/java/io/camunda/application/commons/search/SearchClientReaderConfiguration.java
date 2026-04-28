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
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.cache.ProcessCache;
import io.camunda.search.clients.reader.AuditLogReader;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.BatchOperationItemReader;
import io.camunda.search.clients.reader.BatchOperationReader;
import io.camunda.search.clients.reader.ClusterVariableReader;
import io.camunda.search.clients.reader.CorrelatedMessageSubscriptionReader;
import io.camunda.search.clients.reader.DecisionDefinitionReader;
import io.camunda.search.clients.reader.DecisionInstanceReader;
import io.camunda.search.clients.reader.DecisionRequirementsReader;
import io.camunda.search.clients.reader.DeployedResourceDocumentReader;
import io.camunda.search.clients.reader.DeployedResourceReader;
import io.camunda.search.clients.reader.FlowNodeInstanceReader;
import io.camunda.search.clients.reader.FormReader;
import io.camunda.search.clients.reader.GlobalListenerReader;
import io.camunda.search.clients.reader.GroupMemberReader;
import io.camunda.search.clients.reader.GroupReader;
import io.camunda.search.clients.reader.IncidentProcessInstanceStatisticsByDefinitionReader;
import io.camunda.search.clients.reader.IncidentProcessInstanceStatisticsByErrorReader;
import io.camunda.search.clients.reader.IncidentReader;
import io.camunda.search.clients.reader.JobMetricsBatchReader;
import io.camunda.search.clients.reader.JobReader;
import io.camunda.search.clients.reader.MappingRuleReader;
import io.camunda.search.clients.reader.MessageSubscriptionReader;
import io.camunda.search.clients.reader.ProcessDefinitionInstanceStatisticsReader;
import io.camunda.search.clients.reader.ProcessDefinitionInstanceVersionStatisticsReader;
import io.camunda.search.clients.reader.ProcessDefinitionMessageSubscriptionStatisticsReader;
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
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.tenant.TenantConnectConfigResolver;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.DeployedResourceIndex;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnSecondaryStorageType({
  SecondaryStorageType.elasticsearch,
  SecondaryStorageType.opensearch
})
public class SearchClientReaderConfiguration {

  @Bean
  public IndexDescriptors indexDescriptors(final ConnectConfiguration connectConfiguration) {
    return new IndexDescriptors(
        connectConfiguration.getIndexPrefix(),
        connectConfiguration.getTypeEnum().isElasticSearch());
  }

  @Bean
  public Map<String, IndexDescriptors> physicalTenantScopedIndexDescriptors(
      final TenantConnectConfigResolver tenantConnectConfigResolver) {
    return tenantConnectConfigResolver.tenantConfigs().entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry ->
                    new IndexDescriptors(
                        entry.getValue().getIndexPrefix(),
                        entry.getValue().getTypeEnum().isElasticSearch())));
  }

  @Bean
  public SearchClientBasedQueryExecutor searchClientBasedQueryExecutor(
      final DocumentBasedSearchClient searchClient, final IndexDescriptors descriptors) {
    final var transformers = ServiceTransformers.newInstance(descriptors);
    return new SearchClientBasedQueryExecutor(searchClient, transformers);
  }

  @Bean
  public SearchClientReaders documentReaders(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptors descriptors,
      final GatewayRestConfiguration config) {
    final var cacheConfig =
        new ProcessCache.Configuration(
            config.getProcessCache().getMaxSize(),
            config.getProcessCache().getExpirationIdleMillis());
    return SearchClientReadersFactory.create(executor, descriptors, cacheConfig);
  }

  @Bean
  public AuthorizationReader authorizationReader(final SearchClientReaders documentReaders) {
    return documentReaders.authorizationReader();
  }

  @Bean
  public BatchOperationReader batchOperationReader(final SearchClientReaders documentReaders) {
    return documentReaders.batchOperationReader();
  }

  @Bean
  public BatchOperationItemReader batchOperationItemReader(
      final SearchClientReaders documentReaders) {
    return documentReaders.batchOperationItemReader();
  }

  @Bean
  public CorrelatedMessageSubscriptionReader correlatedMessageSubscriptionsReader(
      final SearchClientReaders documentReaders) {
    return documentReaders.correlatedMessageSubscriptionReader();
  }

  @Bean
  public DecisionDefinitionReader decisionDefinitionReader(
      final SearchClientReaders documentReaders) {
    return documentReaders.decisionDefinitionReader();
  }

  @Bean
  public DecisionInstanceReader decisionInstanceReader(final SearchClientReaders documentReaders) {
    return documentReaders.decisionInstanceReader();
  }

  @Bean
  public DecisionRequirementsReader decisionRequirementsReader(
      final SearchClientReaders documentReaders) {
    return documentReaders.decisionRequirementsReader();
  }

  @Bean
  public FlowNodeInstanceReader flowNodeInstanceReader(final SearchClientReaders documentReaders) {
    return documentReaders.flowNodeInstanceReader();
  }

  @Bean
  public FormReader formReader(final SearchClientReaders documentReaders) {
    return documentReaders.formReader();
  }

  @Bean
  public GroupReader groupReader(final SearchClientReaders documentReaders) {
    return documentReaders.groupReader();
  }

  @Bean
  public GroupMemberReader groupMemberReader(final SearchClientReaders documentReaders) {
    return documentReaders.groupMemberReader();
  }

  @Bean
  public IncidentReader incidentReader(final SearchClientReaders documentReaders) {
    return documentReaders.incidentReader();
  }

  @Bean
  public JobReader jobReader(final SearchClientReaders documentReaders) {
    return documentReaders.jobReader();
  }

  @Bean
  public JobMetricsBatchReader jobMetricsBatchReader(final SearchClientReaders documentReaders) {
    return documentReaders.jobMetricsBatchReader();
  }

  @Bean
  public MappingRuleReader mappingRuleReader(final SearchClientReaders documentReaders) {
    return documentReaders.mappingRuleReader();
  }

  @Bean
  public MessageSubscriptionReader messageSubscriptionReader(
      final SearchClientReaders documentReaders) {
    return documentReaders.messageSubscriptionReader();
  }

  @Bean
  public ProcessDefinitionMessageSubscriptionStatisticsReader
      processDefinitionMessageSubscriptionStatisticsReader(
          final SearchClientReaders documentReaders) {
    return documentReaders.processDefinitionMessageSubscriptionStatisticsReader();
  }

  @Bean
  public ProcessDefinitionReader processDefinitionReader(
      final SearchClientReaders documentReaders) {
    return documentReaders.processDefinitionReader();
  }

  @Bean
  public ProcessDefinitionStatisticsReader processDefinitionStatisticsReader(
      final SearchClientReaders documentReaders) {
    return documentReaders.processDefinitionStatisticsReader();
  }

  @Bean
  public ProcessDefinitionInstanceStatisticsReader processDefinitionInstanceStatisticsReader(
      final SearchClientReaders documentReaders) {
    return documentReaders.processDefinitionInstanceStatisticsReader();
  }

  @Bean
  public ProcessDefinitionInstanceVersionStatisticsReader
      processDefinitionInstanceVersionStatisticsReader(final SearchClientReaders documentReaders) {
    return documentReaders.processDefinitionInstanceVersionStatisticsReader();
  }

  @Bean
  public ProcessInstanceReader processInstanceReader(final SearchClientReaders documentReaders) {
    return documentReaders.processInstanceReader();
  }

  @Bean
  public ProcessInstanceStatisticsReader processInstanceStatisticsReader(
      final SearchClientReaders documentReaders) {
    return documentReaders.processInstanceStatisticsReader();
  }

  @Bean
  public RoleReader roleReader(final SearchClientReaders documentReaders) {
    return documentReaders.roleReader();
  }

  @Bean
  public RoleMemberReader roleMemberReader(final SearchClientReaders documentReaders) {
    return documentReaders.roleMemberReader();
  }

  @Bean
  public SequenceFlowReader sequenceFlowReader(final SearchClientReaders documentReaders) {
    return documentReaders.sequenceFlowReader();
  }

  @Bean
  public TenantReader tenantReader(final SearchClientReaders documentReaders) {
    return documentReaders.tenantReader();
  }

  @Bean
  public TenantMemberReader tenantMemberReaderReader(final SearchClientReaders documentReaders) {
    return documentReaders.tenantMemberReader();
  }

  @Bean
  public UsageMetricsReader usageMetricsReader(final SearchClientReaders documentReaders) {
    return documentReaders.usageMetricsReader();
  }

  @Bean
  public UsageMetricsTUReader usageMetricsTUReader(final SearchClientReaders documentReaders) {
    return documentReaders.usageMetricsTUReader();
  }

  @Bean
  public UserReader userReader(final SearchClientReaders documentReaders) {
    return documentReaders.userReader();
  }

  @Bean
  public UserTaskReader userTaskReader(final SearchClientReaders documentReaders) {
    return documentReaders.userTaskReader();
  }

  @Bean
  public VariableReader variableReader(final SearchClientReaders documentReaders) {
    return documentReaders.variableReader();
  }

  @Bean
  public ClusterVariableReader clusterVariableReader(final SearchClientReaders documentReaders) {
    return documentReaders.clusterVariableReader();
  }

  @Bean
  public AuditLogReader auditLogReader(final SearchClientReaders documentReaders) {
    return documentReaders.auditLogReader();
  }

  @Bean
  public IncidentProcessInstanceStatisticsByErrorReader
      incidentProcessInstanceStatisticsByErrorReader(final SearchClientReaders documentReaders) {
    return documentReaders.incidentProcessInstanceStatisticsByErrorReader();
  }

  @Bean
  public IncidentProcessInstanceStatisticsByDefinitionReader
      incidentProcessInstanceStatisticsByDefinitionReader(
          final SearchClientReaders documentReaders) {
    return documentReaders.incidentProcessInstanceStatisticsByDefinitionReader();
  }

  @Bean
  public DeployedResourceReader resourceReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new DeployedResourceDocumentReader(
        executor, descriptors.get(DeployedResourceIndex.class));
  }

  @Bean
  public GlobalListenerReader globalListenerReader(final SearchClientReaders documentReaders) {
    return documentReaders.globalListenerReader();
  }
}
