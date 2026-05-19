/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
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
import org.apache.ibatis.session.SqlSessionFactory;

public record RdbmsMapperBundle(
    SqlSessionFactory sqlSessionFactory,
    VendorDatabaseProperties vendorDatabaseProperties,
    ExporterPositionMapper exporterPositionMapper,
    AuditLogMapper auditLogMapper,
    DecisionInstanceMapper decisionInstanceMapper,
    DecisionDefinitionMapper decisionDefinitionMapper,
    DecisionRequirementsMapper decisionRequirementsMapper,
    FlowNodeInstanceMapper flowNodeInstanceMapper,
    IncidentMapper incidentMapper,
    ProcessInstanceMapper processInstanceMapper,
    ProcessDefinitionMapper processDefinitionMapper,
    PurgeMapper purgeMapper,
    UserTaskMapper userTaskMapper,
    VariableMapper variableMapper,
    JobMapper jobMapper,
    JobMetricsBatchMapper jobMetricsBatchMapper,
    SequenceFlowMapper sequenceFlowMapper,
    UsageMetricMapper usageMetricMapper,
    UsageMetricTUMapper usageMetricTUMapper,
    BatchOperationMapper batchOperationMapper,
    MessageSubscriptionMapper messageSubscriptionMapper,
    CorrelatedMessageSubscriptionMapper correlatedMessageSubscriptionMapper,
    ClusterVariableMapper clusterVariableMapper,
    HistoryDeletionMapper historyDeletionMapper) {

  public static RdbmsMapperBundle from(
      final SqlSessionFactory sqlSessionFactory,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    final var cfg = sqlSessionFactory.getConfiguration();
    return new RdbmsMapperBundle(
        sqlSessionFactory,
        vendorDatabaseProperties,
        cfg.getMapper(ExporterPositionMapper.class, null),
        cfg.getMapper(AuditLogMapper.class, null),
        cfg.getMapper(DecisionInstanceMapper.class, null),
        cfg.getMapper(DecisionDefinitionMapper.class, null),
        cfg.getMapper(DecisionRequirementsMapper.class, null),
        cfg.getMapper(FlowNodeInstanceMapper.class, null),
        cfg.getMapper(IncidentMapper.class, null),
        cfg.getMapper(ProcessInstanceMapper.class, null),
        cfg.getMapper(ProcessDefinitionMapper.class, null),
        cfg.getMapper(PurgeMapper.class, null),
        cfg.getMapper(UserTaskMapper.class, null),
        cfg.getMapper(VariableMapper.class, null),
        cfg.getMapper(JobMapper.class, null),
        cfg.getMapper(JobMetricsBatchMapper.class, null),
        cfg.getMapper(SequenceFlowMapper.class, null),
        cfg.getMapper(UsageMetricMapper.class, null),
        cfg.getMapper(UsageMetricTUMapper.class, null),
        cfg.getMapper(BatchOperationMapper.class, null),
        cfg.getMapper(MessageSubscriptionMapper.class, null),
        cfg.getMapper(CorrelatedMessageSubscriptionMapper.class, null),
        cfg.getMapper(ClusterVariableMapper.class, null),
        cfg.getMapper(HistoryDeletionMapper.class, null));
  }
}
