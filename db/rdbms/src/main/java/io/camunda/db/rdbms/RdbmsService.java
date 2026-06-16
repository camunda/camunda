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
import java.util.Map;
import java.util.function.Consumer;

/**
 * A holder for all rdbms services. Reads are served per physical tenant: each {@code getXxxReader}
 * accessor takes a {@code physicalTenantId} and returns the reader backed by that tenant's own
 * datasource.
 */
public class RdbmsService {

  private final RdbmsWriterFactory rdbmsWriterFactory;
  private final Map<String, RdbmsTenantReaders> tenantReaders;
  private final ReplicationLogStatusProviderFactory replicationLogStatusProviderFactory;

  public RdbmsService(
      final RdbmsWriterFactory rdbmsWriterFactory,
      final Map<String, RdbmsTenantReaders> tenantReaders,
      final ReplicationLogStatusProviderFactory replicationLogStatusProviderFactory) {
    this.rdbmsWriterFactory = rdbmsWriterFactory;
    this.tenantReaders = Map.copyOf(tenantReaders);
    this.replicationLogStatusProviderFactory = replicationLogStatusProviderFactory;
  }

  private RdbmsTenantReaders readers(final String physicalTenantId) {
    final var readers = tenantReaders.get(physicalTenantId);
    if (readers == null) {
      throw new IllegalArgumentException(
          "No RDBMS readers for physical tenant '%s'; known physical tenants: %s"
              .formatted(physicalTenantId, tenantReaders.keySet()));
    }
    return readers;
  }

  public AuthorizationDbReader getAuthorizationReader(final String physicalTenantId) {
    return readers(physicalTenantId).authorizationReader();
  }

  public AuditLogDbReader getAuditLogReader(final String physicalTenantId) {
    return readers(physicalTenantId).auditLogReader();
  }

  public DecisionDefinitionDbReader getDecisionDefinitionReader(final String physicalTenantId) {
    return readers(physicalTenantId).decisionDefinitionReader();
  }

  public DecisionInstanceDbReader getDecisionInstanceReader(final String physicalTenantId) {
    return readers(physicalTenantId).decisionInstanceReader();
  }

  public DecisionRequirementsDbReader getDecisionRequirementsReader(final String physicalTenantId) {
    return readers(physicalTenantId).decisionRequirementsReader();
  }

  public FlowNodeInstanceDbReader getFlowNodeInstanceReader(final String physicalTenantId) {
    return readers(physicalTenantId).flowNodeInstanceReader();
  }

  public GroupDbReader getGroupReader(final String physicalTenantId) {
    return readers(physicalTenantId).groupReader();
  }

  public GroupMemberDbReader getGroupMemberReader(final String physicalTenantId) {
    return readers(physicalTenantId).groupMemberReader();
  }

  public IncidentDbReader getIncidentReader(final String physicalTenantId) {
    return readers(physicalTenantId).incidentReader();
  }

  public ProcessDefinitionDbReader getProcessDefinitionReader(final String physicalTenantId) {
    return readers(physicalTenantId).processDefinitionReader();
  }

  public ProcessInstanceDbReader getProcessInstanceReader(final String physicalTenantId) {
    return readers(physicalTenantId).processInstanceReader();
  }

  public TenantDbReader getTenantReader(final String physicalTenantId) {
    return readers(physicalTenantId).tenantReader();
  }

  public TenantMemberDbReader getTenantMemberReader(final String physicalTenantId) {
    return readers(physicalTenantId).tenantMemberReader();
  }

  public VariableDbReader getVariableReader(final String physicalTenantId) {
    return readers(physicalTenantId).variableReader();
  }

  public ClusterVariableDbReader getClusterVariableReader(final String physicalTenantId) {
    return readers(physicalTenantId).clusterVariableReader();
  }

  public WaitStateDbReader getWaitStateReader(final String physicalTenantId) {
    return readers(physicalTenantId).waitStateReader();
  }

  public RoleDbReader getRoleReader(final String physicalTenantId) {
    return readers(physicalTenantId).roleReader();
  }

  public RoleMemberDbReader getRoleMemberReader(final String physicalTenantId) {
    return readers(physicalTenantId).roleMemberReader();
  }

  public UserDbReader getUserReader(final String physicalTenantId) {
    return readers(physicalTenantId).userReader();
  }

  public UserTaskDbReader getUserTaskReader(final String physicalTenantId) {
    return readers(physicalTenantId).userTaskReader();
  }

  public FormDbReader getFormReader(final String physicalTenantId) {
    return readers(physicalTenantId).formReader();
  }

  public MappingRuleDbReader getMappingRuleReader(final String physicalTenantId) {
    return readers(physicalTenantId).mappingRuleReader();
  }

  public BatchOperationDbReader getBatchOperationReader(final String physicalTenantId) {
    return readers(physicalTenantId).batchOperationReader();
  }

  public SequenceFlowDbReader getSequenceFlowReader(final String physicalTenantId) {
    return readers(physicalTenantId).sequenceFlowReader();
  }

  public UsageMetricsDbReader getUsageMetricReader(final String physicalTenantId) {
    return readers(physicalTenantId).usageMetricsReader();
  }

  public UsageMetricTUDbReader getUsageMetricTUReader(final String physicalTenantId) {
    return readers(physicalTenantId).usageMetricsTUReader();
  }

  public BatchOperationItemDbReader getBatchOperationItemReader(final String physicalTenantId) {
    return readers(physicalTenantId).batchOperationItemReader();
  }

  public JobDbReader getJobReader(final String physicalTenantId) {
    return readers(physicalTenantId).jobReader();
  }

  public JobMetricsBatchDbReader getJobMetricsBatchDbReader(final String physicalTenantId) {
    return readers(physicalTenantId).jobMetricsBatchReader();
  }

  public MessageSubscriptionDbReader getMessageSubscriptionReader(final String physicalTenantId) {
    return readers(physicalTenantId).messageSubscriptionReader();
  }

  public ProcessDefinitionMessageSubscriptionStatisticsDbReader
      getProcessDefinitionMessageSubscriptionStatisticsDbReader(final String physicalTenantId) {
    return readers(physicalTenantId).processDefinitionMessageSubscriptionStatisticsReader();
  }

  public CorrelatedMessageSubscriptionDbReader getCorrelatedMessageSubscriptionReader(
      final String physicalTenantId) {
    return readers(physicalTenantId).correlatedMessageSubscriptionReader();
  }

  public ProcessDefinitionInstanceStatisticsDbReader getProcessDefinitionInstanceStatisticsReader(
      final String physicalTenantId) {
    return readers(physicalTenantId).processDefinitionInstanceStatisticsReader();
  }

  public ProcessDefinitionInstanceVersionStatisticsDbReader
      getProcessDefinitionInstanceVersionStatisticsReader(final String physicalTenantId) {
    return readers(physicalTenantId).processDefinitionInstanceVersionStatisticsReader();
  }

  public IncidentProcessInstanceStatisticsByErrorDbReader
      getIncidentProcessInstanceStatisticsByErrorDbReader(final String physicalTenantId) {
    return readers(physicalTenantId).incidentProcessInstanceStatisticsByErrorReader();
  }

  public IncidentProcessInstanceStatisticsByDefinitionDbReader
      getIncidentProcessInstanceStatisticsByDefinitionReader(final String physicalTenantId) {
    return readers(physicalTenantId).incidentProcessInstanceStatisticsByDefinitionReader();
  }

  public HistoryDeletionDbReader getHistoryDeletionDbReader(final String physicalTenantId) {
    return readers(physicalTenantId).historyDeletionReader();
  }

  public AgentInstanceDbReader getAgentInstanceDbReader(final String physicalTenantId) {
    return readers(physicalTenantId).agentInstanceReader();
  }

  public GlobalListenerDbReader getGlobalListenerDbReader(final String physicalTenantId) {
    return readers(physicalTenantId).globalListenerReader();
  }

  public DeployedResourceDbReader getResourceDbReader(final String physicalTenantId) {
    return readers(physicalTenantId).deployedResourceReader();
  }

  public ReplicationLogStatusProvider getReplicationLogStatusProvider() {
    return replicationLogStatusProviderFactory.create();
  }

  public RdbmsWriters createWriter(final long partitionId) { // todo fix in all itests afterwards?
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
