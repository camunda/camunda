/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
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
      final MapperSource mappers,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    return new RdbmsMapperBundle(
        sqlSessionFactory,
        vendorDatabaseProperties,
        mappers.getMapper(AgentDefinitionMapper.class),
        mappers.getMapper(AgentHistoryMapper.class),
        mappers.getMapper(AgentInstanceMapper.class),
        mappers.getMapper(AuditLogMapper.class),
        mappers.getMapper(AuthorizationMapper.class),
        mappers.getMapper(BatchOperationMapper.class),
        mappers.getMapper(ClusterVariableMapper.class),
        mappers.getMapper(CorrelatedMessageSubscriptionMapper.class),
        mappers.getMapper(DecisionDefinitionMapper.class),
        mappers.getMapper(DecisionInstanceMapper.class),
        mappers.getMapper(DecisionRequirementsMapper.class),
        mappers.getMapper(DeployedResourceMapper.class),
        mappers.getMapper(ExporterPositionMapper.class),
        mappers.getMapper(FlowNodeInstanceMapper.class),
        mappers.getMapper(FormMapper.class),
        mappers.getMapper(GlobalListenerMapper.class),
        mappers.getMapper(GroupMapper.class),
        mappers.getMapper(HistoryDeletionMapper.class),
        mappers.getMapper(IncidentMapper.class),
        mappers.getMapper(JobMapper.class),
        mappers.getMapper(JobMetricsBatchMapper.class),
        mappers.getMapper(MappingRuleMapper.class),
        mappers.getMapper(MessageSubscriptionMapper.class),
        mappers.getMapper(PersistentWebSessionMapper.class),
        mappers.getMapper(ProcessDefinitionMapper.class),
        mappers.getMapper(ProcessInstanceMapper.class),
        mappers.getMapper(PurgeMapper.class),
        mappers.getMapper(ReplicationStatusMapper.class),
        mappers.getMapper(RoleMapper.class),
        mappers.getMapper(SequenceFlowMapper.class),
        mappers.getMapper(TableMetricsMapper.class),
        mappers.getMapper(TenantMapper.class),
        mappers.getMapper(UsageMetricMapper.class),
        mappers.getMapper(UsageMetricTUMapper.class),
        mappers.getMapper(UserMapper.class),
        mappers.getMapper(UserTaskMapper.class),
        mappers.getMapper(VariableMapper.class),
        mappers.getMapper(WaitStateMapper.class));
  }

  /** Where the bundle gets each mapper from. */
  @FunctionalInterface
  public interface MapperSource {
    <T> T getMapper(Class<T> type);
  }
}
