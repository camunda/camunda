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
import io.camunda.db.rdbms.write.queue.TransactionRunner;
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
            Map.of("default", defaultBundle, "tenantA", tenantABundle),
            new SimpleMeterRegistry(),
            TransactionRunner.noop());

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
        new RdbmsWriterFactory(
            Map.of("default", newBundle()), new SimpleMeterRegistry(), TransactionRunner.noop());

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
        mock(ExporterPositionMapper.class),
        mock(AuditLogMapper.class),
        mock(DecisionInstanceMapper.class),
        mock(DecisionDefinitionMapper.class),
        mock(DecisionRequirementsMapper.class),
        mock(FlowNodeInstanceMapper.class),
        mock(IncidentMapper.class),
        mock(ProcessInstanceMapper.class),
        mock(ProcessDefinitionMapper.class),
        mock(PurgeMapper.class),
        mock(UserTaskMapper.class),
        mock(VariableMapper.class),
        mock(JobMapper.class),
        mock(JobMetricsBatchMapper.class),
        mock(SequenceFlowMapper.class),
        mock(UsageMetricMapper.class),
        mock(UsageMetricTUMapper.class),
        mock(BatchOperationMapper.class),
        mock(MessageSubscriptionMapper.class),
        mock(CorrelatedMessageSubscriptionMapper.class),
        mock(ClusterVariableMapper.class),
        mock(HistoryDeletionMapper.class));
  }
}
