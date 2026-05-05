/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.configuration.Rdbms;
import io.camunda.db.rdbms.sql.AuthorizationMapper;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MyBatisConfigurationTest {

  private static final RdbmsDatabaseIdProvider DATABASE_ID_PROVIDER =
      new RdbmsDatabaseIdProvider(null);

  private static Rdbms h2Rdbms() {
    final var rdbms = new Rdbms();
    rdbms.setUrl(
        "jdbc:h2:mem:rdbms-mybatis-test-"
            + UUID.randomUUID()
            + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    rdbms.setUsername("sa");
    rdbms.setPassword("");
    return rdbms;
  }

  @Test
  void shouldBuildSqlSessionFactoryPerPhysicalTenant() throws Exception {
    final var configs = new LinkedHashMap<String, Rdbms>();
    configs.put("tenant-a", h2Rdbms());
    configs.put("tenant-b", h2Rdbms());

    try (final var registry = RdbmsDataSources.of(configs, DATABASE_ID_PROVIDER)) {
      final var configuration = new MyBatisConfiguration();

      final var factories = configuration.sqlSessionFactories(registry, DATABASE_ID_PROVIDER, "");

      assertThat(factories).containsOnlyKeys("tenant-a", "tenant-b");
      assertThat(factories.get("tenant-a")).isNotNull();
      assertThat(factories.get("tenant-b")).isNotNull();
      assertThat(factories.get("tenant-a")).isNotSameAs(factories.get("tenant-b"));
    }
  }

  @Test
  void shouldBuildMapperBundlePerPhysicalTenant() throws Exception {
    final var configs = new LinkedHashMap<String, Rdbms>();
    configs.put("tenant-a", h2Rdbms());
    configs.put("tenant-b", h2Rdbms());

    try (final var registry = RdbmsDataSources.of(configs, DATABASE_ID_PROVIDER)) {
      final var configuration = new MyBatisConfiguration();
      final var factories = configuration.sqlSessionFactories(registry, DATABASE_ID_PROVIDER, "");

      final var bundles = configuration.rdbmsMapperBundles(factories);

      assertThat(bundles).containsOnlyKeys("tenant-a", "tenant-b");
      assertEveryMapperPresent(bundles.get("tenant-a"));
      assertEveryMapperPresent(bundles.get("tenant-b"));
      // Each tenant gets distinct proxies — bound to its own SqlSessionFactory.
      assertThat(bundles.get("tenant-a").authorizationMapper())
          .isNotSameAs(bundles.get("tenant-b").authorizationMapper());
    }
  }

  @Test
  void shouldExposeDefaultTenantMapperAsBean() {
    // given — a bundles map containing the default tenant
    final var defaultBundle = bundleWithDistinctMock();
    final var bundles =
        Map.of(
            RdbmsDataSources.DEFAULT_PHYSICAL_TENANT_ID,
            defaultBundle,
            "other",
            bundleWithDistinctMock());
    final var configuration = new MyBatisConfiguration();

    // when
    final var mapper = configuration.authorizationMapper(bundles);

    // then — the bean returns the default tenant's mapper, not "other"'s
    assertThat(mapper).isSameAs(defaultBundle.authorizationMapper());
  }

  @Test
  void shouldFailExposingMapperWhenDefaultTenantBundleIsMissing() {
    // given — no bundle for the default tenant id
    final var configuration = new MyBatisConfiguration();
    final Map<String, RdbmsMapperBundle> bundles = Map.of("other", bundleWithDistinctMock());

    // when / then
    assertThatThrownBy(() -> configuration.authorizationMapper(bundles))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(RdbmsDataSources.DEFAULT_PHYSICAL_TENANT_ID);
  }

  private static void assertEveryMapperPresent(final RdbmsMapperBundle bundle) {
    assertThat(bundle.auditLogMapper()).isNotNull();
    assertThat(bundle.authorizationMapper()).isNotNull();
    assertThat(bundle.batchOperationMapper()).isNotNull();
    assertThat(bundle.clusterVariableMapper()).isNotNull();
    assertThat(bundle.correlatedMessageSubscriptionMapper()).isNotNull();
    assertThat(bundle.decisionDefinitionMapper()).isNotNull();
    assertThat(bundle.decisionInstanceMapper()).isNotNull();
    assertThat(bundle.decisionRequirementsMapper()).isNotNull();
    assertThat(bundle.deployedResourceMapper()).isNotNull();
    assertThat(bundle.exporterPositionMapper()).isNotNull();
    assertThat(bundle.flowNodeInstanceMapper()).isNotNull();
    assertThat(bundle.formMapper()).isNotNull();
    assertThat(bundle.globalListenerMapper()).isNotNull();
    assertThat(bundle.groupMapper()).isNotNull();
    assertThat(bundle.historyDeletionMapper()).isNotNull();
    assertThat(bundle.incidentMapper()).isNotNull();
    assertThat(bundle.jobMapper()).isNotNull();
    assertThat(bundle.jobMetricsBatchMapper()).isNotNull();
    assertThat(bundle.mappingRuleMapper()).isNotNull();
    assertThat(bundle.messageSubscriptionMapper()).isNotNull();
    assertThat(bundle.persistentWebSessionMapper()).isNotNull();
    assertThat(bundle.processDefinitionMapper()).isNotNull();
    assertThat(bundle.processInstanceMapper()).isNotNull();
    assertThat(bundle.purgeMapper()).isNotNull();
    assertThat(bundle.roleMapper()).isNotNull();
    assertThat(bundle.sequenceFlowMapper()).isNotNull();
    assertThat(bundle.tableMetricsMapper()).isNotNull();
    assertThat(bundle.tenantMapper()).isNotNull();
    assertThat(bundle.usageMetricMapper()).isNotNull();
    assertThat(bundle.usageMetricTUMapper()).isNotNull();
    assertThat(bundle.userMapper()).isNotNull();
    assertThat(bundle.userTaskMapper()).isNotNull();
    assertThat(bundle.variableMapper()).isNotNull();
  }

  private static RdbmsMapperBundle bundleWithDistinctMock() {
    return new RdbmsMapperBundle(
        mock(io.camunda.db.rdbms.sql.AuditLogMapper.class),
        mock(AuthorizationMapper.class),
        mock(io.camunda.db.rdbms.sql.BatchOperationMapper.class),
        mock(io.camunda.db.rdbms.sql.ClusterVariableMapper.class),
        mock(io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper.class),
        mock(io.camunda.db.rdbms.sql.DecisionDefinitionMapper.class),
        mock(io.camunda.db.rdbms.sql.DecisionInstanceMapper.class),
        mock(io.camunda.db.rdbms.sql.DecisionRequirementsMapper.class),
        mock(io.camunda.db.rdbms.sql.DeployedResourceMapper.class),
        mock(io.camunda.db.rdbms.sql.ExporterPositionMapper.class),
        mock(io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.class),
        mock(io.camunda.db.rdbms.sql.FormMapper.class),
        mock(io.camunda.db.rdbms.sql.GlobalListenerMapper.class),
        mock(io.camunda.db.rdbms.sql.GroupMapper.class),
        mock(io.camunda.db.rdbms.sql.HistoryDeletionMapper.class),
        mock(io.camunda.db.rdbms.sql.IncidentMapper.class),
        mock(io.camunda.db.rdbms.sql.JobMapper.class),
        mock(io.camunda.db.rdbms.sql.JobMetricsBatchMapper.class),
        mock(io.camunda.db.rdbms.sql.MappingRuleMapper.class),
        mock(io.camunda.db.rdbms.sql.MessageSubscriptionMapper.class),
        mock(io.camunda.db.rdbms.sql.PersistentWebSessionMapper.class),
        mock(io.camunda.db.rdbms.sql.ProcessDefinitionMapper.class),
        mock(io.camunda.db.rdbms.sql.ProcessInstanceMapper.class),
        mock(io.camunda.db.rdbms.sql.PurgeMapper.class),
        mock(io.camunda.db.rdbms.sql.RoleMapper.class),
        mock(io.camunda.db.rdbms.sql.SequenceFlowMapper.class),
        mock(io.camunda.db.rdbms.sql.TableMetricsMapper.class),
        mock(io.camunda.db.rdbms.sql.TenantMapper.class),
        mock(io.camunda.db.rdbms.sql.UsageMetricMapper.class),
        mock(io.camunda.db.rdbms.sql.UsageMetricTUMapper.class),
        mock(io.camunda.db.rdbms.sql.UserMapper.class),
        mock(io.camunda.db.rdbms.sql.UserTaskMapper.class),
        mock(io.camunda.db.rdbms.sql.VariableMapper.class));
  }
}
