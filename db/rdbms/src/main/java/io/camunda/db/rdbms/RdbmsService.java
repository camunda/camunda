/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.read.service.AuditLogDbReader;
import io.camunda.db.rdbms.read.service.AuthorizationDbReader;
import io.camunda.db.rdbms.read.service.BatchOperationDbReader;
import io.camunda.db.rdbms.read.service.BatchOperationItemDbReader;
import io.camunda.db.rdbms.read.service.ClusterVariableDbReader;
import io.camunda.db.rdbms.read.service.CorrelatedMessageSubscriptionDbReader;
import io.camunda.db.rdbms.read.service.DecisionDefinitionDbReader;
import io.camunda.db.rdbms.read.service.DecisionInstanceDbReader;
import io.camunda.db.rdbms.read.service.DecisionRequirementsDbReader;
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
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.Builder;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;
import io.camunda.db.rdbms.write.RdbmsWriters;
import java.util.function.Consumer;

/** A holder for all rdbms services */
public class RdbmsService {

  private final RdbmsWriterFactory rdbmsWriterFactory;
  private final AuditLogDbReader auditLogReader;
  private final AuthorizationDbReader authorizationReader;
  private final DecisionDefinitionDbReader decisionDefinitionReader;
  private final DecisionInstanceDbReader decisionInstanceReader;
  private final DecisionRequirementsDbReader decisionRequirementsReader;
  private final FlowNodeInstanceDbReader flowNodeInstanceReader;
  private final GroupDbReader groupReader;
  private final GroupMemberDbReader groupMemberReader;
  private final IncidentDbReader incidentReader;
  private final ProcessDefinitionDbReader processDefinitionReader;
  private final ProcessInstanceDbReader processInstanceReader;
  private final VariableDbReader variableReader;
  private final ClusterVariableDbReader clusterVariableDbReader;
  private final RoleDbReader roleReader;
  private final RoleMemberDbReader roleMemberReader;
  private final TenantDbReader tenantReader;
  private final TenantMemberDbReader tenantMemberReader;
  private final UserDbReader userReader;
  private final UserTaskDbReader userTaskReader;
  private final FormDbReader formReader;
  private final MappingRuleDbReader mappingRuleReader;
  private final BatchOperationDbReader batchOperationReader;
  private final SequenceFlowDbReader sequenceFlowReader;
  private final BatchOperationItemDbReader batchOperationItemReader;
  private final JobDbReader jobReader;
  private final JobMetricsBatchDbReader jobMetricsBatchDbReader;
  private final UsageMetricsDbReader usageMetricReader;
  private final UsageMetricTUDbReader usageMetricTUDbReader;
  private final MessageSubscriptionDbReader messageSubscriptionReader;
  private final ProcessDefinitionMessageSubscriptionStatisticsDbReader
      processDefinitionMessageSubscriptionStatisticsDbReader;
  private final CorrelatedMessageSubscriptionDbReader correlatedMessageSubscriptionReader;
  private final ProcessDefinitionInstanceStatisticsDbReader
      processDefinitionInstanceStatisticsDbReader;
  private final ProcessDefinitionInstanceVersionStatisticsDbReader
      processDefinitionInstanceVersionStatisticsDbReader;
  private final HistoryDeletionDbReader historyDeletionDbReader;
  private final IncidentProcessInstanceStatisticsByErrorDbReader
      incidentProcessInstanceStatisticsByErrorDbReader;
  private final IncidentProcessInstanceStatisticsByDefinitionDbReader
      incidentProcessInstanceStatisticsByDefinitionDbReader;
  private final GlobalListenerDbReader globalListenerDbReader;

  public RdbmsService(
      final RdbmsWriterFactory rdbmsWriterFactory,
      final AuditLogDbReader auditLogReader,
      final AuthorizationDbReader authorizationReader,
      final DecisionDefinitionDbReader decisionDefinitionReader,
      final DecisionInstanceDbReader decisionInstanceReader,
      final DecisionRequirementsDbReader decisionRequirementsReader,
      final FlowNodeInstanceDbReader flowNodeInstanceReader,
      final GroupDbReader groupReader,
      final GroupMemberDbReader groupMemberReader,
      final IncidentDbReader incidentReader,
      final ProcessDefinitionDbReader processDefinitionReader,
      final ProcessInstanceDbReader processInstanceReader,
      final VariableDbReader variableReader,
      final ClusterVariableDbReader clusterVariableDbReader,
      final RoleDbReader roleReader,
      final RoleMemberDbReader roleMemberReader,
      final TenantDbReader tenantReader,
      final TenantMemberDbReader tenantMemberReader,
      final UserDbReader userReader,
      final UserTaskDbReader userTaskReader,
      final FormDbReader formReader,
      final MappingRuleDbReader mappingRuleReader,
      final BatchOperationDbReader batchOperationReader,
      final SequenceFlowDbReader sequenceFlowReader,
      final BatchOperationItemDbReader batchOperationItemReader,
      final JobDbReader jobReader,
      final JobMetricsBatchDbReader jobMetricsBatchDbReader,
      final UsageMetricsDbReader usageMetricReader,
      final UsageMetricTUDbReader usageMetricTUDbReader,
      final MessageSubscriptionDbReader messageSubscriptionReader,
      final ProcessDefinitionMessageSubscriptionStatisticsDbReader
          processDefinitionMessageSubscriptionStatisticsDbReader,
      final CorrelatedMessageSubscriptionDbReader correlatedMessageSubscriptionReader,
      final ProcessDefinitionInstanceStatisticsDbReader processDefinitionInstanceStatisticsDbReader,
      final ProcessDefinitionInstanceVersionStatisticsDbReader
          processDefinitionInstanceVersionStatisticsDbReader,
      final HistoryDeletionDbReader historyDeletionDbReader,
      final IncidentProcessInstanceStatisticsByErrorDbReader
          incidentProcessInstanceStatisticsByErrorDbReader,
      final IncidentProcessInstanceStatisticsByDefinitionDbReader
          incidentProcessInstanceStatisticsByDefinitionDbReader,
      final GlobalListenerDbReader globalListenerDbReader) {
    this.rdbmsWriterFactory = rdbmsWriterFactory;
    this.auditLogReader = auditLogReader;
    this.authorizationReader = authorizationReader;
    this.decisionRequirementsReader = decisionRequirementsReader;
    this.decisionDefinitionReader = decisionDefinitionReader;
    this.decisionInstanceReader = decisionInstanceReader;
    this.flowNodeInstanceReader = flowNodeInstanceReader;
    this.groupReader = groupReader;
    this.groupMemberReader = groupMemberReader;
    this.incidentReader = incidentReader;
    this.processDefinitionReader = processDefinitionReader;
    this.processInstanceReader = processInstanceReader;
    this.roleMemberReader = roleMemberReader;
    this.tenantReader = tenantReader;
    this.variableReader = variableReader;
    this.clusterVariableDbReader = clusterVariableDbReader;
    this.roleReader = roleReader;
    this.tenantMemberReader = tenantMemberReader;
    this.userReader = userReader;
    this.userTaskReader = userTaskReader;
    this.formReader = formReader;
    this.mappingRuleReader = mappingRuleReader;
    this.batchOperationReader = batchOperationReader;
    this.sequenceFlowReader = sequenceFlowReader;
    this.batchOperationItemReader = batchOperationItemReader;
    this.jobReader = jobReader;
    this.jobMetricsBatchDbReader = jobMetricsBatchDbReader;
    this.usageMetricReader = usageMetricReader;
    this.usageMetricTUDbReader = usageMetricTUDbReader;
    this.messageSubscriptionReader = messageSubscriptionReader;
    this.processDefinitionMessageSubscriptionStatisticsDbReader =
        processDefinitionMessageSubscriptionStatisticsDbReader;
    this.correlatedMessageSubscriptionReader = correlatedMessageSubscriptionReader;
    this.processDefinitionInstanceStatisticsDbReader = processDefinitionInstanceStatisticsDbReader;
    this.processDefinitionInstanceVersionStatisticsDbReader =
        processDefinitionInstanceVersionStatisticsDbReader;
    this.historyDeletionDbReader = historyDeletionDbReader;
    this.incidentProcessInstanceStatisticsByErrorDbReader =
        incidentProcessInstanceStatisticsByErrorDbReader;
    this.incidentProcessInstanceStatisticsByDefinitionDbReader =
        incidentProcessInstanceStatisticsByDefinitionDbReader;
    this.globalListenerDbReader = globalListenerDbReader;
  }

  public AuthorizationDbReader getAuthorizationReader() {
    return authorizationReader;
  }

  public AuditLogDbReader getAuditLogReader() {
    return auditLogReader;
  }

  public DecisionDefinitionDbReader getDecisionDefinitionReader() {
    return decisionDefinitionReader;
  }

  public DecisionInstanceDbReader getDecisionInstanceReader() {
    return decisionInstanceReader;
  }

  public DecisionRequirementsDbReader getDecisionRequirementsReader() {
    return decisionRequirementsReader;
  }

  public FlowNodeInstanceDbReader getFlowNodeInstanceReader() {
    return flowNodeInstanceReader;
  }

  public GroupDbReader getGroupReader() {
    return groupReader;
  }

  public GroupMemberDbReader getGroupMemberReader() {
    return groupMemberReader;
  }

  public IncidentDbReader getIncidentReader() {
    return incidentReader;
  }

  public ProcessDefinitionDbReader getProcessDefinitionReader() {
    return processDefinitionReader;
  }

  public ProcessInstanceDbReader getProcessInstanceReader() {
    return processInstanceReader;
  }

  public TenantDbReader getTenantReader() {
    return tenantReader;
  }

  public TenantMemberDbReader getTenantMemberReader() {
    return tenantMemberReader;
  }

  public VariableDbReader getVariableReader() {
    return variableReader;
  }

  public ClusterVariableDbReader getClusterVariableReader() {
    return clusterVariableDbReader;
  }

  public RoleDbReader getRoleReader() {
    return roleReader;
  }

  public RoleMemberDbReader getRoleMemberReader() {
    return roleMemberReader;
  }

  public UserDbReader getUserReader() {
    return userReader;
  }

  public UserTaskDbReader getUserTaskReader() {
    return userTaskReader;
  }

  public FormDbReader getFormReader() {
    return formReader;
  }

  public MappingRuleDbReader getMappingRuleReader() {
    return mappingRuleReader;
  }

  public BatchOperationDbReader getBatchOperationReader() {
    return batchOperationReader;
  }

  public SequenceFlowDbReader getSequenceFlowReader() {
    return sequenceFlowReader;
  }

  public UsageMetricsDbReader getUsageMetricReader() {
    return usageMetricReader;
  }

  public UsageMetricTUDbReader getUsageMetricTUReader() {
    return usageMetricTUDbReader;
  }

  public BatchOperationItemDbReader getBatchOperationItemReader() {
    return batchOperationItemReader;
  }

  public JobDbReader getJobReader() {
    return jobReader;
  }

  public JobMetricsBatchDbReader getJobMetricsBatchDbReader() {
    return jobMetricsBatchDbReader;
  }

  public MessageSubscriptionDbReader getMessageSubscriptionReader() {
    return messageSubscriptionReader;
  }

  public ProcessDefinitionMessageSubscriptionStatisticsDbReader
      getProcessDefinitionMessageSubscriptionStatisticsDbReader() {
    return processDefinitionMessageSubscriptionStatisticsDbReader;
  }

  public CorrelatedMessageSubscriptionDbReader getCorrelatedMessageSubscriptionReader() {
    return correlatedMessageSubscriptionReader;
  }

  public RdbmsWriters createWriter(final long partitionId) { // todo fix in all itests afterwards?
    return createWriter(new RdbmsWriterConfig.Builder().partitionId((int) partitionId).build());
  }

  public ProcessDefinitionInstanceStatisticsDbReader
      getProcessDefinitionInstanceStatisticsReader() {
    return processDefinitionInstanceStatisticsDbReader;
  }

  public ProcessDefinitionInstanceVersionStatisticsDbReader
      getProcessDefinitionInstanceVersionStatisticsReader() {
    return processDefinitionInstanceVersionStatisticsDbReader;
  }

  public IncidentProcessInstanceStatisticsByErrorDbReader
      getIncidentProcessInstanceStatisticsByErrorDbReader() {
    return incidentProcessInstanceStatisticsByErrorDbReader;
  }

  public HistoryDeletionDbReader getHistoryDeletionDbReader() {
    return historyDeletionDbReader;
  }

  public IncidentProcessInstanceStatisticsByDefinitionDbReader
      getIncidentProcessInstanceStatisticsByDefinitionReader() {
    return incidentProcessInstanceStatisticsByDefinitionDbReader;
  }

  public GlobalListenerDbReader getGlobalListenerDbReader() {
    return globalListenerDbReader;
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
