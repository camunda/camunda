/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.exporter.rdbms.ExporterConfiguration;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifies that the RDBMS exporter configuration is resolved <em>per physical tenant</em> from each
 * tenant's {@code camunda.data.secondary-storage.rdbms.*} — the exact pipeline the {@code
 * rdbmsExporterFactory} bean uses ({@link PhysicalTenantResolver#mapValues} + {@link
 * BrokerBasedPropertiesOverride#toRdbmsExporterConfiguration}). See #57804.
 */
class RdbmsExporterPhysicalTenantConfigTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  @Test
  void shouldResolveExporterConfigurationPerPhysicalTenant() {
    // given three physical tenants (the default plus two named) each with its own rdbms settings
    final MockEnvironment environment = new MockEnvironment();
    environment.setProperty("camunda.data.secondary-storage.type", "rdbms");
    configureTenant(environment, DEFAULT_PHYSICAL_TENANT_ID, "10", "PT1S");
    configureTenant(environment, TENANT_A, "100", "PT2S");
    configureTenant(environment, TENANT_B, "500", "PT9S");

    final PhysicalTenantResolver resolver = PhysicalTenantResolver.of(environment, new Camunda());

    // when the exporter configuration is resolved per physical tenant (as the factory bean does)
    final Map<String, ExporterConfiguration> configByPhysicalTenant =
        resolver.mapValues(BrokerBasedPropertiesOverride::toRdbmsExporterConfiguration);

    // then every tenant's exporter configuration reflects its own settings, and the synthesized
    // default tenant inherits the root configuration
    assertThat(configByPhysicalTenant)
        .containsOnlyKeys(DEFAULT_PHYSICAL_TENANT_ID, TENANT_A, TENANT_B);

    assertThat(configByPhysicalTenant.get(TENANT_A).getQueueSize()).isEqualTo(100);
    assertThat(configByPhysicalTenant.get(TENANT_A).getFlushInterval())
        .isEqualTo(Duration.ofSeconds(2));

    assertThat(configByPhysicalTenant.get(TENANT_B).getQueueSize()).isEqualTo(500);
    assertThat(configByPhysicalTenant.get(TENANT_B).getFlushInterval())
        .isEqualTo(Duration.ofSeconds(9));

    assertThat(configByPhysicalTenant.get(DEFAULT_PHYSICAL_TENANT_ID).getQueueSize()).isEqualTo(10);
    assertThat(configByPhysicalTenant.get(DEFAULT_PHYSICAL_TENANT_ID).getFlushInterval())
        .isEqualTo(Duration.ofSeconds(1));
  }

  private static void configureTenant(
      final MockEnvironment environment,
      final String tenantId,
      final String queueSize,
      final String flushInterval) {
    final String base = "camunda.physical-tenants." + tenantId + ".";
    // A distinct storage location per tenant satisfies the secondary-storage isolation validation.
    environment.setProperty(
        base + "data.secondary-storage.rdbms.url", "jdbc:h2:mem:rdbms-" + tenantId);
    environment.setProperty(base + "data.secondary-storage.rdbms.queue-size", queueSize);
    environment.setProperty(base + "data.secondary-storage.rdbms.flush-interval", flushInterval);
    // Each explicitly-declared tenant must carry its own security initialization block.
    environment.setProperty(
        base + "security.initialization.default-roles.admin.users[0]", tenantId + "-admin");
  }
}
