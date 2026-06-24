/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read;

import io.camunda.db.rdbms.read.service.AgentHistoryDbReader;
import io.camunda.db.rdbms.read.service.AgentInstanceDbReader;
import io.camunda.db.rdbms.read.service.AuditLogDbReader;
import io.camunda.db.rdbms.read.service.AuthorizationDbReader;
import io.camunda.db.rdbms.read.service.BatchOperationDbReader;
import io.camunda.db.rdbms.read.service.BatchOperationItemDbReader;
import io.camunda.db.rdbms.read.service.ClusterVariableDbReader;
import io.camunda.db.rdbms.read.service.CorrelatedMessageSubscriptionDbReader;
import io.camunda.db.rdbms.read.service.DecisionDefinitionDbReader;
import io.camunda.db.rdbms.read.service.DecisionInstanceDbReader;
import io.camunda.db.rdbms.read.service.DecisionRequirementsDbReader;
import io.camunda.db.rdbms.read.service.DeployedResourceDbReader;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceDbReader;
import io.camunda.db.rdbms.read.service.FormDbReader;
import io.camunda.db.rdbms.read.service.GlobalListenerDbReader;
import io.camunda.db.rdbms.read.service.GroupDbReader;
import io.camunda.db.rdbms.read.service.GroupMemberDbReader;
import io.camunda.db.rdbms.read.service.HistoryDeletionDbReader;
import io.camunda.db.rdbms.read.service.IncidentDbReader;
import io.camunda.db.rdbms.read.service.IncidentProcessInstanceStatisticsByDefinitionDbReader;
import io.camunda.db.rdbms.read.service.IncidentProcessInstanceStatisticsByErrorDbReader;
import io.camunda.db.rdbms.read.service.JobDbReader;
import io.camunda.db.rdbms.read.service.JobMetricsBatchDbReader;
import io.camunda.db.rdbms.read.service.MappingRuleDbReader;
import io.camunda.db.rdbms.read.service.MessageSubscriptionDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionInstanceStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionInstanceVersionStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionMessageSubscriptionStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceStatisticsDbReader;
import io.camunda.db.rdbms.read.service.RoleDbReader;
import io.camunda.db.rdbms.read.service.RoleMemberDbReader;
import io.camunda.db.rdbms.read.service.SequenceFlowDbReader;
import io.camunda.db.rdbms.read.service.TenantDbReader;
import io.camunda.db.rdbms.read.service.TenantMemberDbReader;
import io.camunda.db.rdbms.read.service.UsageMetricTUDbReader;
import io.camunda.db.rdbms.read.service.UsageMetricsDbReader;
import io.camunda.db.rdbms.read.service.UserDbReader;
import io.camunda.db.rdbms.read.service.UserTaskDbReader;
import io.camunda.db.rdbms.read.service.VariableDbReader;
import io.camunda.db.rdbms.read.service.WaitStateDbReader;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.search.clients.reader.SearchClientReaders;

/**
 * Bundle of all RDBMS read services for a single physical tenant, each backed by that tenant's own
 * datasource. Built from the tenant's {@link RdbmsMapperBundle} via {@link #create}.
 */
public record RdbmsTenantReaders(
    AgentInstanceDbReader agentInstanceReader,
    AgentHistoryDbReader agentHistoryReader,
    AuditLogDbReader auditLogReader,
    AuthorizationDbReader authorizationReader,
    BatchOperationDbReader batchOperationReader,
    BatchOperationItemDbReader batchOperationItemReader,
    ClusterVariableDbReader clusterVariableReader,
    CorrelatedMessageSubscriptionDbReader correlatedMessageSubscriptionReader,
    DecisionDefinitionDbReader decisionDefinitionReader,
    DecisionInstanceDbReader decisionInstanceReader,
    DecisionRequirementsDbReader decisionRequirementsReader,
    DeployedResourceDbReader deployedResourceReader,
    FlowNodeInstanceDbReader flowNodeInstanceReader,
    FormDbReader formReader,
    GlobalListenerDbReader globalListenerReader,
    GroupDbReader groupReader,
    GroupMemberDbReader groupMemberReader,
    HistoryDeletionDbReader historyDeletionReader,
    IncidentDbReader incidentReader,
    IncidentProcessInstanceStatisticsByDefinitionDbReader
        incidentProcessInstanceStatisticsByDefinitionReader,
    IncidentProcessInstanceStatisticsByErrorDbReader incidentProcessInstanceStatisticsByErrorReader,
    JobDbReader jobReader,
    JobMetricsBatchDbReader jobMetricsBatchReader,
    MappingRuleDbReader mappingRuleReader,
    MessageSubscriptionDbReader messageSubscriptionReader,
    ProcessDefinitionDbReader processDefinitionReader,
    ProcessDefinitionInstanceStatisticsDbReader processDefinitionInstanceStatisticsReader,
    ProcessDefinitionInstanceVersionStatisticsDbReader
        processDefinitionInstanceVersionStatisticsReader,
    ProcessDefinitionMessageSubscriptionStatisticsDbReader
        processDefinitionMessageSubscriptionStatisticsReader,
    ProcessDefinitionStatisticsDbReader processDefinitionStatisticsReader,
    ProcessInstanceDbReader processInstanceReader,
    ProcessInstanceStatisticsDbReader processInstanceStatisticsReader,
    RoleDbReader roleReader,
    RoleMemberDbReader roleMemberReader,
    SequenceFlowDbReader sequenceFlowReader,
    TenantDbReader tenantReader,
    TenantMemberDbReader tenantMemberReader,
    UsageMetricsDbReader usageMetricsReader,
    UsageMetricTUDbReader usageMetricsTUReader,
    UserDbReader userReader,
    UserTaskDbReader userTaskReader,
    VariableDbReader variableReader,
    WaitStateDbReader waitStateReader) {

  public static RdbmsTenantReaders create(
      final RdbmsMapperBundle mappers, final RdbmsReaderConfig readerConfig) {
    return new RdbmsTenantReaders(
        new AgentInstanceDbReader(mappers.agentInstanceMapper(), readerConfig),
        new AgentHistoryDbReader(readerConfig),
        new AuditLogDbReader(mappers.auditLogMapper(), readerConfig),
        new AuthorizationDbReader(mappers.authorizationMapper(), readerConfig),
        new BatchOperationDbReader(mappers.batchOperationMapper(), readerConfig),
        new BatchOperationItemDbReader(mappers.batchOperationMapper(), readerConfig),
        new ClusterVariableDbReader(mappers.clusterVariableMapper(), readerConfig),
        new CorrelatedMessageSubscriptionDbReader(
            mappers.correlatedMessageSubscriptionMapper(), readerConfig),
        new DecisionDefinitionDbReader(mappers.decisionDefinitionMapper(), readerConfig),
        new DecisionInstanceDbReader(mappers.decisionInstanceMapper(), readerConfig),
        new DecisionRequirementsDbReader(mappers.decisionRequirementsMapper(), readerConfig),
        new DeployedResourceDbReader(mappers.deployedResourceMapper(), readerConfig),
        new FlowNodeInstanceDbReader(mappers.flowNodeInstanceMapper(), readerConfig),
        new FormDbReader(mappers.formMapper(), readerConfig),
        new GlobalListenerDbReader(mappers.globalListenerMapper(), readerConfig),
        new GroupDbReader(mappers.groupMapper(), readerConfig),
        new GroupMemberDbReader(mappers.groupMapper(), readerConfig),
        new HistoryDeletionDbReader(mappers.historyDeletionMapper()),
        new IncidentDbReader(mappers.incidentMapper(), readerConfig),
        new IncidentProcessInstanceStatisticsByDefinitionDbReader(
            mappers.incidentMapper(), readerConfig),
        new IncidentProcessInstanceStatisticsByErrorDbReader(
            mappers.incidentMapper(), readerConfig),
        new JobDbReader(mappers.jobMapper(), readerConfig),
        new JobMetricsBatchDbReader(mappers.jobMetricsBatchMapper(), readerConfig),
        new MappingRuleDbReader(mappers.mappingRuleMapper(), readerConfig),
        new MessageSubscriptionDbReader(mappers.messageSubscriptionMapper(), readerConfig),
        new ProcessDefinitionDbReader(mappers.processDefinitionMapper(), readerConfig),
        new ProcessDefinitionInstanceStatisticsDbReader(
            mappers.processDefinitionMapper(), readerConfig),
        new ProcessDefinitionInstanceVersionStatisticsDbReader(
            mappers.processDefinitionMapper(), readerConfig),
        new ProcessDefinitionMessageSubscriptionStatisticsDbReader(
            mappers.messageSubscriptionMapper(), readerConfig),
        new ProcessDefinitionStatisticsDbReader(mappers.processDefinitionMapper(), readerConfig),
        new ProcessInstanceDbReader(mappers.processInstanceMapper(), readerConfig),
        new ProcessInstanceStatisticsDbReader(mappers.processInstanceMapper(), readerConfig),
        new RoleDbReader(mappers.roleMapper(), readerConfig),
        new RoleMemberDbReader(mappers.roleMapper(), readerConfig),
        new SequenceFlowDbReader(mappers.sequenceFlowMapper(), readerConfig),
        new TenantDbReader(mappers.tenantMapper(), readerConfig),
        new TenantMemberDbReader(mappers.tenantMapper(), readerConfig),
        new UsageMetricsDbReader(mappers.usageMetricMapper()),
        new UsageMetricTUDbReader(mappers.usageMetricTUMapper()),
        new UserDbReader(mappers.userMapper(), readerConfig),
        new UserTaskDbReader(mappers.userTaskMapper(), readerConfig),
        new VariableDbReader(mappers.variableMapper(), readerConfig),
        new WaitStateDbReader(mappers.waitStateMapper(), readerConfig));
  }

  public SearchClientReaders toSearchClientReaders() {
    return new SearchClientReaders(
        agentInstanceReader,
        agentHistoryReader,
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
        deployedResourceReader,
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
        globalListenerReader,
        waitStateReader);
  }
}
