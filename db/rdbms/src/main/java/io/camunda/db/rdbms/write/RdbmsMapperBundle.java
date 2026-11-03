/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.exception.TranslatingMapperProxy;
import io.camunda.db.rdbms.sql.AgentDefinitionMapper;
import io.camunda.db.rdbms.sql.AgentHistoryMapper;
import io.camunda.db.rdbms.sql.AgentInstanceMapper;
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.sql.AuthorizationMapper;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.ClusterVariableMapper;
import io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.DecisionRequirementsMapper;
import io.camunda.db.rdbms.sql.DeployedResourceMapper;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.FormMapper;
import io.camunda.db.rdbms.sql.GlobalListenerMapper;
import io.camunda.db.rdbms.sql.GroupMapper;
import io.camunda.db.rdbms.sql.HistoryDeletionMapper;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.sql.JobMetricsBatchMapper;
import io.camunda.db.rdbms.sql.MappingRuleMapper;
import io.camunda.db.rdbms.sql.MessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.PersistentWebSessionMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.PurgeMapper;
import io.camunda.db.rdbms.sql.ReplicationStatusMapper;
import io.camunda.db.rdbms.sql.RoleMapper;
import io.camunda.db.rdbms.sql.SequenceFlowMapper;
import io.camunda.db.rdbms.sql.TableMetricsMapper;
import io.camunda.db.rdbms.sql.TenantMapper;
import io.camunda.db.rdbms.sql.UsageMetricMapper;
import io.camunda.db.rdbms.sql.UsageMetricTUMapper;
import io.camunda.db.rdbms.sql.UserMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.sql.WaitStateMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public record RdbmsMapperBundle(
    SqlSessionFactory sqlSessionFactory,
    VendorDatabaseProperties vendorDatabaseProperties,
    AgentDefinitionMapper agentDefinitionMapper,
    AgentHistoryMapper agentHistoryMapper,
    AgentInstanceMapper agentInstanceMapper,
    AuditLogMapper auditLogMapper,
    AuthorizationMapper authorizationMapper,
    BatchOperationMapper batchOperationMapper,
    ClusterVariableMapper clusterVariableMapper,
    CorrelatedMessageSubscriptionMapper correlatedMessageSubscriptionMapper,
    DecisionDefinitionMapper decisionDefinitionMapper,
    DecisionInstanceMapper decisionInstanceMapper,
    DecisionRequirementsMapper decisionRequirementsMapper,
    DeployedResourceMapper deployedResourceMapper,
    ExporterPositionMapper exporterPositionMapper,
    FlowNodeInstanceMapper flowNodeInstanceMapper,
    FormMapper formMapper,
    GlobalListenerMapper globalListenerMapper,
    GroupMapper groupMapper,
    HistoryDeletionMapper historyDeletionMapper,
    IncidentMapper incidentMapper,
    JobMapper jobMapper,
    JobMetricsBatchMapper jobMetricsBatchMapper,
    MappingRuleMapper mappingRuleMapper,
    MessageSubscriptionMapper messageSubscriptionMapper,
    PersistentWebSessionMapper persistentWebSessionMapper,
    ProcessDefinitionMapper processDefinitionMapper,
    ProcessInstanceMapper processInstanceMapper,
    PurgeMapper purgeMapper,
    ReplicationStatusMapper replicationStatusMapper,
    RoleMapper roleMapper,
    SequenceFlowMapper sequenceFlowMapper,
    TableMetricsMapper tableMetricsMapper,
    TenantMapper tenantMapper,
    UsageMetricMapper usageMetricMapper,
    UsageMetricTUMapper usageMetricTUMapper,
    UserMapper userMapper,
    UserTaskMapper userTaskMapper,
    VariableMapper variableMapper,
    WaitStateMapper waitStateMapper) {

  public static RdbmsMapperBundle from(
      final SqlSessionFactory sqlSessionFactory,
      final SqlSession sqlSession,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    return new RdbmsMapperBundle(
        sqlSessionFactory,
        vendorDatabaseProperties,
        getMapper(sqlSession, AgentDefinitionMapper.class),
        getMapper(sqlSession, AgentHistoryMapper.class),
        getMapper(sqlSession, AgentInstanceMapper.class),
        getMapper(sqlSession, AuditLogMapper.class),
        getMapper(sqlSession, AuthorizationMapper.class),
        getMapper(sqlSession, BatchOperationMapper.class),
        getMapper(sqlSession, ClusterVariableMapper.class),
        getMapper(sqlSession, CorrelatedMessageSubscriptionMapper.class),
        getMapper(sqlSession, DecisionDefinitionMapper.class),
        getMapper(sqlSession, DecisionInstanceMapper.class),
        getMapper(sqlSession, DecisionRequirementsMapper.class),
        getMapper(sqlSession, DeployedResourceMapper.class),
        getMapper(sqlSession, ExporterPositionMapper.class),
        getMapper(sqlSession, FlowNodeInstanceMapper.class),
        getMapper(sqlSession, FormMapper.class),
        getMapper(sqlSession, GlobalListenerMapper.class),
        getMapper(sqlSession, GroupMapper.class),
        getMapper(sqlSession, HistoryDeletionMapper.class),
        getMapper(sqlSession, IncidentMapper.class),
        getMapper(sqlSession, JobMapper.class),
        getMapper(sqlSession, JobMetricsBatchMapper.class),
        getMapper(sqlSession, MappingRuleMapper.class),
        getMapper(sqlSession, MessageSubscriptionMapper.class),
        getMapper(sqlSession, PersistentWebSessionMapper.class),
        getMapper(sqlSession, ProcessDefinitionMapper.class),
        getMapper(sqlSession, ProcessInstanceMapper.class),
        getMapper(sqlSession, PurgeMapper.class),
        getMapper(sqlSession, ReplicationStatusMapper.class),
        getMapper(sqlSession, RoleMapper.class),
        getMapper(sqlSession, SequenceFlowMapper.class),
        getMapper(sqlSession, TableMetricsMapper.class),
        getMapper(sqlSession, TenantMapper.class),
        getMapper(sqlSession, UsageMetricMapper.class),
        getMapper(sqlSession, UsageMetricTUMapper.class),
        getMapper(sqlSession, UserMapper.class),
        getMapper(sqlSession, UserTaskMapper.class),
        getMapper(sqlSession, VariableMapper.class),
        getMapper(sqlSession, WaitStateMapper.class));
  }

  private static <T> T getMapper(final SqlSession sqlSession, final Class<T> type) {
    return TranslatingMapperProxy.wrap(type, sqlSession.getMapper(type));
  }
}
