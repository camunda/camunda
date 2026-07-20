/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.exporter;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import io.camunda.application.commons.rdbms.MyBatisConfiguration;
import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.cluster.PartitionId;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import io.camunda.db.rdbms.RdbmsServiceFactory;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.exporter.rdbms.RdbmsExporterFactory;
import io.camunda.exporter.rdbms.RdbmsExporterWrapper;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.InstantSource;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Asserts that the RDBMS exporter is fully physical-tenant aware at runtime: two physical tenants
 * are configured with different exporter settings, and each exporter instance applies its own
 * tenant's {@link io.camunda.exporter.rdbms.ExporterConfiguration} and writes into its own tenant
 * database (see #57804).
 *
 * <p>The default tenant is configured to flush on every record ({@code queue-size=0}) while tenant
 * B buffers ({@code queue-size} large); with no real actor scheduler nothing auto-flushes, so the
 * config-driven flush behavior is observable deterministically right after a single export.
 */
@Tag("rdbms")
@SpringJUnitConfig
// Satisfies @ConditionalOnSecondaryStorageType(rdbms); the per-tenant config (including each
// tenant's own url) is driven by the MockEnvironment-backed PhysicalTenantResolver below.
@TestPropertySource(properties = "camunda.data.secondary-storage.type=rdbms")
class RdbmsExporterPhysicalTenantIT {

  private static final String TENANT_B = "tenantb";
  private static final RecordFixtures FIXTURES = new RecordFixtures();

  @MockitoBean private ActorScheduler actorScheduler;

  @Autowired private RdbmsServiceFactory rdbmsServiceFactory;
  @Autowired private RdbmsSchemaManagerRegistry rdbmsSchemaManagerRegistry;
  @Autowired private PhysicalTenantResolver physicalTenantResolver;

  @Test
  void shouldApplyExporterConfigurationAndIsolateWritesPerPhysicalTenant() {
    // given the per-physical-tenant exporter config resolved exactly as the factory bean does
    final RdbmsExporterFactory factory =
        new RdbmsExporterFactory(
            rdbmsServiceFactory,
            rdbmsSchemaManagerRegistry,
            physicalTenantResolver.mapValues(
                BrokerBasedPropertiesOverride::toRdbmsExporterConfiguration));

    // the default tenant flushes per record; tenant B buffers (see class javadoc)
    final RdbmsExporterWrapper defaultExporter =
        configuredExporter(factory, DEFAULT_PHYSICAL_TENANT_ID);
    final RdbmsExporterWrapper tenantBExporter = configuredExporter(factory, TENANT_B);

    final var defaultRecord = FIXTURES.getProcessInstanceStartedRecord();
    final var tenantBRecord = FIXTURES.getProcessInstanceStartedRecord();
    final long defaultKey = processInstanceKey(defaultRecord);
    final long tenantBKey = processInstanceKey(tenantBRecord);

    // when a record is exported through each tenant's exporter
    defaultExporter.export(defaultRecord);
    tenantBExporter.export(tenantBRecord);

    // then the default tenant applied queue-size=0 and flushed immediately, while tenant B applied
    // its own large queue-size and left the record buffered
    final var defaultReader = processInstanceReader(DEFAULT_PHYSICAL_TENANT_ID);
    final var tenantBReader = processInstanceReader(TENANT_B);

    assertThat(defaultReader.findOne(defaultKey)).isPresent();
    assertThat(tenantBReader.findOne(tenantBKey)).isEmpty();

    // and writes are isolated to each tenant's own database
    assertThat(defaultReader.findOne(tenantBKey)).isEmpty();
    assertThat(tenantBReader.findOne(defaultKey)).isEmpty();
  }

  private RdbmsExporterWrapper configuredExporter(
      final RdbmsExporterFactory factory, final String physicalTenantId) {
    final RdbmsExporterWrapper wrapper = (RdbmsExporterWrapper) factory.newInstance();
    wrapper.configure(
        new ExporterContext(
            null,
            new ExporterConfiguration("rdbms", Map.of()),
            new PartitionId(physicalTenantId, 1),
            "",
            null,
            mock(MeterRegistry.class, RETURNS_DEEP_STUBS),
            InstantSource.system()));
    wrapper.open(new ExporterTestController());
    return wrapper;
  }

  private ProcessInstanceDbReader processInstanceReader(final String physicalTenantId) {
    return rdbmsServiceFactory
        .createRdbmsService(physicalTenantId, new SimpleMeterRegistry())
        .getProcessInstanceReader();
  }

  private static long processInstanceKey(final io.camunda.zeebe.protocol.record.Record<?> record) {
    return ((ProcessInstanceRecordValue) record.getValue()).getProcessInstanceKey();
  }

  @Configuration
  @Import({MyBatisConfiguration.class, RdbmsConfiguration.class})
  static class TestConfig {

    @Bean
    MockEnvironment mockEnvironment() {
      final var env = new MockEnvironment();
      // default tenant: flush on every record
      configureTenant(env, DEFAULT_PHYSICAL_TENANT_ID, "0");
      // tenant B: buffer (nothing auto-flushes without a real actor scheduler)
      configureTenant(env, TENANT_B, "1000");
      return env;
    }

    @Bean
    @Primary
    PhysicalTenantResolver physicalTenantResolverOverride(final MockEnvironment environment) {
      return PhysicalTenantResolver.of(environment, new Camunda());
    }

    @Bean
    Camunda camunda() {
      return new Camunda();
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    private static void configureTenant(
        final MockEnvironment env, final String tenantId, final String queueSize) {
      final var base = "camunda.physical-tenants." + tenantId + ".data.secondary-storage.";
      env.setProperty(base + "type", "rdbms");
      env.setProperty(
          base + "rdbms.url",
          "jdbc:h2:mem:rdbms-pt-" + tenantId + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
      env.setProperty(base + "rdbms.username", "sa");
      env.setProperty(base + "rdbms.password", "");
      env.setProperty(base + "rdbms.queue-size", queueSize);
      env.setProperty(
          "camunda.physical-tenants."
              + tenantId
              + ".security.initialization.default-roles.admin.users[0]",
          tenantId + "-admin");
    }
  }
}
