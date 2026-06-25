/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
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
        sqlSession.getMapper(AgentHistoryMapper.class),
        sqlSession.getMapper(AgentInstanceMapper.class),
        sqlSession.getMapper(AuditLogMapper.class),
        sqlSession.getMapper(AuthorizationMapper.class),
        sqlSession.getMapper(BatchOperationMapper.class),
        sqlSession.getMapper(ClusterVariableMapper.class),
        sqlSession.getMapper(CorrelatedMessageSubscriptionMapper.class),
        sqlSession.getMapper(DecisionDefinitionMapper.class),
        sqlSession.getMapper(DecisionInstanceMapper.class),
        sqlSession.getMapper(DecisionRequirementsMapper.class),
        sqlSession.getMapper(DeployedResourceMapper.class),
        sqlSession.getMapper(ExporterPositionMapper.class),
        sqlSession.getMapper(FlowNodeInstanceMapper.class),
        sqlSession.getMapper(FormMapper.class),
        sqlSession.getMapper(GlobalListenerMapper.class),
        sqlSession.getMapper(GroupMapper.class),
        sqlSession.getMapper(HistoryDeletionMapper.class),
        sqlSession.getMapper(IncidentMapper.class),
        sqlSession.getMapper(JobMapper.class),
        sqlSession.getMapper(JobMetricsBatchMapper.class),
        sqlSession.getMapper(MappingRuleMapper.class),
        sqlSession.getMapper(MessageSubscriptionMapper.class),
        sqlSession.getMapper(PersistentWebSessionMapper.class),
        sqlSession.getMapper(ProcessDefinitionMapper.class),
        sqlSession.getMapper(ProcessInstanceMapper.class),
        sqlSession.getMapper(PurgeMapper.class),
        sqlSession.getMapper(ReplicationStatusMapper.class),
        sqlSession.getMapper(RoleMapper.class),
        sqlSession.getMapper(SequenceFlowMapper.class),
        sqlSession.getMapper(TableMetricsMapper.class),
        sqlSession.getMapper(TenantMapper.class),
        sqlSession.getMapper(UsageMetricMapper.class),
        sqlSession.getMapper(UsageMetricTUMapper.class),
        sqlSession.getMapper(UserMapper.class),
        sqlSession.getMapper(UserTaskMapper.class),
        sqlSession.getMapper(VariableMapper.class),
        sqlSession.getMapper(WaitStateMapper.class));
  }
}
