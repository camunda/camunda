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
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
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
import io.camunda.db.rdbms.write.queue.DefaultExecutionQueue;
import io.camunda.db.rdbms.write.service.ExporterPositionService;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.ibatis.session.SqlSessionFactory;

public class RdbmsWriterFactory {

  private final SqlSessionFactory sqlSessionFactory;
  private final ExporterPositionMapper exporterPositionMapper;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final AuditLogMapper auditLogMapper;
  private final DecisionInstanceMapper decisionInstanceMapper;
  private final DecisionDefinitionMapper decisionDefinitionMapper;
  private final DecisionRequirementsMapper decisionRequirementsMapper;
  private final FlowNodeInstanceMapper flowNodeInstanceMapper;
  private final IncidentMapper incidentMapper;
  private final ProcessInstanceMapper processInstanceMapper;
  private final ProcessDefinitionMapper processDefinitionMapper;
  private final PurgeMapper purgeMapper;
  private final UserTaskMapper userTaskMapper;
  private final VariableMapper variableMapper;
  private final MeterRegistry meterRegistry;
  private final BatchOperationDbReader batchOperationReader;
  private final JobMapper jobMapper;
  private final JobMetricsBatchMapper jobMetricsBatchMapper;
  private final SequenceFlowMapper sequenceFlowMapper;
  private final UsageMetricMapper usageMetricMapper;
  private final UsageMetricTUMapper usageMetricTUMapper;
  private final BatchOperationMapper batchOperationMapper;
  private final MessageSubscriptionMapper messageSubscriptionMapper;
  private final CorrelatedMessageSubscriptionMapper correlatedMessageSubscriptionMapper;
  private final ClusterVariableMapper clusterVariableMapper;
  private final HistoryDeletionMapper historyDeletionMapper;

  public RdbmsWriterFactory(
      final SqlSessionFactory sqlSessionFactory,
      final ExporterPositionMapper exporterPositionMapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
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
      final MeterRegistry meterRegistry,
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
    this.sqlSessionFactory = sqlSessionFactory;
    this.exporterPositionMapper = exporterPositionMapper;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.auditLogMapper = auditLogMapper;
    this.decisionInstanceMapper = decisionInstanceMapper;
    this.decisionDefinitionMapper = decisionDefinitionMapper;
    this.decisionRequirementsMapper = decisionRequirementsMapper;
    this.flowNodeInstanceMapper = flowNodeInstanceMapper;
    this.incidentMapper = incidentMapper;
    this.processInstanceMapper = processInstanceMapper;
    this.processDefinitionMapper = processDefinitionMapper;
    this.purgeMapper = purgeMapper;
    this.userTaskMapper = userTaskMapper;
    this.variableMapper = variableMapper;
    this.jobMapper = jobMapper;
    this.jobMetricsBatchMapper = jobMetricsBatchMapper;
    this.meterRegistry = meterRegistry;
    this.batchOperationReader = batchOperationReader;
    this.sequenceFlowMapper = sequenceFlowMapper;
    this.usageMetricMapper = usageMetricMapper;
    this.usageMetricTUMapper = usageMetricTUMapper;
    this.batchOperationMapper = batchOperationMapper;
    this.messageSubscriptionMapper = messageSubscriptionMapper;
    this.correlatedMessageSubscriptionMapper = correlatedMessageSubscriptionMapper;
    this.clusterVariableMapper = clusterVariableMapper;
    this.historyDeletionMapper = historyDeletionMapper;
  }

  public RdbmsWriters createWriter(final RdbmsWriterConfig config) {
    final var metrics = new RdbmsWriterMetrics(meterRegistry, config.partitionId());
    final var executionQueue =
        new DefaultExecutionQueue(
            sqlSessionFactory,
            config.partitionId(),
            config.queueSize(),
            config.queueMemoryLimit(),
            metrics);
    return new RdbmsWriters(
        config,
        executionQueue,
        new ExporterPositionService(executionQueue, exporterPositionMapper),
        metrics,
        auditLogMapper,
        decisionInstanceMapper,
        decisionDefinitionMapper,
        decisionRequirementsMapper,
        flowNodeInstanceMapper,
        incidentMapper,
        processInstanceMapper,
        processDefinitionMapper,
        purgeMapper,
        userTaskMapper,
        variableMapper,
        vendorDatabaseProperties,
        batchOperationReader,
        jobMapper,
        jobMetricsBatchMapper,
        sequenceFlowMapper,
        usageMetricMapper,
        usageMetricTUMapper,
        batchOperationMapper,
        messageSubscriptionMapper,
        correlatedMessageSubscriptionMapper,
        clusterVariableMapper,
        historyDeletionMapper);
  }
}
