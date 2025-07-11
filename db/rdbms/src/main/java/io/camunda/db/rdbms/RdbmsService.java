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
import io.camunda.db.rdbms.read.service.DecisionDefinitionDbReader;
import io.camunda.db.rdbms.read.service.DecisionInstanceDbReader;
import io.camunda.db.rdbms.read.service.DecisionRequirementsDbReader;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceDbReader;
import io.camunda.db.rdbms.read.service.FormDbReader;
import io.camunda.db.rdbms.read.service.GroupDbReader;
import io.camunda.db.rdbms.read.service.IncidentDbReader;
import io.camunda.db.rdbms.read.service.JobDbReader;
import io.camunda.db.rdbms.read.service.MappingDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionDbReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.read.service.RoleDbReader;
import io.camunda.db.rdbms.read.service.SequenceFlowDbReader;
import io.camunda.db.rdbms.read.service.TenantDbReader;
import io.camunda.db.rdbms.read.service.UsageMetricsDbReader;
import io.camunda.db.rdbms.read.service.UserDbReader;
import io.camunda.db.rdbms.read.service.UserTaskDbReader;
import io.camunda.db.rdbms.read.service.VariableDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.Builder;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;
import io.camunda.search.clients.reader.SearchClientReaders;
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
  private final IncidentDbReader incidentReader;
  private final ProcessDefinitionDbReader processDefinitionReader;
  private final ProcessInstanceDbReader processInstanceReader;
  private final VariableDbReader variableReader;
  private final RoleDbReader roleReader;
  private final TenantDbReader tenantReader;
  private final UserDbReader userReader;
  private final UserTaskDbReader userTaskReader;
  private final FormDbReader formReader;
  private final MappingDbReader mappingReader;
  private final BatchOperationDbReader batchOperationReader;
  private final SequenceFlowDbReader sequenceFlowReader;
  private final BatchOperationItemDbReader batchOperationItemReader;
  private final JobDbReader jobReader;
  private final UsageMetricsDbReader usageMetricReader;

  public RdbmsService(
      final RdbmsWriterFactory rdbmsWriterFactory, final SearchClientReaders readers) {
    this.rdbmsWriterFactory = rdbmsWriterFactory;
    authorizationReader = (AuthorizationDbReader) readers.authorizationReader();
    decisionRequirementsReader =
        (DecisionRequirementsDbReader) readers.decisionRequirementsReader();
    decisionDefinitionReader = (DecisionDefinitionDbReader) readers.decisionDefinitionReader();
    decisionInstanceReader = (DecisionInstanceDbReader) readers.decisionInstanceReader();
    flowNodeInstanceReader = (FlowNodeInstanceDbReader) readers.flowNodeInstanceReader();
    groupReader = (GroupDbReader) readers.groupReader();
    incidentReader = (IncidentDbReader) readers.incidentReader();
    processDefinitionReader = (ProcessDefinitionDbReader) readers.processDefinitionReader();
    processInstanceReader = (ProcessInstanceDbReader) readers.processInstanceReader();
    tenantReader = (TenantDbReader) readers.tenantReader();
    variableReader = (VariableDbReader) readers.variableReader();
    roleReader = (RoleDbReader) readers.roleReader();
    userReader = (UserDbReader) readers.userReader();
    userTaskReader = (UserTaskDbReader) readers.userTaskReader();
    formReader = (FormDbReader) readers.formReader();
    mappingReader = (MappingDbReader) readers.mappingReader();
    batchOperationReader = (BatchOperationDbReader) readers.batchOperationReader();
    sequenceFlowReader = (SequenceFlowDbReader) readers.sequenceFlowReader();
    batchOperationItemReader = (BatchOperationItemDbReader) readers.batchOperationItemReader();
    jobReader = (JobDbReader) readers.jobReader();
    usageMetricReader = (UsageMetricsDbReader) readers.usageMetricsReader();
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

  public VariableDbReader getVariableReader() {
    return variableReader;
  }

  public RoleDbReader getRoleReader() {
    return roleReader;
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

  public MappingDbReader getMappingReader() {
    return mappingReader;
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

  public BatchOperationItemDbReader getBatchOperationItemReader() {
    return batchOperationItemReader;
  }

  public JobDbReader getJobReader() {
    return jobReader;
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
