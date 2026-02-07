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
import io.camunda.search.clients.reader.AuditLogDocumentReader;
import io.camunda.search.clients.reader.AuditLogReader;
import io.camunda.search.clients.reader.AuthorizationDocumentReader;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.BatchOperationDocumentReader;
import io.camunda.search.clients.reader.BatchOperationItemDocumentReader;
import io.camunda.search.clients.reader.BatchOperationItemReader;
import io.camunda.search.clients.reader.BatchOperationReader;
import io.camunda.search.clients.reader.ClusterVariableDocumentReader;
import io.camunda.search.clients.reader.ClusterVariableReader;
import io.camunda.search.clients.reader.CorrelatedMessageSubscriptionDocumentReader;
import io.camunda.search.clients.reader.CorrelatedMessageSubscriptionReader;
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
import io.camunda.search.clients.reader.IncidentProcessInstanceStatisticsByDefinitionDocumentReader;
import io.camunda.search.clients.reader.IncidentProcessInstanceStatisticsByDefinitionReader;
import io.camunda.search.clients.reader.IncidentProcessInstanceStatisticsByErrorDocumentReader;
import io.camunda.search.clients.reader.IncidentProcessInstanceStatisticsByErrorReader;
import io.camunda.search.clients.reader.IncidentReader;
import io.camunda.search.clients.reader.JobDocumentReader;
import io.camunda.search.clients.reader.JobMetricsBatchDocumentReader;
import io.camunda.search.clients.reader.JobMetricsBatchReader;
import io.camunda.search.clients.reader.JobReader;
import io.camunda.search.clients.reader.MappingRuleDocumentReader;
import io.camunda.search.clients.reader.MappingRuleReader;
import io.camunda.search.clients.reader.MessageSubscriptionDocumentReader;
import io.camunda.search.clients.reader.MessageSubscriptionReader;
import io.camunda.search.clients.reader.ProcessDefinitionDocumentReader;
import io.camunda.search.clients.reader.ProcessDefinitionInstanceStatisticsDocumentReader;
import io.camunda.search.clients.reader.ProcessDefinitionInstanceStatisticsReader;
import io.camunda.search.clients.reader.ProcessDefinitionInstanceVersionStatisticsDocumentReader;
import io.camunda.search.clients.reader.ProcessDefinitionInstanceVersionStatisticsReader;
import io.camunda.search.clients.reader.ProcessDefinitionMessageSubscriptionStatisticsDocumentReader;
import io.camunda.search.clients.reader.ProcessDefinitionMessageSubscriptionStatisticsReader;
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
import io.camunda.search.clients.reader.UsageMetricsTUDocumentReader;
import io.camunda.search.clients.reader.UsageMetricsTUReader;
import io.camunda.search.clients.reader.UserDocumentReader;
import io.camunda.search.clients.reader.UserReader;
import io.camunda.search.clients.reader.UserTaskDocumentReader;
import io.camunda.search.clients.reader.UserTaskReader;
import io.camunda.search.clients.reader.VariableDocumentReader;
import io.camunda.search.clients.reader.VariableReader;
import io.camunda.search.clients.reader.utils.IncidentErrorHashCodeNormalizer;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.AuthorizationIndex;
import io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.GroupIndex;
import io.camunda.webapps.schema.descriptors.index.MappingRuleIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.webapps.schema.descriptors.index.TenantIndex;
import io.camunda.webapps.schema.descriptors.index.UserIndex;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.CorrelatedMessageSubscriptionTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.JobMetricsBatchTemplate;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
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
  public SearchClientBasedQueryExecutor searchClientBasedQueryExecutor(
      final DocumentBasedSearchClient searchClient, final IndexDescriptors descriptors) {
    final var transformers = ServiceTransformers.newInstance(descriptors);
    return new SearchClientBasedQueryExecutor(searchClient, transformers);
  }

  @Bean
  public AuthorizationReader authorizationReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new AuthorizationDocumentReader(executor, descriptors.get(AuthorizationIndex.class));
  }

  @Bean
  public IncidentErrorHashCodeNormalizer incidentErrorHashCodeNormalizer(
      final IncidentReader incidentReader) {
    return new IncidentErrorHashCodeNormalizer((IncidentDocumentReader) incidentReader);
  }

  @Bean
  public BatchOperationReader batchOperationReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new BatchOperationDocumentReader(
        executor, descriptors.get(BatchOperationTemplate.class));
  }

  @Bean
  public BatchOperationItemReader batchOperationItemReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new BatchOperationItemDocumentReader(executor, descriptors.get(OperationTemplate.class));
  }

  @Bean
  public CorrelatedMessageSubscriptionReader correlatedMessageSubscriptionsReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new CorrelatedMessageSubscriptionDocumentReader(
        executor, descriptors.get(CorrelatedMessageSubscriptionTemplate.class));
  }

  @Bean
  public DecisionDefinitionReader decisionDefinitionReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new DecisionDefinitionDocumentReader(executor, descriptors.get(DecisionIndex.class));
  }

  @Bean
  public DecisionInstanceReader decisionInstanceReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new DecisionInstanceDocumentReader(
        executor, descriptors.get(DecisionInstanceTemplate.class));
  }

  @Bean
  public DecisionRequirementsReader decisionRequirementsReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new DecisionRequirementsDocumentReader(
        executor, descriptors.get(DecisionRequirementsIndex.class));
  }

  @Bean
  public FlowNodeInstanceReader flowNodeInstanceReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new FlowNodeInstanceDocumentReader(
        executor, descriptors.get(FlowNodeInstanceTemplate.class));
  }

  @Bean
  public FormReader formReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new FormDocumentReader(executor, descriptors.get(FormIndex.class));
  }

  @Bean
  public GroupReader groupReader(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptors descriptors,
      final TenantMemberReader tenantMemberReader,
      final RoleMemberReader roleMemberReader) {
    return new GroupDocumentReader(
        executor,
        descriptors.get(GroupIndex.class),
        (TenantMemberDocumentReader) tenantMemberReader,
        (RoleMemberDocumentReader) roleMemberReader);
  }

  @Bean
  public GroupMemberReader groupMemberReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new GroupMemberDocumentReader(executor, descriptors.get(GroupIndex.class));
  }

  @Bean
  public IncidentReader incidentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new IncidentDocumentReader(executor, descriptors.get(IncidentTemplate.class));
  }

  @Bean
  public JobReader jobReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new JobDocumentReader(executor, descriptors.get(JobTemplate.class));
  }

  @Bean
  public JobMetricsBatchReader jobMetricsBatchReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new JobMetricsBatchDocumentReader(
        executor, descriptors.get(JobMetricsBatchTemplate.class));
  }

  @Bean
  public MappingRuleReader mappingRuleReader(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptors descriptors,
      final RoleMemberReader roleMemberReader,
      final TenantMemberReader tenantMemberReader,
      final GroupMemberReader groupMemberReader) {
    return new MappingRuleDocumentReader(
        executor,
        descriptors.get(MappingRuleIndex.class),
        (RoleMemberDocumentReader) roleMemberReader,
        (TenantMemberDocumentReader) tenantMemberReader,
        (GroupMemberDocumentReader) groupMemberReader);
  }

  @Bean
  public MessageSubscriptionReader messageSubscriptionReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new MessageSubscriptionDocumentReader(
        executor, descriptors.get(MessageSubscriptionTemplate.class));
  }

  @Bean
  public ProcessDefinitionMessageSubscriptionStatisticsReader
      processDefinitionMessageSubscriptionStatisticsReader(
          final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new ProcessDefinitionMessageSubscriptionStatisticsDocumentReader(
        executor, descriptors.get(MessageSubscriptionTemplate.class));
  }

  @Bean
  public ProcessDefinitionReader processDefinitionReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new ProcessDefinitionDocumentReader(executor, descriptors.get(ProcessIndex.class));
  }

  @Bean
  public ProcessDefinitionStatisticsReader processDefinitionStatisticsReader(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptors descriptors,
      final IncidentErrorHashCodeNormalizer normalizer) {
    return new ProcessDefinitionStatisticsDocumentReader(
        executor, descriptors.get(ListViewTemplate.class), normalizer);
  }

  @Bean
  public ProcessDefinitionInstanceStatisticsReader processDefinitionInstanceStatisticsReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new ProcessDefinitionInstanceStatisticsDocumentReader(
        executor, descriptors.get(ListViewTemplate.class)) {};
  }

  @Bean
  public ProcessDefinitionInstanceVersionStatisticsReader
      processDefinitionInstanceVersionStatisticsReader(
          final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new ProcessDefinitionInstanceVersionStatisticsDocumentReader(
        executor, descriptors.get(ListViewTemplate.class)) {};
  }

  @Bean
  public ProcessInstanceReader processInstanceReader(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptors descriptors,
      final IncidentErrorHashCodeNormalizer normalizer) {
    return new ProcessInstanceDocumentReader(
        executor, descriptors.get(ListViewTemplate.class), normalizer);
  }

  @Bean
  public ProcessInstanceStatisticsReader processInstanceStatisticsReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new ProcessInstanceStatisticsDocumentReader(
        executor, descriptors.get(ListViewTemplate.class));
  }

  @Bean
  public RoleReader roleReader(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptors descriptors,
      final TenantMemberReader tenantMemberReader) {
    return new RoleDocumentReader(
        executor,
        descriptors.get(RoleIndex.class),
        (TenantMemberDocumentReader) tenantMemberReader);
  }

  @Bean
  public RoleMemberReader roleMemberReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new RoleMemberDocumentReader(executor, descriptors.get(RoleIndex.class));
  }

  @Bean
  public SequenceFlowReader sequenceFlowReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new SequenceFlowDocumentReader(executor, descriptors.get(SequenceFlowTemplate.class));
  }

  @Bean
  public TenantReader tenantReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new TenantDocumentReader(executor, descriptors.get(TenantIndex.class));
  }

  @Bean
  public TenantMemberReader tenantMemberReaderReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new TenantMemberDocumentReader(executor, descriptors.get(TenantIndex.class));
  }

  @Bean
  public UsageMetricsReader usageMetricsReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new UsageMetricsDocumentReader(executor, descriptors.get(UsageMetricTemplate.class));
  }

  @Bean
  public UsageMetricsTUReader usageMetricsTUReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new UsageMetricsTUDocumentReader(executor, descriptors.get(UsageMetricTUTemplate.class));
  }

  @Bean
  public UserReader userReader(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptors descriptors,
      final RoleMemberReader roleMemberReader,
      final TenantMemberReader tenantMemberReader,
      final GroupMemberReader groupMemberReader) {
    return new UserDocumentReader(
        executor,
        descriptors.get(UserIndex.class),
        (RoleMemberDocumentReader) roleMemberReader,
        (TenantMemberDocumentReader) tenantMemberReader,
        (GroupMemberDocumentReader) groupMemberReader);
  }

  @Bean
  public UserTaskReader userTaskReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new UserTaskDocumentReader(executor, descriptors.get(TaskTemplate.class));
  }

  @Bean
  public VariableReader variableReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new VariableDocumentReader(executor, descriptors.get(VariableTemplate.class));
  }

  @Bean
  public ClusterVariableReader clusterVariableReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new ClusterVariableDocumentReader(executor, descriptors.get(ClusterVariableIndex.class));
  }

  @Bean
  public AuditLogReader auditLogReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new AuditLogDocumentReader(executor, descriptors.get(AuditLogTemplate.class));
  }

  @Bean
  public IncidentProcessInstanceStatisticsByErrorReader
      incidentProcessInstanceStatisticsByErrorReader(
          final SearchClientBasedQueryExecutor executor, final IndexDescriptors descriptors) {
    return new IncidentProcessInstanceStatisticsByErrorDocumentReader(
        executor, descriptors.get(IncidentTemplate.class)) {};
  }

  @Bean
  public IncidentProcessInstanceStatisticsByDefinitionReader
      incidentProcessInstanceStatisticsByDefinitionReader(
          final SearchClientBasedQueryExecutor executor,
          final IndexDescriptors descriptors,
          final ProcessCache processCache) {
    return new IncidentProcessInstanceStatisticsByDefinitionDocumentReader(
        executor, descriptors.get(IncidentTemplate.class), processCache);
  }

  @Bean
  public ProcessCache searchClientProcessCache(
      final GatewayRestConfiguration configuration, final SearchClientBasedQueryExecutor executor) {

    final var cacheConfiguration =
        new ProcessCache.Configuration(
            configuration.getProcessCache().getMaxSize(),
            configuration.getProcessCache().getExpirationIdleMillis());

    return new ProcessCache(cacheConfiguration, executor);
  }
}
