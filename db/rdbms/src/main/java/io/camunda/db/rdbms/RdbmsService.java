/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.read.RdbmsTenantReaders;
import io.camunda.db.rdbms.read.replication.ReplicationLogStatusProvider;
import io.camunda.db.rdbms.read.replication.ReplicationLogStatusProviderFactory;
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
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
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
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.Builder;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;
import io.camunda.db.rdbms.write.RdbmsWriters;
import java.util.function.Consumer;

/**
 * A holder for all rdbms services scoped to a single physical tenant. Each instance is bound to one
 * physical tenant's datasource and is created on demand via {@link RdbmsServiceFactory}; readers,
 * writers and the replication status provider it exposes all operate against that tenant's storage.
 */
public class RdbmsService {

  private final RdbmsWriterFactory rdbmsWriterFactory;
  private final RdbmsTenantReaders tenantReaders;
  private final ReplicationLogStatusProviderFactory replicationLogStatusProviderFactory;

  public RdbmsService(
      final RdbmsWriterFactory rdbmsWriterFactory,
      final RdbmsTenantReaders tenantReaders,
      final ReplicationLogStatusProviderFactory replicationLogStatusProviderFactory) {
    this.rdbmsWriterFactory = rdbmsWriterFactory;
    this.tenantReaders = tenantReaders;
    this.replicationLogStatusProviderFactory = replicationLogStatusProviderFactory;
  }

  public AuthorizationDbReader getAuthorizationReader() {
    return tenantReaders.authorizationReader();
  }

  public AuditLogDbReader getAuditLogReader() {
    return tenantReaders.auditLogReader();
  }

  public DecisionDefinitionDbReader getDecisionDefinitionReader() {
    return tenantReaders.decisionDefinitionReader();
  }

  public DecisionInstanceDbReader getDecisionInstanceReader() {
    return tenantReaders.decisionInstanceReader();
  }

  public DecisionRequirementsDbReader getDecisionRequirementsReader() {
    return tenantReaders.decisionRequirementsReader();
  }

  public FlowNodeInstanceDbReader getFlowNodeInstanceReader() {
    return tenantReaders.flowNodeInstanceReader();
  }

  public GroupDbReader getGroupReader() {
    return tenantReaders.groupReader();
  }

  public GroupMemberDbReader getGroupMemberReader() {
    return tenantReaders.groupMemberReader();
  }

  public IncidentDbReader getIncidentReader() {
    return tenantReaders.incidentReader();
  }

  public ProcessDefinitionDbReader getProcessDefinitionReader() {
    return tenantReaders.processDefinitionReader();
  }

  public ProcessInstanceDbReader getProcessInstanceReader() {
    return tenantReaders.processInstanceReader();
  }

  public TenantDbReader getTenantReader() {
    return tenantReaders.tenantReader();
  }

  public TenantMemberDbReader getTenantMemberReader() {
    return tenantReaders.tenantMemberReader();
  }

  public VariableDbReader getVariableReader() {
    return tenantReaders.variableReader();
  }

  public ClusterVariableDbReader getClusterVariableReader() {
    return tenantReaders.clusterVariableReader();
  }

  public WaitStateDbReader getWaitStateReader() {
    return tenantReaders.waitStateReader();
  }

  public RoleDbReader getRoleReader() {
    return tenantReaders.roleReader();
  }

  public RoleMemberDbReader getRoleMemberReader() {
    return tenantReaders.roleMemberReader();
  }

  public UserDbReader getUserReader() {
    return tenantReaders.userReader();
  }

  public UserTaskDbReader getUserTaskReader() {
    return tenantReaders.userTaskReader();
  }

  public FormDbReader getFormReader() {
    return tenantReaders.formReader();
  }

  public MappingRuleDbReader getMappingRuleReader() {
    return tenantReaders.mappingRuleReader();
  }

  public BatchOperationDbReader getBatchOperationReader() {
    return tenantReaders.batchOperationReader();
  }

  public SequenceFlowDbReader getSequenceFlowReader() {
    return tenantReaders.sequenceFlowReader();
  }

  public UsageMetricsDbReader getUsageMetricReader() {
    return tenantReaders.usageMetricsReader();
  }

  public UsageMetricTUDbReader getUsageMetricTUReader() {
    return tenantReaders.usageMetricsTUReader();
  }

  public BatchOperationItemDbReader getBatchOperationItemReader() {
    return tenantReaders.batchOperationItemReader();
  }

  public JobDbReader getJobReader() {
    return tenantReaders.jobReader();
  }

  public JobMetricsBatchDbReader getJobMetricsBatchDbReader() {
    return tenantReaders.jobMetricsBatchReader();
  }

  public MessageSubscriptionDbReader getMessageSubscriptionReader() {
    return tenantReaders.messageSubscriptionReader();
  }

  public ProcessDefinitionMessageSubscriptionStatisticsDbReader
      getProcessDefinitionMessageSubscriptionStatisticsDbReader() {
    return tenantReaders.processDefinitionMessageSubscriptionStatisticsReader();
  }

  public CorrelatedMessageSubscriptionDbReader getCorrelatedMessageSubscriptionReader() {
    return tenantReaders.correlatedMessageSubscriptionReader();
  }

  public ProcessDefinitionInstanceStatisticsDbReader
      getProcessDefinitionInstanceStatisticsReader() {
    return tenantReaders.processDefinitionInstanceStatisticsReader();
  }

  public ProcessDefinitionInstanceVersionStatisticsDbReader
      getProcessDefinitionInstanceVersionStatisticsReader() {
    return tenantReaders.processDefinitionInstanceVersionStatisticsReader();
  }

  public IncidentProcessInstanceStatisticsByErrorDbReader
      getIncidentProcessInstanceStatisticsByErrorDbReader() {
    return tenantReaders.incidentProcessInstanceStatisticsByErrorReader();
  }

  public IncidentProcessInstanceStatisticsByDefinitionDbReader
      getIncidentProcessInstanceStatisticsByDefinitionReader() {
    return tenantReaders.incidentProcessInstanceStatisticsByDefinitionReader();
  }

  public HistoryDeletionDbReader getHistoryDeletionDbReader() {
    return tenantReaders.historyDeletionReader();
  }

  public AgentInstanceDbReader getAgentInstanceDbReader() {
    return tenantReaders.agentInstanceReader();
  }

  public GlobalListenerDbReader getGlobalListenerDbReader() {
    return tenantReaders.globalListenerReader();
  }

  public DeployedResourceDbReader getResourceDbReader() {
    return tenantReaders.deployedResourceReader();
  }

  public ReplicationLogStatusProvider getReplicationLogStatusProvider() {
    return replicationLogStatusProviderFactory.create();
  }

  public RdbmsWriters createWriter(final long partitionId) {
    return createWriter(new RdbmsWriterConfig.Builder().partitionId((int) partitionId).build());
  }

  public RdbmsWriters createWriter(final RdbmsWriterConfig config) {
    return rdbmsWriterFactory.createWriter(config);
  }

  public RdbmsWriters createWriter(final Consumer<Builder> configBuilder) {
    final RdbmsWriterConfig.Builder builder = new RdbmsWriterConfig.Builder();
    configBuilder.accept(builder);
    return rdbmsWriterFactory.createWriter(builder.build());
  }
}
