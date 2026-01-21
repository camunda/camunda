/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.MultiEngineAware;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.read.service.BatchOperationDbReader;
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.ClusterVariableMapper;
import io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.HistoryDeletionMapper;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.sql.MessageSubscriptionMapper;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ibatis.session.SqlSessionFactory;

public class RdbmsWriterFactory {

  private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
  private final SqlSessionFactory sqlSessionFactory;
  private final Map<String, SqlSessionFactory> engineSqlSessionFactories;
  private final ExporterPositionMapper exporterPositionMapper;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final AuditLogMapper auditLogMapper;
  private final DecisionInstanceMapper decisionInstanceMapper;
  private final FlowNodeInstanceMapper flowNodeInstanceMapper;
  private final IncidentMapper incidentMapper;
  private final ProcessInstanceMapper processInstanceMapper;
  private final PurgeMapper purgeMapper;
  private final UserTaskMapper userTaskMapper;
  private final VariableMapper variableMapper;
  private final MeterRegistry meterRegistry;
  private final BatchOperationDbReader batchOperationReader;
  private final JobMapper jobMapper;
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
      final Map<String, SqlSessionFactory> engineSqlSessionFactories,
      final ExporterPositionMapper exporterPositionMapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final AuditLogMapper auditLogMapper,
      final DecisionInstanceMapper decisionInstanceMapper,
      final FlowNodeInstanceMapper flowNodeInstanceMapper,
      final IncidentMapper incidentMapper,
      final ProcessInstanceMapper processInstanceMapper,
      final PurgeMapper purgeMapper,
      final UserTaskMapper userTaskMapper,
      final VariableMapper variableMapper,
      final MeterRegistry meterRegistry,
      final BatchOperationDbReader batchOperationReader,
      final JobMapper jobMapper,
      final SequenceFlowMapper sequenceFlowMapper,
      final UsageMetricMapper usageMetricMapper,
      final UsageMetricTUMapper usageMetricTUMapper,
      final BatchOperationMapper batchOperationMapper,
      final MessageSubscriptionMapper messageSubscriptionMapper,
      final CorrelatedMessageSubscriptionMapper correlatedMessageSubscriptionMapper,
      final ClusterVariableMapper clusterVariableMapper,
      final HistoryDeletionMapper historyDeletionMapper) {
    this.sqlSessionFactory = sqlSessionFactory;
    this.engineSqlSessionFactories = engineSqlSessionFactories;
    this.exporterPositionMapper = exporterPositionMapper;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.auditLogMapper = auditLogMapper;
    this.decisionInstanceMapper = decisionInstanceMapper;
    this.flowNodeInstanceMapper = flowNodeInstanceMapper;
    this.incidentMapper = incidentMapper;
    this.processInstanceMapper = processInstanceMapper;
    this.purgeMapper = purgeMapper;
    this.userTaskMapper = userTaskMapper;
    this.variableMapper = variableMapper;
    this.jobMapper = jobMapper;
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
    final String engineName = config.engineName();
    final SqlSessionFactory factory;

    if (engineName != null && engineSqlSessionFactories.containsKey(engineName)) {
      factory = engineSqlSessionFactories.get(engineName);
    } else {
      factory = sqlSessionFactory;
    }

    final var executionQueue =
        new DefaultExecutionQueue(
            factory, config.partitionId(), config.queueSize(), config.queueMemoryLimit(), metrics);
    return new RdbmsWriters(
        config,
        executionQueue,
        new ExporterPositionService(executionQueue, getMapper(exporterPositionMapper, engineName)),
        metrics,
        getMapper(auditLogMapper, engineName),
        getMapper(decisionInstanceMapper, engineName),
        getMapper(flowNodeInstanceMapper, engineName),
        getMapper(incidentMapper, engineName),
        getMapper(processInstanceMapper, engineName),
        getMapper(purgeMapper, engineName),
        getMapper(userTaskMapper, engineName),
        getMapper(variableMapper, engineName),
        vendorDatabaseProperties,
        batchOperationReader, // Reader, might need handling too? But Readers are usually
        // stateless/proxy.
        getMapper(jobMapper, engineName),
        getMapper(sequenceFlowMapper, engineName),
        getMapper(usageMetricMapper, engineName),
        getMapper(usageMetricTUMapper, engineName),
        getMapper(batchOperationMapper, engineName),
        getMapper(messageSubscriptionMapper, engineName),
        getMapper(correlatedMessageSubscriptionMapper, engineName),
        getMapper(clusterVariableMapper, engineName),
        getMapper(historyDeletionMapper, engineName));
  }

  private <T> T getMapper(final T mapper, final String engineName) {
    if (engineName != null && mapper instanceof MultiEngineAware) {
      return (T) ((MultiEngineAware) mapper).withEngine(engineName);
    }
    return mapper;
  }
}
