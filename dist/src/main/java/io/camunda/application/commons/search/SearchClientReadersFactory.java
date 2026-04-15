/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.cache.ProcessCache;
import io.camunda.search.clients.reader.AuditLogDocumentReader;
import io.camunda.search.clients.reader.AuthorizationDocumentReader;
import io.camunda.search.clients.reader.BatchOperationDocumentReader;
import io.camunda.search.clients.reader.BatchOperationItemDocumentReader;
import io.camunda.search.clients.reader.ClusterVariableDocumentReader;
import io.camunda.search.clients.reader.CorrelatedMessageSubscriptionDocumentReader;
import io.camunda.search.clients.reader.DecisionDefinitionDocumentReader;
import io.camunda.search.clients.reader.DecisionInstanceDocumentReader;
import io.camunda.search.clients.reader.DecisionRequirementsDocumentReader;
import io.camunda.search.clients.reader.FlowNodeInstanceDocumentReader;
import io.camunda.search.clients.reader.FormDocumentReader;
import io.camunda.search.clients.reader.GlobalListenerDocumentReader;
import io.camunda.search.clients.reader.GroupDocumentReader;
import io.camunda.search.clients.reader.GroupMemberDocumentReader;
import io.camunda.search.clients.reader.IncidentDocumentReader;
import io.camunda.search.clients.reader.IncidentProcessInstanceStatisticsByDefinitionDocumentReader;
import io.camunda.search.clients.reader.IncidentProcessInstanceStatisticsByErrorDocumentReader;
import io.camunda.search.clients.reader.JobDocumentReader;
import io.camunda.search.clients.reader.JobMetricsBatchDocumentReader;
import io.camunda.search.clients.reader.MappingRuleDocumentReader;
import io.camunda.search.clients.reader.MessageSubscriptionDocumentReader;
import io.camunda.search.clients.reader.ProcessDefinitionDocumentReader;
import io.camunda.search.clients.reader.ProcessDefinitionInstanceStatisticsDocumentReader;
import io.camunda.search.clients.reader.ProcessDefinitionInstanceVersionStatisticsDocumentReader;
import io.camunda.search.clients.reader.ProcessDefinitionMessageSubscriptionStatisticsDocumentReader;
import io.camunda.search.clients.reader.ProcessDefinitionStatisticsDocumentReader;
import io.camunda.search.clients.reader.ProcessInstanceDocumentReader;
import io.camunda.search.clients.reader.ProcessInstanceStatisticsDocumentReader;
import io.camunda.search.clients.reader.RoleDocumentReader;
import io.camunda.search.clients.reader.RoleMemberDocumentReader;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.clients.reader.SequenceFlowDocumentReader;
import io.camunda.search.clients.reader.TenantDocumentReader;
import io.camunda.search.clients.reader.TenantMemberDocumentReader;
import io.camunda.search.clients.reader.UsageMetricsDocumentReader;
import io.camunda.search.clients.reader.UsageMetricsTUDocumentReader;
import io.camunda.search.clients.reader.UserDocumentReader;
import io.camunda.search.clients.reader.UserTaskDocumentReader;
import io.camunda.search.clients.reader.VariableDocumentReader;
import io.camunda.search.clients.reader.utils.IncidentErrorHashCodeNormalizer;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.AuthorizationIndex;
import io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.GlobalListenerIndex;
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

/** Static factory that creates a complete set of ES/OS document readers for one physical tenant. */
public class SearchClientReadersFactory {

  private SearchClientReadersFactory() {}

  /** Creates a complete set of readers for one physical tenant. */
  public static SearchClientReaders create(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptors descriptors,
      final ProcessCache.Configuration processCacheConfig) {

    // --- Phase 1: Simple readers (no cross-reader dependencies) ---
    final var authorizationReader =
        new AuthorizationDocumentReader(executor, descriptors.get(AuthorizationIndex.class));
    final var batchOperationReader =
        new BatchOperationDocumentReader(executor, descriptors.get(BatchOperationTemplate.class));
    final var batchOperationItemReader =
        new BatchOperationItemDocumentReader(executor, descriptors.get(OperationTemplate.class));
    final var correlatedMessageSubscriptionReader =
        new CorrelatedMessageSubscriptionDocumentReader(
            executor, descriptors.get(CorrelatedMessageSubscriptionTemplate.class));
    final var decisionDefinitionReader =
        new DecisionDefinitionDocumentReader(executor, descriptors.get(DecisionIndex.class));
    final var decisionInstanceReader =
        new DecisionInstanceDocumentReader(
            executor, descriptors.get(DecisionInstanceTemplate.class));
    final var decisionRequirementsReader =
        new DecisionRequirementsDocumentReader(
            executor, descriptors.get(DecisionRequirementsIndex.class));
    final var flowNodeInstanceReader =
        new FlowNodeInstanceDocumentReader(
            executor, descriptors.get(FlowNodeInstanceTemplate.class));
    final var formReader = new FormDocumentReader(executor, descriptors.get(FormIndex.class));
    final var incidentReader =
        new IncidentDocumentReader(executor, descriptors.get(IncidentTemplate.class));
    final var jobReader = new JobDocumentReader(executor, descriptors.get(JobTemplate.class));
    final var jobMetricsBatchReader =
        new JobMetricsBatchDocumentReader(executor, descriptors.get(JobMetricsBatchTemplate.class));
    final var messageSubscriptionReader =
        new MessageSubscriptionDocumentReader(
            executor, descriptors.get(MessageSubscriptionTemplate.class));
    final var processDefinitionMessageSubscriptionStatisticsReader =
        new ProcessDefinitionMessageSubscriptionStatisticsDocumentReader(
            executor, descriptors.get(MessageSubscriptionTemplate.class));
    final var processDefinitionReader =
        new ProcessDefinitionDocumentReader(executor, descriptors.get(ProcessIndex.class));
    final var processDefinitionInstanceStatisticsReader =
        new ProcessDefinitionInstanceStatisticsDocumentReader(
            executor, descriptors.get(ListViewTemplate.class)) {};
    final var processDefinitionInstanceVersionStatisticsReader =
        new ProcessDefinitionInstanceVersionStatisticsDocumentReader(
            executor, descriptors.get(ListViewTemplate.class)) {};
    final var processInstanceStatisticsReader =
        new ProcessInstanceStatisticsDocumentReader(
            executor, descriptors.get(ListViewTemplate.class));
    final var sequenceFlowReader =
        new SequenceFlowDocumentReader(executor, descriptors.get(SequenceFlowTemplate.class));
    final var tenantReader = new TenantDocumentReader(executor, descriptors.get(TenantIndex.class));
    final var usageMetricsReader =
        new UsageMetricsDocumentReader(executor, descriptors.get(UsageMetricTemplate.class));
    final var usageMetricsTUReader =
        new UsageMetricsTUDocumentReader(executor, descriptors.get(UsageMetricTUTemplate.class));
    final var userTaskReader =
        new UserTaskDocumentReader(executor, descriptors.get(TaskTemplate.class));
    final var variableReader =
        new VariableDocumentReader(executor, descriptors.get(VariableTemplate.class));
    final var clusterVariableReader =
        new ClusterVariableDocumentReader(executor, descriptors.get(ClusterVariableIndex.class));
    final var auditLogReader =
        new AuditLogDocumentReader(executor, descriptors.get(AuditLogTemplate.class));
    final var globalListenerReader =
        new GlobalListenerDocumentReader(executor, descriptors.get(GlobalListenerIndex.class));

    // --- Phase 2: Base readers needed by composite readers ---
    final var roleMemberReader =
        new RoleMemberDocumentReader(executor, descriptors.get(RoleIndex.class));
    final var tenantMemberReader =
        new TenantMemberDocumentReader(executor, descriptors.get(TenantIndex.class));
    final var groupMemberReader =
        new GroupMemberDocumentReader(executor, descriptors.get(GroupIndex.class));

    // --- Phase 3: Composite readers (depend on Phase 2 readers) ---
    final var groupReader =
        new GroupDocumentReader(
            executor, descriptors.get(GroupIndex.class), tenantMemberReader, roleMemberReader);
    final var roleReader =
        new RoleDocumentReader(executor, descriptors.get(RoleIndex.class), tenantMemberReader);
    final var mappingRuleReader =
        new MappingRuleDocumentReader(
            executor,
            descriptors.get(MappingRuleIndex.class),
            roleMemberReader,
            tenantMemberReader,
            groupMemberReader);
    final var userReader =
        new UserDocumentReader(
            executor,
            descriptors.get(UserIndex.class),
            roleMemberReader,
            tenantMemberReader,
            groupMemberReader);

    // --- Phase 4: Normalizer and cache (depend on Phase 1 readers) ---
    final var incidentErrorHashCodeNormalizer = new IncidentErrorHashCodeNormalizer(incidentReader);
    final var processDefinitionStatisticsReader =
        new ProcessDefinitionStatisticsDocumentReader(
            executor, descriptors.get(ListViewTemplate.class), incidentErrorHashCodeNormalizer);
    final var processInstanceReader =
        new ProcessInstanceDocumentReader(
            executor, descriptors.get(ListViewTemplate.class), incidentErrorHashCodeNormalizer);
    final var processCache = new ProcessCache(processCacheConfig, executor);
    final var incidentProcessInstanceStatisticsByDefinitionReader =
        new IncidentProcessInstanceStatisticsByDefinitionDocumentReader(
            executor, descriptors.get(IncidentTemplate.class), processCache) {};

    // --- Phase 5: Remaining simple reader that uses anonymous subclass ---
    final var incidentProcessInstanceStatisticsByErrorReader =
        new IncidentProcessInstanceStatisticsByErrorDocumentReader(
            executor, descriptors.get(IncidentTemplate.class)) {};

    // --- Assemble ---
    return new SearchClientReaders(
        authorizationReader,
        batchOperationReader,
        batchOperationItemReader,
        correlatedMessageSubscriptionReader,
        decisionDefinitionReader,
        decisionInstanceReader,
        decisionRequirementsReader,
        flowNodeInstanceReader,
        formReader,
        groupReader,
        groupMemberReader,
        incidentReader,
        jobReader,
        jobMetricsBatchReader,
        mappingRuleReader,
        messageSubscriptionReader,
        processDefinitionMessageSubscriptionStatisticsReader,
        processDefinitionReader,
        processDefinitionStatisticsReader,
        processInstanceReader,
        processDefinitionInstanceStatisticsReader,
        processDefinitionInstanceVersionStatisticsReader,
        processInstanceStatisticsReader,
        roleReader,
        roleMemberReader,
        sequenceFlowReader,
        tenantReader,
        tenantMemberReader,
        usageMetricsReader,
        usageMetricsTUReader,
        userReader,
        userTaskReader,
        variableReader,
        clusterVariableReader,
        auditLogReader,
        incidentProcessInstanceStatisticsByErrorReader,
        incidentProcessInstanceStatisticsByDefinitionReader,
        globalListenerReader);
  }
}
