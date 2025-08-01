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
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
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
import io.camunda.db.rdbms.write.queue.DefaultExecutionQueue;
import io.camunda.db.rdbms.write.service.ExporterPositionService;
import org.apache.ibatis.session.SqlSessionFactory;

public class RdbmsWriterFactory {

  private final SqlSessionFactory sqlSessionFactory;
  private final ExporterPositionMapper exporterPositionMapper;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final DecisionInstanceMapper decisionInstanceMapper;
  private final FlowNodeInstanceMapper flowNodeInstanceMapper;
  private final IncidentMapper incidentMapper;
  private final ProcessInstanceMapper processInstanceMapper;
  private final PurgeMapper purgeMapper;
  private final UserTaskMapper userTaskMapper;
  private final VariableMapper variableMapper;
  private final RdbmsWriterMetrics metrics;
  private final BatchOperationDbReader batchOperationReader;
  private final JobMapper jobMapper;
  private final SequenceFlowMapper sequenceFlowMapper;
  private final UsageMetricMapper usageMetricMapper;
  private final UsageMetricTUMapper usageMetricTUMapper;
  private final BatchOperationMapper batchOperationMapper;

  public RdbmsWriterFactory(
      final SqlSessionFactory sqlSessionFactory,
      final ExporterPositionMapper exporterPositionMapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final DecisionInstanceMapper decisionInstanceMapper,
      final FlowNodeInstanceMapper flowNodeInstanceMapper,
      final IncidentMapper incidentMapper,
      final ProcessInstanceMapper processInstanceMapper,
      final PurgeMapper purgeMapper,
      final UserTaskMapper userTaskMapper,
      final VariableMapper variableMapper,
      final RdbmsWriterMetrics metrics,
      final BatchOperationDbReader batchOperationReader,
      final JobMapper jobMapper,
      final SequenceFlowMapper sequenceFlowMapper,
      final UsageMetricMapper usageMetricMapper,
      final UsageMetricTUMapper usageMetricTUMapper,
      final BatchOperationMapper batchOperationMapper) {
    this.sqlSessionFactory = sqlSessionFactory;
    this.exporterPositionMapper = exporterPositionMapper;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.decisionInstanceMapper = decisionInstanceMapper;
    this.flowNodeInstanceMapper = flowNodeInstanceMapper;
    this.incidentMapper = incidentMapper;
    this.processInstanceMapper = processInstanceMapper;
    this.purgeMapper = purgeMapper;
    this.userTaskMapper = userTaskMapper;
    this.variableMapper = variableMapper;
    this.jobMapper = jobMapper;
    this.metrics = metrics;
    this.batchOperationReader = batchOperationReader;
    this.sequenceFlowMapper = sequenceFlowMapper;
    this.usageMetricMapper = usageMetricMapper;
    this.usageMetricTUMapper = usageMetricTUMapper;
    this.batchOperationMapper = batchOperationMapper;
  }

  public RdbmsWriter createWriter(final RdbmsWriterConfig config) {
    final var executionQueue =
        new DefaultExecutionQueue(
            sqlSessionFactory, config.partitionId(), config.queueSize(), metrics);
    return new RdbmsWriter(
        config,
        executionQueue,
        new ExporterPositionService(executionQueue, exporterPositionMapper),
        metrics,
        decisionInstanceMapper,
        flowNodeInstanceMapper,
        incidentMapper,
        processInstanceMapper,
        purgeMapper,
        userTaskMapper,
        variableMapper,
        vendorDatabaseProperties,
        batchOperationReader,
        jobMapper,
        sequenceFlowMapper,
        usageMetricMapper,
        usageMetricTUMapper,
        batchOperationMapper);
  }
}
