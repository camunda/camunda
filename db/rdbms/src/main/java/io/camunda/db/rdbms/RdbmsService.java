/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.read.service.AuthorizationDbReader;
import io.camunda.db.rdbms.read.service.BatchOperationDbReader;
import io.camunda.db.rdbms.read.service.BatchOperationItemDbReader;
import io.camunda.db.rdbms.read.service.CorrelatedMessageSubscriptionDbReader;
import io.camunda.db.rdbms.read.service.DecisionDefinitionDbReader;
import io.camunda.db.rdbms.read.service.DecisionInstanceDbReader;
import io.camunda.db.rdbms.read.service.DecisionRequirementsDbReader;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceDbReader;
import io.camunda.db.rdbms.read.service.FormDbReader;
import io.camunda.db.rdbms.read.service.GroupDbReader;
import io.camunda.db.rdbms.read.service.GroupMemberDbReader;
import io.camunda.db.rdbms.read.service.IncidentDbReader;
import io.camunda.db.rdbms.read.service.JobDbReader;
import io.camunda.db.rdbms.read.service.MappingRuleDbReader;
import io.camunda.db.rdbms.read.service.MessageSubscriptionDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionDbReader;
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
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.Builder;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;
import java.util.function.Consumer;

/** A holder for all rdbms services */
public class RdbmsService {

  private final RdbmsWriterFactory rdbmsWriterFactory;
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
  private final UsageMetricsDbReader usageMetricReader;
  private final UsageMetricTUDbReader usageMetricTUDbReader;
  private final MessageSubscriptionDbReader messageSubscriptionReader;
  private final CorrelatedMessageSubscriptionDbReader correlatedMessageSubscriptionReader;

  public RdbmsService(
      final RdbmsWriterFactory rdbmsWriterFactory,
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
      final UsageMetricsDbReader usageMetricReader,
      final UsageMetricTUDbReader usageMetricTUDbReader,
      final MessageSubscriptionDbReader messageSubscriptionReader,
      final CorrelatedMessageSubscriptionDbReader correlatedMessageSubscriptionReader) {
    this.rdbmsWriterFactory = rdbmsWriterFactory;
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
    this.usageMetricReader = usageMetricReader;
    this.usageMetricTUDbReader = usageMetricTUDbReader;
    this.messageSubscriptionReader = messageSubscriptionReader;
    this.correlatedMessageSubscriptionReader = correlatedMessageSubscriptionReader;
  }

  public AuthorizationDbReader getAuthorizationReader() {
    return authorizationReader;
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

  public MessageSubscriptionDbReader getMessageSubscriptionReader() {
    return messageSubscriptionReader;
  }

  public CorrelatedMessageSubscriptionDbReader getCorrelatedMessageSubscriptionReader() {
    return correlatedMessageSubscriptionReader;
  }

  public RdbmsWriter createWriter(final long partitionId) { // todo fix in all itests afterwards?
    return createWriter(new RdbmsWriterConfig.Builder().partitionId((int) partitionId).build());
  }

  public RdbmsWriter createWriter(final RdbmsWriterConfig config) {
    return rdbmsWriterFactory.createWriter(config);
  }

  public RdbmsWriter createWriter(final Consumer<Builder> configBuilder) {
    final RdbmsWriterConfig.Builder builder = new RdbmsWriterConfig.Builder();
    configBuilder.accept(builder);
    return rdbmsWriterFactory.createWriter(builder.build());
  }
}
