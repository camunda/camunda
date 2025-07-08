/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search.reader;

import io.camunda.application.commons.search.condition.SearchEngineEnabledCondition;
import io.camunda.search.clients.DocumentBasedSearchClient;
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
import io.camunda.search.clients.reader.MappingDocumentReader;
import io.camunda.search.clients.reader.MappingReader;
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
import io.camunda.search.clients.reader.SearchClientReaders;
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
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.AuthorizationIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.GroupIndex;
import io.camunda.webapps.schema.descriptors.index.MappingIndex;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.webapps.schema.descriptors.index.TenantIndex;
import io.camunda.webapps.schema.descriptors.index.UserIndex;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
@Conditional(SearchEngineEnabledCondition.class)
public class SearchClientReadersConfiguration {

  @Bean
  public IndexDescriptors indexDescriptors(final ConnectConfiguration connectConfiguration) {
    return new IndexDescriptors(
        connectConfiguration.getIndexPrefix(),
        connectConfiguration.getTypeEnum().isElasticSearch());
  }

  @Bean
  public ServiceTransformers serviceTransformers(final IndexDescriptors indexDescriptors) {
    return ServiceTransformers.newInstance(indexDescriptors);
  }

  @Bean
  public AuthorizationReader authorizationReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new AuthorizationDocumentReader(
        searchClient, transformers, indexDescriptors.get(AuthorizationIndex.class));
  }

  @Bean
  public BatchOperationReader batchOperationReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new BatchOperationDocumentReader(
        searchClient, transformers, indexDescriptors.get(BatchOperationTemplate.class));
  }

  @Bean
  public BatchOperationItemReader batchOperationItemReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new BatchOperationItemDocumentReader(
        searchClient, transformers, indexDescriptors.get(OperationTemplate.class));
  }

  @Bean
  public DecisionDefinitionReader decisionDefinitionReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new DecisionDefinitionDocumentReader(
        searchClient, transformers, indexDescriptors.get(DecisionIndex.class));
  }

  @Bean
  public DecisionInstanceReader decisionInstanceReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new DecisionInstanceDocumentReader(
        searchClient, transformers, indexDescriptors.get(DecisionInstanceTemplate.class));
  }

  @Bean
  public DecisionRequirementsReader decisionRequirementsReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new DecisionRequirementsDocumentReader(
        searchClient, transformers, indexDescriptors.get(DecisionRequirementsIndex.class));
  }

  @Bean
  public FlowNodeInstanceReader flowNodeInstanceReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new FlowNodeInstanceDocumentReader(
        searchClient, transformers, indexDescriptors.get(FlowNodeInstanceTemplate.class));
  }

  @Bean
  public FormReader formReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new FormDocumentReader(
        searchClient, transformers, indexDescriptors.get(FormIndex.class));
  }

  @Bean
  public GroupReader groupReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors,
      final TenantMemberReader tenantMemberReader,
      final RoleMemberReader roleMemberReader) {
    return new GroupDocumentReader(
        searchClient,
        transformers,
        indexDescriptors.get(GroupIndex.class),
        (TenantMemberDocumentReader) tenantMemberReader,
        (RoleMemberDocumentReader) roleMemberReader);
  }

  @Bean
  public GroupMemberReader groupMemberReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new GroupMemberDocumentReader(
        searchClient, transformers, indexDescriptors.get(GroupIndex.class));
  }

  @Bean
  public IncidentReader incidentReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new IncidentDocumentReader(
        searchClient, transformers, indexDescriptors.get(IncidentTemplate.class));
  }

  @Bean
  public JobReader jobReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new JobDocumentReader(
        searchClient, transformers, indexDescriptors.get(JobTemplate.class));
  }

  @Bean
  public MappingReader mappingReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors,
      final RoleMemberReader roleMemberReader,
      final TenantMemberReader tenantMemberReader,
      final GroupMemberReader groupMemberReader) {
    return new MappingDocumentReader(
        searchClient,
        transformers,
        indexDescriptors.get(MappingIndex.class),
        (RoleMemberDocumentReader) roleMemberReader,
        (TenantMemberDocumentReader) tenantMemberReader,
        (GroupMemberDocumentReader) groupMemberReader);
  }

  @Bean
  public MessageSubscriptionReader messageSubscriptionReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new MessageSubscriptionDocumentReader(
        searchClient, transformers, indexDescriptors.get(EventTemplate.class));
  }

  @Bean
  public ProcessDefinitionReader processDefinitionReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new ProcessDefinitionDocumentReader(
        searchClient, transformers, indexDescriptors.get(ProcessIndex.class));
  }

  @Bean
  public ProcessDefinitionStatisticsReader processDefinitionStatisticsReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors,
      final IncidentReader incidentReader) {
    return new ProcessDefinitionStatisticsDocumentReader(
        searchClient,
        transformers,
        indexDescriptors.get(ListViewTemplate.class),
        (IncidentDocumentReader) incidentReader);
  }

  @Bean
  public ProcessInstanceReader processInstanceReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors,
      final IncidentReader incidentReader) {
    return new ProcessInstanceDocumentReader(
        searchClient,
        transformers,
        indexDescriptors.get(ListViewTemplate.class),
        (IncidentDocumentReader) incidentReader);
  }

  @Bean
  public ProcessInstanceStatisticsReader processInstanceStatisticsReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new ProcessInstanceStatisticsDocumentReader(
        searchClient, transformers, indexDescriptors.get(ListViewTemplate.class));
  }

  @Bean
  public RoleReader roleReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors,
      final TenantMemberReader tenantMemberReader) {
    return new RoleDocumentReader(
        searchClient,
        transformers,
        indexDescriptors.get(RoleIndex.class),
        (TenantMemberDocumentReader) tenantMemberReader);
  }

  @Bean
  public RoleMemberReader roleMemberReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new RoleMemberDocumentReader(
        searchClient, transformers, indexDescriptors.get(RoleIndex.class));
  }

  @Bean
  public SequenceFlowReader sequenceFlowReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new SequenceFlowDocumentReader(
        searchClient, transformers, indexDescriptors.get(SequenceFlowTemplate.class));
  }

  @Bean
  public TenantReader tenantReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new TenantDocumentReader(
        searchClient, transformers, indexDescriptors.get(TenantIndex.class));
  }

  @Bean
  public TenantMemberReader tenantMemberReaderReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new TenantMemberDocumentReader(
        searchClient, transformers, indexDescriptors.get(TenantIndex.class));
  }

  @Bean
  public UsageMetricsReader usageMetricsReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new UsageMetricsDocumentReader(
        searchClient, transformers, indexDescriptors.get(MetricIndex.class));
  }

  @Bean
  public UserReader userReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors,
      final RoleMemberReader roleMemberReader,
      final TenantMemberReader tenantMemberReader,
      final GroupMemberReader groupMemberReader) {
    return new UserDocumentReader(
        searchClient,
        transformers,
        indexDescriptors.get(UserIndex.class),
        (RoleMemberDocumentReader) roleMemberReader,
        (TenantMemberDocumentReader) tenantMemberReader,
        (GroupMemberDocumentReader) groupMemberReader);
  }

  @Bean
  public UserTaskReader userTaskReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new UserTaskDocumentReader(
        searchClient, transformers, indexDescriptors.get(TaskTemplate.class));
  }

  @Bean
  public VariableReader variableReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors) {
    return new VariableDocumentReader(
        searchClient, transformers, indexDescriptors.get(VariableTemplate.class));
  }

  @Bean
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
      final MappingReader mappingReader,
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
        mappingReader,
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
        userReader,
        userTaskReader,
        variableReader);
  }
}
