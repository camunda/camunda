/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;

class RdbmsWriterFactoryTest {

  @Test
  void shouldCreateWriterFromBundle() {
    // given
    final var factory = new RdbmsWriterFactory(newBundle(), new SimpleMeterRegistry());

    // when
    final var writers =
        factory.createWriter(new RdbmsWriterConfig.Builder().partitionId(0).build());

    // then
    assertThat(writers).isNotNull();
  }

  @Test
  void shouldCreateIsolatedWritersPerCall() {
    // given
    final var factory = new RdbmsWriterFactory(newBundle(), new SimpleMeterRegistry());

    // when
    final var writersPartition0 =
        factory.createWriter(new RdbmsWriterConfig.Builder().partitionId(0).build());
    final var writersPartition1 =
        factory.createWriter(new RdbmsWriterConfig.Builder().partitionId(1).build());

    // then
    assertThat(writersPartition0).isNotSameAs(writersPartition1);
    assertThat(writersPartition0.getExecutionQueue())
        .isNotSameAs(writersPartition1.getExecutionQueue());
  }

  private static RdbmsMapperBundle newBundle() {
    return new RdbmsMapperBundle(
        mock(SqlSessionFactory.class),
        mock(VendorDatabaseProperties.class),
        mock(AgentHistoryMapper.class),
        mock(AgentInstanceMapper.class),
        mock(AuditLogMapper.class),
        mock(AuthorizationMapper.class),
        mock(BatchOperationMapper.class),
        mock(ClusterVariableMapper.class),
        mock(CorrelatedMessageSubscriptionMapper.class),
        mock(DecisionDefinitionMapper.class),
        mock(DecisionInstanceMapper.class),
        mock(DecisionRequirementsMapper.class),
        mock(DeployedResourceMapper.class),
        mock(ExporterPositionMapper.class),
        mock(FlowNodeInstanceMapper.class),
        mock(FormMapper.class),
        mock(GlobalListenerMapper.class),
        mock(GroupMapper.class),
        mock(HistoryDeletionMapper.class),
        mock(IncidentMapper.class),
        mock(JobMapper.class),
        mock(JobMetricsBatchMapper.class),
        mock(MappingRuleMapper.class),
        mock(MessageSubscriptionMapper.class),
        mock(PersistentWebSessionMapper.class),
        mock(ProcessDefinitionMapper.class),
        mock(ProcessInstanceMapper.class),
        mock(PurgeMapper.class),
        mock(ReplicationStatusMapper.class),
        mock(RoleMapper.class),
        mock(SequenceFlowMapper.class),
        mock(TableMetricsMapper.class),
        mock(TenantMapper.class),
        mock(UsageMetricMapper.class),
        mock(UsageMetricTUMapper.class),
        mock(UserMapper.class),
        mock(UserTaskMapper.class),
        mock(VariableMapper.class),
        mock(WaitStateMapper.class));
  }
}
