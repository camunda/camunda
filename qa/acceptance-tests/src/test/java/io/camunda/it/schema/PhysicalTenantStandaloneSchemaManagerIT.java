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

/**
 * Covers that a single run of the schema manager CLI services every physical tenant. Before this
 * was tenant-aware the CLI only ever created the root tenant's schema, and callers worked around it
 * by running the CLI once per tenant.
 *
 * <p>Elasticsearch only: what is under test is the per-tenant fan-out and the failure reporting,
 * neither of which is backend-specific, and {@link StandaloneSchemaManagerIT} already covers both
 * backends for the single-tenant path.
 */
@ZeebeIntegration
final class PhysicalTenantStandaloneSchemaManagerIT {

  // Physical tenant ids are normalized to alphanumerics, so a dash here would not survive.
  private static final String TENANT_ID = "tenanta";
  private static final String DEFAULT_INDEX_PREFIX = "default-tenant";
  private static final String TENANT_INDEX_PREFIX = "tenant-a-prefix";

  /** The Zeebe exporter's own default index prefix, used by the default tenant here. */
  private static final String DEFAULT_EXPORTER_PREFIX = "zeebe-record";

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

    // then - both tenants' webapp indices exist, from the one run
    assertThat(strategy.indicesExist(DEFAULT_INDEX_PREFIX + "-*")).isTrue();
    assertThat(strategy.indicesExist(TENANT_INDEX_PREFIX + "-*")).isTrue();

    // and - each tenant's Zeebe exporter templates were created under that tenant's own prefix.
    // The exporter names templates '<index-prefix>_<value-type>_<version>', so asserting on one
    // value type keeps this specific to the exporter rather than matching webapp templates too.
    assertThat(strategy.countTemplates(TENANT_INDEX_PREFIX + "_process_*")).isGreaterThan(0);
    assertThat(strategy.countTemplates(DEFAULT_EXPORTER_PREFIX + "_process_*")).isGreaterThan(0);
  }

  @Test
  void shouldFailAndNameTheTenantWhenOneTenantCannotBeReached() throws Exception {
    // given - the tenant points at a port nothing is listening on, so it fails fast
    strategy.startContainer();
    strategy.createAdminClient();
    configureDefaultTenant();
    configureTenant("http://localhost:1");

    // when / then - the CLI fails, and the message names the unreachable tenant
    // Asserting on the schema-creation wording as well, so that a configuration error — which would
    // also mention the tenant id — cannot satisfy this test without any schema work happening.
    assertThatThrownBy(schemaManager::start)
        .hasMessageContaining("Failed to create/update the schema")
        .hasMessageContaining(TENANT_ID);

    // and - the healthy tenant was still attempted rather than skipped once the other failed
    assertThat(strategy.indicesExist(DEFAULT_INDEX_PREFIX + "-*")).isTrue();
  }

  private String url() {
    return "http://localhost:" + strategy.getContainer().getMappedPort(9200);
  }

  private void configureDefaultTenant() {
    strategy.configureStandaloneSchemaManager(schemaManager);
    // Matches the value type enabled for the tenant's exporter, so both sides are comparable.
    schemaManager.withProperty("zeebe.broker.exporters.elasticsearch.args.index.process", "true");
    schemaManager.withUnifiedConfig(
        cfg ->
            cfg.getData()
                .getSecondaryStorage()
                .getElasticsearch()
                .setIndexPrefix(DEFAULT_INDEX_PREFIX));
  }

  private void configureTenant(final String tenantUrl) {
    strategy.configurePhysicalTenant(schemaManager, TENANT_ID, tenantUrl, TENANT_INDEX_PREFIX);
  }
}
