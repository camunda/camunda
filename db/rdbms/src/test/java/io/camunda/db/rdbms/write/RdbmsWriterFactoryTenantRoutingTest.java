/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
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
import java.util.Map;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;

class RdbmsWriterFactoryTenantRoutingTest {

  @Test
  void shouldRouteToBundleForRequestedTenant() {
    // given
    final var defaultBundle = newBundle();
    final var tenantABundle = newBundle();
    final var factory =
        new RdbmsWriterFactory(
            Map.of("default", defaultBundle, "tenantA", tenantABundle), new SimpleMeterRegistry());

    // when
    final var defaultWriters =
        factory.createWriter(new RdbmsWriterConfig.Builder().partitionId(0).build());
    final var tenantAWriters =
        factory.createWriter(
            new RdbmsWriterConfig.Builder().partitionId(0).physicalTenantId("tenantA").build());

    // then
    assertThat(defaultWriters).isNotNull();
    assertThat(tenantAWriters).isNotNull();
    assertThat(defaultWriters).isNotSameAs(tenantAWriters);
    assertThat(defaultWriters.getExecutionQueue()).isNotSameAs(tenantAWriters.getExecutionQueue());
  }

  @Test
  void shouldRejectUnknownTenant() {
    // given
    final var factory =
        new RdbmsWriterFactory(Map.of("default", newBundle()), new SimpleMeterRegistry());

    // when / then
    assertThatThrownBy(
            () ->
                factory.createWriter(
                    new RdbmsWriterConfig.Builder()
                        .partitionId(0)
                        .physicalTenantId("unknown")
                        .build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown")
        .hasMessageContaining("default");
  }

  private static RdbmsMapperBundle newBundle() {
    return new RdbmsMapperBundle(
        mock(SqlSessionFactory.class),
        mock(VendorDatabaseProperties.class),
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
