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
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.ClusterVariableMapper;
import io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.DecisionRequirementsMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.HistoryDeletionMapper;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.sql.JobMetricsBatchMapper;
import io.camunda.db.rdbms.sql.MessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.PurgeMapper;
import io.camunda.db.rdbms.sql.SequenceFlowMapper;
import io.camunda.db.rdbms.sql.UsageMetricMapper;
import io.camunda.db.rdbms.sql.UsageMetricTUMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.service.AuditLogWriter;
import io.camunda.db.rdbms.write.service.AuthorizationWriter;
import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.db.rdbms.write.service.ClusterVariableWriter;
import io.camunda.db.rdbms.write.service.CorrelatedMessageSubscriptionWriter;
import io.camunda.db.rdbms.write.service.DecisionDefinitionWriter;
import io.camunda.db.rdbms.write.service.DecisionInstanceWriter;
import io.camunda.db.rdbms.write.service.DecisionRequirementsWriter;
import io.camunda.db.rdbms.write.service.ExporterPositionService;
import io.camunda.db.rdbms.write.service.FlowNodeInstanceWriter;
import io.camunda.db.rdbms.write.service.FormWriter;
import io.camunda.db.rdbms.write.service.GlobalListenerWriter;
import io.camunda.db.rdbms.write.service.GroupWriter;
import io.camunda.db.rdbms.write.service.HistoryDeletionWriter;
import io.camunda.db.rdbms.write.service.IncidentWriter;
import io.camunda.db.rdbms.write.service.JobMetricsBatchWriter;
import io.camunda.db.rdbms.write.service.JobWriter;
import io.camunda.db.rdbms.write.service.MappingRuleWriter;
import io.camunda.db.rdbms.write.service.MessageSubscriptionWriter;
import io.camunda.db.rdbms.write.service.ProcessDefinitionWriter;
import io.camunda.db.rdbms.write.service.ProcessInstanceDependant;
import io.camunda.db.rdbms.write.service.ProcessInstanceWriter;
import io.camunda.db.rdbms.write.service.RdbmsPurger;
import io.camunda.db.rdbms.write.service.RdbmsWriter;
import io.camunda.db.rdbms.write.service.RoleWriter;
import io.camunda.db.rdbms.write.service.SequenceFlowWriter;
import io.camunda.db.rdbms.write.service.TenantWriter;
import io.camunda.db.rdbms.write.service.UsageMetricTUWriter;
import io.camunda.db.rdbms.write.service.UsageMetricWriter;
import io.camunda.db.rdbms.write.service.UserTaskWriter;
import io.camunda.db.rdbms.write.service.UserWriter;
import io.camunda.db.rdbms.write.service.VariableWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RdbmsWriters {

  private final RdbmsPurger rdbmsPurger;
  private final ExecutionQueue executionQueue;
  private final ExporterPositionService exporterPositionService;

  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final RdbmsWriterMetrics metrics;

  private final Map<Class<?>, RdbmsWriter> writers = new HashMap<>();

  public RdbmsWriters(
      final RdbmsWriterConfig config,
      final ExecutionQueue executionQueue,
      final ExporterPositionService exporterPositionService,
      final RdbmsWriterMetrics metrics,
      final AuditLogMapper auditLogMapper,
      final DecisionInstanceMapper decisionInstanceMapper,
      final DecisionDefinitionMapper decisionDefinitionMapper,
      final DecisionRequirementsMapper decisionRequirementsMapper,
      final FlowNodeInstanceMapper flowNodeInstanceMapper,
      final IncidentMapper incidentMapper,
      final ProcessInstanceMapper processInstanceMapper,
      final ProcessDefinitionMapper processDefinitionMapper,
      final PurgeMapper purgeMapper,
      final UserTaskMapper userTaskMapper,
      final VariableMapper variableMapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final BatchOperationDbReader batchOperationReader,
      final JobMapper jobMapper,
      final JobMetricsBatchMapper jobMetricsBatchMapper,
      final SequenceFlowMapper sequenceFlowMapper,
      final UsageMetricMapper usageMetricMapper,
      final UsageMetricTUMapper usageMetricTUMapper,
      final BatchOperationMapper batchOperationMapper,
      final MessageSubscriptionMapper messageSubscriptionMapper,
      final CorrelatedMessageSubscriptionMapper correlatedMessageSubscriptionMapper,
      final ClusterVariableMapper clusterVariableMapper,
      final HistoryDeletionMapper historyDeletionMapper) {
    this.executionQueue = executionQueue;
    this.exporterPositionService = exporterPositionService;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.metrics = metrics;
    rdbmsPurger = new RdbmsPurger(purgeMapper, vendorDatabaseProperties);

    writers.put(
        AuditLogWriter.class,
        new AuditLogWriter(executionQueue, auditLogMapper, vendorDatabaseProperties, config));
    writers.put(AuthorizationWriter.class, new AuthorizationWriter(executionQueue));
    writers.put(
        DecisionDefinitionWriter.class,
        new DecisionDefinitionWriter(decisionDefinitionMapper, executionQueue));
    writers.put(
        DecisionInstanceWriter.class,
        new DecisionInstanceWriter(
            decisionInstanceMapper, executionQueue, vendorDatabaseProperties, config));
    writers.put(
        DecisionRequirementsWriter.class,
        new DecisionRequirementsWriter(decisionRequirementsMapper, executionQueue));
    writers.put(
        FlowNodeInstanceWriter.class,
        new FlowNodeInstanceWriter(executionQueue, flowNodeInstanceMapper, config));
    writers.put(GroupWriter.class, new GroupWriter(executionQueue));
    writers.put(
        IncidentWriter.class,
        new IncidentWriter(executionQueue, incidentMapper, vendorDatabaseProperties));
    writers.put(
        ProcessDefinitionWriter.class,
        new ProcessDefinitionWriter(processDefinitionMapper, executionQueue));
    writers.put(
        ProcessInstanceWriter.class,
        new ProcessInstanceWriter(processInstanceMapper, executionQueue));
    writers.put(TenantWriter.class, new TenantWriter(executionQueue));
    writers.put(
        VariableWriter.class,
        new VariableWriter(executionQueue, variableMapper, vendorDatabaseProperties, config));
    writers.put(RoleWriter.class, new RoleWriter(executionQueue));
    writers.put(UserWriter.class, new UserWriter(executionQueue));
    writers.put(UserTaskWriter.class, new UserTaskWriter(executionQueue, userTaskMapper));
    writers.put(FormWriter.class, new FormWriter(executionQueue));
    writers.put(MappingRuleWriter.class, new MappingRuleWriter(executionQueue));
    writers.put(
        BatchOperationWriter.class,
        new BatchOperationWriter(
            batchOperationReader,
            executionQueue,
            batchOperationMapper,
            config,
            vendorDatabaseProperties));
    writers.put(
        JobWriter.class,
        new JobWriter(executionQueue, jobMapper, vendorDatabaseProperties, config));
    writers.put(
        JobMetricsBatchWriter.class,
        new JobMetricsBatchWriter(executionQueue, jobMetricsBatchMapper));
    writers.put(
        SequenceFlowWriter.class, new SequenceFlowWriter(executionQueue, sequenceFlowMapper));
    writers.put(UsageMetricWriter.class, new UsageMetricWriter(executionQueue, usageMetricMapper));
    writers.put(
        UsageMetricTUWriter.class, new UsageMetricTUWriter(executionQueue, usageMetricTUMapper));
    writers.put(
        MessageSubscriptionWriter.class,
        new MessageSubscriptionWriter(executionQueue, messageSubscriptionMapper));
    writers.put(
        CorrelatedMessageSubscriptionWriter.class,
        new CorrelatedMessageSubscriptionWriter(
            executionQueue, correlatedMessageSubscriptionMapper));
    writers.put(
        ClusterVariableWriter.class,
        new ClusterVariableWriter(executionQueue, vendorDatabaseProperties));
    writers.put(
        HistoryDeletionWriter.class,
        new HistoryDeletionWriter(executionQueue, historyDeletionMapper));
    writers.put(GlobalListenerWriter.class, new GlobalListenerWriter(executionQueue));
  }

  public AuthorizationWriter getAuthorizationWriter() {
    return getWriter(AuthorizationWriter.class);
  }

  public AuditLogWriter getAuditLogWriter() {
    return getWriter(AuditLogWriter.class);
  }

  public DecisionDefinitionWriter getDecisionDefinitionWriter() {
    return getWriter(DecisionDefinitionWriter.class);
  }

  public DecisionInstanceWriter getDecisionInstanceWriter() {
    return getWriter(DecisionInstanceWriter.class);
  }

  public DecisionRequirementsWriter getDecisionRequirementsWriter() {
    return getWriter(DecisionRequirementsWriter.class);
  }

  public FlowNodeInstanceWriter getFlowNodeInstanceWriter() {
    return getWriter(FlowNodeInstanceWriter.class);
  }

  public GroupWriter getGroupWriter() {
    return getWriter(GroupWriter.class);
  }

  public IncidentWriter getIncidentWriter() {
    return getWriter(IncidentWriter.class);
  }

  public ProcessDefinitionWriter getProcessDefinitionWriter() {
    return getWriter(ProcessDefinitionWriter.class);
  }

  public ProcessInstanceWriter getProcessInstanceWriter() {
    return getWriter(ProcessInstanceWriter.class);
  }

  public TenantWriter getTenantWriter() {
    return getWriter(TenantWriter.class);
  }

  public VariableWriter getVariableWriter() {
    return getWriter(VariableWriter.class);
  }

  public ClusterVariableWriter getClusterVariableWriter() {
    return getWriter(ClusterVariableWriter.class);
  }

  public RoleWriter getRoleWriter() {
    return getWriter(RoleWriter.class);
  }

  public UserWriter getUserWriter() {
    return getWriter(UserWriter.class);
  }

  public UserTaskWriter getUserTaskWriter() {
    return getWriter(UserTaskWriter.class);
  }

  public FormWriter getFormWriter() {
    return getWriter(FormWriter.class);
  }

  public MappingRuleWriter getMappingRuleWriter() {
    return getWriter(MappingRuleWriter.class);
  }

  public BatchOperationWriter getBatchOperationWriter() {
    return getWriter(BatchOperationWriter.class);
  }

  public JobWriter getJobWriter() {
    return getWriter(JobWriter.class);
  }

  public JobMetricsBatchWriter getJobMetricsBatchWriter() {
    return getWriter(JobMetricsBatchWriter.class);
  }

  public SequenceFlowWriter getSequenceFlowWriter() {
    return getWriter(SequenceFlowWriter.class);
  }

  public UsageMetricWriter getUsageMetricWriter() {
    return getWriter(UsageMetricWriter.class);
  }

  public UsageMetricTUWriter getUsageMetricTUWriter() {
    return getWriter(UsageMetricTUWriter.class);
  }

  public MessageSubscriptionWriter getMessageSubscriptionWriter() {
    return getWriter(MessageSubscriptionWriter.class);
  }

  public CorrelatedMessageSubscriptionWriter getCorrelatedMessageSubscriptionWriter() {
    return getWriter(CorrelatedMessageSubscriptionWriter.class);
  }

  public HistoryDeletionWriter getHistoryDeletionWriter() {
    return getWriter(HistoryDeletionWriter.class);
  }

  public GlobalListenerWriter getGlobalListenerWriter() {
    return getWriter(GlobalListenerWriter.class);
  }

  public List<ProcessInstanceDependant> getProcessInstanceDependantWriters() {
    return writers.values().stream()
        .filter(ProcessInstanceDependant.class::isInstance)
        .map(ProcessInstanceDependant.class::cast)
        .toList();
  }

  private <W> W getWriter(final Class<W> writerClass) {
    return (W) writers.get(writerClass);
  }

  public ExporterPositionService getExporterPositionService() {
    return exporterPositionService;
  }

  public RdbmsPurger getRdbmsPurger() {
    return rdbmsPurger;
  }

  public ExecutionQueue getExecutionQueue() {
    return executionQueue;
  }

  public RdbmsWriterMetrics getMetrics() {
    return metrics;
  }

  public void flush() {
    flush(true);
  }

  /**
   * Flushes the execution queue based on the force parameter.
   *
   * @param force if true, forces an immediate flush; if false, only flushes if queue limits are
   *     reached
   */
  public boolean flush(final boolean force) {
    if (force) {
      return executionQueue.flush() > 0;
    } else {
      return executionQueue.checkQueueForFlush();
    }
  }

  /**
   * Returns the error message size from vendor-specific database properties. This value is used to
   * determine the maximum length for tree paths and other string fields that need to fit within
   * database column size constraints.
   *
   * @return the error message size in characters
   */
  public int getErrorMessageSize() {
    return vendorDatabaseProperties.errorMessageSize();
  }
}
