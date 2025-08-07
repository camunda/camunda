/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.read.service.BatchOperationDbReader;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.PurgeMapper;
import io.camunda.db.rdbms.sql.SequenceFlowMapper;
import io.camunda.db.rdbms.sql.UsageMetricMapper;
import io.camunda.db.rdbms.sql.UsageMetricTUMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.service.AuthorizationWriter;
import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.db.rdbms.write.service.DecisionDefinitionWriter;
import io.camunda.db.rdbms.write.service.DecisionInstanceWriter;
import io.camunda.db.rdbms.write.service.DecisionRequirementsWriter;
import io.camunda.db.rdbms.write.service.ExporterPositionService;
import io.camunda.db.rdbms.write.service.FlowNodeInstanceWriter;
import io.camunda.db.rdbms.write.service.FormWriter;
import io.camunda.db.rdbms.write.service.GroupWriter;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.db.rdbms.write.service.IncidentWriter;
import io.camunda.db.rdbms.write.service.JobWriter;
import io.camunda.db.rdbms.write.service.MappingRuleWriter;
import io.camunda.db.rdbms.write.service.ProcessDefinitionWriter;
import io.camunda.db.rdbms.write.service.ProcessInstanceWriter;
import io.camunda.db.rdbms.write.service.RdbmsPurger;
import io.camunda.db.rdbms.write.service.RoleWriter;
import io.camunda.db.rdbms.write.service.SequenceFlowWriter;
import io.camunda.db.rdbms.write.service.TenantWriter;
import io.camunda.db.rdbms.write.service.UsageMetricTUWriter;
import io.camunda.db.rdbms.write.service.UsageMetricWriter;
import io.camunda.db.rdbms.write.service.UserTaskWriter;
import io.camunda.db.rdbms.write.service.UserWriter;
import io.camunda.db.rdbms.write.service.VariableWriter;

public class RdbmsWriter {

  private final RdbmsPurger rdbmsPurger;
  private final ExecutionQueue executionQueue;
  private final AuthorizationWriter authorizationWriter;
  private final DecisionDefinitionWriter decisionDefinitionWriter;
  private final DecisionInstanceWriter decisionInstanceWriter;
  private final DecisionRequirementsWriter decisionRequirementsWriter;
  private final ExporterPositionService exporterPositionService;
  private final FlowNodeInstanceWriter flowNodeInstanceWriter;
  private final GroupWriter groupWriter;
  private final IncidentWriter incidentWriter;
  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ProcessInstanceWriter processInstanceWriter;
  private final TenantWriter tenantWriter;
  private final VariableWriter variableWriter;
  private final RoleWriter roleWriter;
  private final UserWriter userWriter;
  private final UserTaskWriter userTaskWriter;
  private final FormWriter formWriter;
  private final MappingRuleWriter mappingRuleWriter;
  private final BatchOperationWriter batchOperationWriter;
  private final JobWriter jobWriter;
  private final SequenceFlowWriter sequenceFlowWriter;
  private final UsageMetricWriter usageMetricWriter;
  private final UsageMetricTUWriter usageMetricTUWriter;

  private final HistoryCleanupService historyCleanupService;

  public RdbmsWriter(
      final RdbmsWriterConfig config,
      final ExecutionQueue executionQueue,
      final ExporterPositionService exporterPositionService,
      final RdbmsWriterMetrics metrics,
      final DecisionInstanceMapper decisionInstanceMapper,
      final FlowNodeInstanceMapper flowNodeInstanceMapper,
      final IncidentMapper incidentMapper,
      final ProcessInstanceMapper processInstanceMapper,
      final PurgeMapper purgeMapper,
      final UserTaskMapper userTaskMapper,
      final VariableMapper variableMapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final BatchOperationDbReader batchOperationReader,
      final JobMapper jobMapper,
      final SequenceFlowMapper sequenceFlowMapper,
      final UsageMetricMapper usageMetricMapper,
      final UsageMetricTUMapper usageMetricTUMapper,
      final BatchOperationMapper batchOperationMapper) {
    this.executionQueue = executionQueue;
    this.exporterPositionService = exporterPositionService;
    rdbmsPurger = new RdbmsPurger(purgeMapper, vendorDatabaseProperties);
    authorizationWriter = new AuthorizationWriter(executionQueue);
    decisionDefinitionWriter = new DecisionDefinitionWriter(executionQueue);
    decisionInstanceWriter =
        new DecisionInstanceWriter(
            decisionInstanceMapper, executionQueue, vendorDatabaseProperties);
    decisionRequirementsWriter = new DecisionRequirementsWriter(executionQueue);
    flowNodeInstanceWriter = new FlowNodeInstanceWriter(executionQueue, flowNodeInstanceMapper);
    groupWriter = new GroupWriter(executionQueue);
    incidentWriter = new IncidentWriter(executionQueue, incidentMapper, vendorDatabaseProperties);
    processDefinitionWriter = new ProcessDefinitionWriter(executionQueue);
    processInstanceWriter = new ProcessInstanceWriter(processInstanceMapper, executionQueue);
    tenantWriter = new TenantWriter(executionQueue);
    variableWriter = new VariableWriter(executionQueue, variableMapper, vendorDatabaseProperties);
    roleWriter = new RoleWriter(executionQueue);
    userWriter = new UserWriter(executionQueue);
    userTaskWriter = new UserTaskWriter(executionQueue, userTaskMapper);
    formWriter = new FormWriter(executionQueue);
    mappingRuleWriter = new MappingRuleWriter(executionQueue);
    batchOperationWriter =
        new BatchOperationWriter(
            batchOperationReader,
            executionQueue,
            batchOperationMapper,
            config,
            vendorDatabaseProperties);
    jobWriter = new JobWriter(executionQueue, jobMapper, vendorDatabaseProperties);
    sequenceFlowWriter = new SequenceFlowWriter(executionQueue, sequenceFlowMapper);
    usageMetricWriter = new UsageMetricWriter(executionQueue, usageMetricMapper);
    usageMetricTUWriter = new UsageMetricTUWriter(executionQueue, usageMetricTUMapper);

    historyCleanupService =
        new HistoryCleanupService(
            config,
            processInstanceWriter,
            incidentWriter,
            flowNodeInstanceWriter,
            userTaskWriter,
            variableWriter,
            decisionInstanceWriter,
            jobWriter,
            sequenceFlowWriter,
            batchOperationWriter,
            metrics);
  }

  public AuthorizationWriter getAuthorizationWriter() {
    return authorizationWriter;
  }

  public DecisionDefinitionWriter getDecisionDefinitionWriter() {
    return decisionDefinitionWriter;
  }

  public DecisionInstanceWriter getDecisionInstanceWriter() {
    return decisionInstanceWriter;
  }

  public DecisionRequirementsWriter getDecisionRequirementsWriter() {
    return decisionRequirementsWriter;
  }

  public FlowNodeInstanceWriter getFlowNodeInstanceWriter() {
    return flowNodeInstanceWriter;
  }

  public GroupWriter getGroupWriter() {
    return groupWriter;
  }

  public IncidentWriter getIncidentWriter() {
    return incidentWriter;
  }

  public ProcessDefinitionWriter getProcessDefinitionWriter() {
    return processDefinitionWriter;
  }

  public ProcessInstanceWriter getProcessInstanceWriter() {
    return processInstanceWriter;
  }

  public TenantWriter getTenantWriter() {
    return tenantWriter;
  }

  public VariableWriter getVariableWriter() {
    return variableWriter;
  }

  public RoleWriter getRoleWriter() {
    return roleWriter;
  }

  public UserWriter getUserWriter() {
    return userWriter;
  }

  public UserTaskWriter getUserTaskWriter() {
    return userTaskWriter;
  }

  public FormWriter getFormWriter() {
    return formWriter;
  }

  public MappingRuleWriter getMappingRuleWriter() {
    return mappingRuleWriter;
  }

  public BatchOperationWriter getBatchOperationWriter() {
    return batchOperationWriter;
  }

  public JobWriter getJobWriter() {
    return jobWriter;
  }

  public SequenceFlowWriter getSequenceFlowWriter() {
    return sequenceFlowWriter;
  }

  public UsageMetricWriter getUsageMetricWriter() {
    return usageMetricWriter;
  }

  public UsageMetricTUWriter getUsageMetricTUWriter() {
    return usageMetricTUWriter;
  }

  public ExporterPositionService getExporterPositionService() {
    return exporterPositionService;
  }

  public RdbmsPurger getRdbmsPurger() {
    return rdbmsPurger;
  }

  public HistoryCleanupService getHistoryCleanupService() {
    return historyCleanupService;
  }

  public ExecutionQueue getExecutionQueue() {
    return executionQueue;
  }

  public void flush() {
    executionQueue.flush();
  }
}
