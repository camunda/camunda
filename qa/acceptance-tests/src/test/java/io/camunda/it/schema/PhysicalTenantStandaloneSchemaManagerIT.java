/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.it.schema.strategy.ElasticsearchBackendStrategy;
import io.camunda.qa.util.cluster.TestStandaloneSchemaManager;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class PhysicalTenantStandaloneSchemaManagerIT {

  private static final String TENANT_ID = "tenanta";
  private static final String DEFAULT_INDEX_PREFIX = "default-tenant";
  private static final String TENANT_INDEX_PREFIX = "tenant-a-prefix";
  private static final String TENANT_EXPORTER_INDEX_PREFIX = "tenant-a-elasticsearch-exporter";

  private static final String DEFAULT_EXPORTER_PREFIX = "zeebe-record";

  private static final String ROOT_EXPORTER_ID = "rootrecords";

  private static final String ROOT_EXPORTER_PREFIX = "root-records";
  private static final String TENANT_ROOT_EXPORTER_PREFIX = "tenant-a-records";

  @AutoClose
  private final ElasticsearchBackendStrategy strategy = new ElasticsearchBackendStrategy();

  @TestZeebe(autoStart = false)
  private final TestStandaloneSchemaManager schemaManager = new TestStandaloneSchemaManager();

  @Test
  void shouldCreateSchemaForEveryPhysicalTenantInOneRun() throws Exception {
    // given
    strategy.startContainer();
    strategy.createAdminClient();
    configureDefaultTenant();
    configureTenant(url());

    // when
    schemaManager.start();

    // then
    assertThat(strategy.indicesExist(DEFAULT_INDEX_PREFIX + "-*")).isTrue();
    assertThat(strategy.indicesExist(TENANT_INDEX_PREFIX + "-*")).isTrue();

    assertThat(strategy.countTemplates(TENANT_EXPORTER_INDEX_PREFIX + "_process_*"))
        .isGreaterThan(0);
    assertThat(strategy.countTemplates(DEFAULT_EXPORTER_PREFIX + "_process_*")).isGreaterThan(0);
  }

  @Test
  void shouldFailAndNameTheTenantWhenOneTenantCannotBeReached() throws Exception {
    // given - the tenant points at a port nothing is listening on
    strategy.startContainer();
    strategy.createAdminClient();
    configureDefaultTenant();
    configureTenant("http://localhost:1");

    // when / then
    assertThatThrownBy(schemaManager::start)
        .hasMessageContaining("Failed to create/update the schema")
        .hasMessageContaining(TENANT_ID);

    // and - the healthy tenant was still attempted
    assertThat(strategy.indicesExist(DEFAULT_INDEX_PREFIX + "-*")).isTrue();
  }

  @Test
  void shouldNotCreateSchemaOfARootExporterTheDefaultTenantDidNotAssign() throws Exception {
    // given - a root-declared exporter only tenant A is assigned
    strategy.startContainer();
    strategy.createAdminClient();
    configureDefaultTenant();
    strategy.configureExporter(
        schemaManager,
        ElasticsearchBackendStrategy.ROOT_SCOPE,
        ROOT_EXPORTER_ID,
        url(),
        ROOT_EXPORTER_PREFIX);
    schemaManager.withProperty("camunda.physical-tenants.default.data.exporters-assigned", "");
    configureTenant(url());
    strategy.configureExporter(
        schemaManager,
        ElasticsearchBackendStrategy.tenantScope(TENANT_ID),
        ROOT_EXPORTER_ID,
        url(),
        TENANT_ROOT_EXPORTER_PREFIX);
    strategy.assignExporters(
        schemaManager,
        TENANT_ID,
        ElasticsearchBackendStrategy.TENANT_EXPORTER_ID,
        ROOT_EXPORTER_ID);

    // when
    schemaManager.start();

    // then - the assigned tenant got the exporter's templates
    assertThat(strategy.countTemplates(TENANT_ROOT_EXPORTER_PREFIX + "_process_*"))
        .isGreaterThan(0);

    // and - the default tenant did not, but its legacy exporter still ran
    assertThat(strategy.countTemplates(ROOT_EXPORTER_PREFIX + "_*")).isZero();

    assertThat(strategy.countTemplates(DEFAULT_EXPORTER_PREFIX + "_process_*")).isGreaterThan(0);
  }

  private String url() {
    return "http://localhost:" + strategy.getContainer().getMappedPort(9200);
  }

  private void configureDefaultTenant() {
    strategy.configureStandaloneSchemaManager(schemaManager);
    schemaManager.withProperty("zeebe.broker.exporters.elasticsearch.args.index.process", "true");
    schemaManager.withUnifiedConfig(
        cfg ->
            cfg.getData()
                .getSecondaryStorage()
                .getElasticsearch()
                .setIndexPrefix(DEFAULT_INDEX_PREFIX));
  }

  private void configureTenant(final String tenantUrl) {
    strategy.configurePhysicalTenant(
        schemaManager, TENANT_ID, tenantUrl, TENANT_INDEX_PREFIX, TENANT_EXPORTER_INDEX_PREFIX);
  }
}
