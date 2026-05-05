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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
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
import io.camunda.db.rdbms.sql.RoleMapper;
import io.camunda.db.rdbms.sql.SequenceFlowMapper;
import io.camunda.db.rdbms.sql.TableMetricsMapper;
import io.camunda.db.rdbms.sql.TenantMapper;
import io.camunda.db.rdbms.sql.UsageMetricMapper;
import io.camunda.db.rdbms.sql.UsageMetricTUMapper;
import io.camunda.db.rdbms.sql.UserMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.TransactionRunner;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RdbmsWriterFactoryTest {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  private SqlSessionFactory factoryA;
  private SqlSessionFactory factoryB;
  private VendorDatabaseProperties propertiesA;
  private VendorDatabaseProperties propertiesB;
  private RdbmsWriterFactory writerFactory;

  @BeforeEach
  void beforeEach() {
    factoryA = mock(SqlSessionFactory.class);
    factoryB = mock(SqlSessionFactory.class);
    when(factoryA.openSession(ExecutorType.BATCH, TransactionIsolationLevel.READ_COMMITTED))
        .thenReturn(mock(SqlSession.class));
    when(factoryB.openSession(ExecutorType.BATCH, TransactionIsolationLevel.READ_COMMITTED))
        .thenReturn(mock(SqlSession.class));

    propertiesA = mock(VendorDatabaseProperties.class);
    propertiesB = mock(VendorDatabaseProperties.class);
    when(propertiesA.errorMessageSize()).thenReturn(1111);
    when(propertiesB.errorMessageSize()).thenReturn(2222);

    writerFactory =
        new RdbmsWriterFactory(
            Map.of(TENANT_A, factoryA, TENANT_B, factoryB),
            Map.of(TENANT_A, mockBundle(), TENANT_B, mockBundle()),
            Map.of(TENANT_A, propertiesA, TENANT_B, propertiesB),
            new SimpleMeterRegistry(),
            TransactionRunner.noop());
  }

  @Test
  void shouldUseRequestedTenantsResources() {
    // when
    final var writers =
        writerFactory.createWriter(
            RdbmsWriterConfig.builder().physicalTenantId(TENANT_B).partitionId(7).build());

    // then — vendor properties for tenant B are wired through to RdbmsWriters
    assertThat(writers.getErrorMessageSize()).isEqualTo(2222);

    // and — tenant A's SqlSessionFactory is never opened by this writer
    verifyNoInteractions(factoryA);

    // and — flushing the queue uses tenant B's SqlSessionFactory
    writers
        .getExecutionQueue()
        .executeInQueue(
            new QueueItem(
                ContextType.AUTHORIZATION,
                WriteStatementType.INSERT,
                "id",
                "io.camunda.db.rdbms.sql.AuthorizationMapper.insert",
                new Object()));
    writers.getExecutionQueue().flush();
    verify(factoryB).openSession(ExecutorType.BATCH, TransactionIsolationLevel.READ_COMMITTED);
  }

  @Test
  void shouldDefaultToBuilderTenantWhenUnspecified() {
    // given
    final var defaultWriterFactory =
        new RdbmsWriterFactory(
            Map.of(RdbmsWriterConfig.DEFAULT_PHYSICAL_TENANT_ID, factoryA),
            Map.of(RdbmsWriterConfig.DEFAULT_PHYSICAL_TENANT_ID, mockBundle()),
            Map.of(RdbmsWriterConfig.DEFAULT_PHYSICAL_TENANT_ID, propertiesA),
            new SimpleMeterRegistry(),
            TransactionRunner.noop());

    // when
    final var writers = defaultWriterFactory.createWriter(RdbmsWriterConfig.builder().build());

    // then
    assertThat(writers.getErrorMessageSize()).isEqualTo(1111);
  }

  @Test
  void shouldThrowWhenSqlSessionFactoryIsMissingForTenant() {
    // given
    final var factory =
        new RdbmsWriterFactory(
            Map.of(TENANT_A, factoryA),
            Map.of(TENANT_A, mockBundle(), "missing", mockBundle()),
            Map.of(TENANT_A, propertiesA, "missing", propertiesA),
            new SimpleMeterRegistry(),
            TransactionRunner.noop());

    // when / then
    assertThatThrownBy(
            () ->
                factory.createWriter(
                    RdbmsWriterConfig.builder().physicalTenantId("missing").build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SqlSessionFactory")
        .hasMessageContaining("missing");
  }

  @Test
  void shouldThrowWhenMapperBundleIsMissingForTenant() {
    // given
    final var factory =
        new RdbmsWriterFactory(
            Map.of(TENANT_A, factoryA, "missing", factoryB),
            Map.of(TENANT_A, mockBundle()),
            Map.of(TENANT_A, propertiesA, "missing", propertiesA),
            new SimpleMeterRegistry(),
            TransactionRunner.noop());

    // when / then
    assertThatThrownBy(
            () ->
                factory.createWriter(
                    RdbmsWriterConfig.builder().physicalTenantId("missing").build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RdbmsMapperBundle")
        .hasMessageContaining("missing");
  }

  @Test
  void shouldThrowWhenVendorPropertiesAreMissingForTenant() {
    // given
    final var factory =
        new RdbmsWriterFactory(
            Map.of(TENANT_A, factoryA, "missing", factoryB),
            Map.of(TENANT_A, mockBundle(), "missing", mockBundle()),
            Map.of(TENANT_A, propertiesA),
            new SimpleMeterRegistry(),
            TransactionRunner.noop());

    // when / then
    assertThatThrownBy(
            () ->
                factory.createWriter(
                    RdbmsWriterConfig.builder().physicalTenantId("missing").build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("VendorDatabaseProperties")
        .hasMessageContaining("missing");
  }

  private static RdbmsMapperBundle mockBundle() {
    return new RdbmsMapperBundle(
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
        mock(RoleMapper.class),
        mock(SequenceFlowMapper.class),
        mock(TableMetricsMapper.class),
        mock(TenantMapper.class),
        mock(UsageMetricMapper.class),
        mock(UsageMetricTUMapper.class),
        mock(UserMapper.class),
        mock(UserTaskMapper.class),
        mock(VariableMapper.class));
  }
}
