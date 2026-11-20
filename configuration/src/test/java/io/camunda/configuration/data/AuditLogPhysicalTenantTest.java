/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.data;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifies that {@code camunda.data.audit-log} can be set per physical tenant and reaches the
 * configuration of both exporters, through the same paths the broker uses to build them.
 */
class AuditLogPhysicalTenantTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_PREFIX = "camunda.physical-tenants." + TENANT_A + ".";

  private MockEnvironment environment;

  @BeforeEach
  void setUp() {
    environment = new MockEnvironment();
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldReachTheCamundaExporterOfTheTenantOnly() {
    // given a tenant that sets only the engine section, with its own index prefix
    environment.setProperty("camunda.data.secondary-storage.type", "elasticsearch");
    environment.setProperty("camunda.data.audit-log.user.categories[0]", "USER_TASKS");
    environment.setProperty(TENANT_PREFIX + "data.audit-log.engine.categories[0]", "ADMIN");
    environment.setProperty(
        TENANT_PREFIX + "data.secondary-storage.elasticsearch.index-prefix", TENANT_A);
    addSecurityInitialization();
    final var resolver = newResolver();

    // when
    final var tenant = camundaExporterAuditLog(resolver.forPhysicalTenant(TENANT_A));
    final var defaultTenant =
        camundaExporterAuditLog(resolver.forPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID));

    // then the tenant has the engine section and keeps the user section of the root
    assertThat(tenant.getEngine().getCategories()).containsExactly(AuditLogOperationCategory.ADMIN);
    assertThat(tenant.getUser().getCategories())
        .containsExactly(AuditLogOperationCategory.USER_TASKS);
    assertThat(defaultTenant.getEngine().getCategories()).isEmpty();
  }

  @Test
  void shouldReachTheRdbmsExporterOfTheTenantOnly() {
    // given a tenant that sets only the engine section, with its own database
    environment.setProperty("camunda.data.secondary-storage.type", "rdbms");
    environment.setProperty("camunda.data.audit-log.user.categories[0]", "USER_TASKS");
    environment.setProperty(TENANT_PREFIX + "data.audit-log.engine.categories[0]", "ADMIN");
    environment.setProperty(
        TENANT_PREFIX + "data.secondary-storage.rdbms.url", "jdbc:h2:mem:rdbms-" + TENANT_A);
    addSecurityInitialization();
    final var resolver = newResolver();

    // when
    final var tenant =
        BrokerBasedPropertiesOverride.toRdbmsExporterConfiguration(
                resolver.forPhysicalTenant(TENANT_A))
            .getAuditLog();
    final var defaultTenant =
        BrokerBasedPropertiesOverride.toRdbmsExporterConfiguration(
                resolver.forPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID))
            .getAuditLog();

    // then
    assertThat(tenant.getEngine().getCategories()).containsExactly(AuditLogOperationCategory.ADMIN);
    assertThat(tenant.getUser().getCategories())
        .containsExactly(AuditLogOperationCategory.USER_TASKS);
    assertThat(defaultTenant.getEngine().getCategories()).isEmpty();
  }

  private AuditLogConfiguration camundaExporterAuditLog(final Camunda camunda) {
    final var exporter = BrokerBasedPropertiesOverride.convert(camunda).getCamundaExporter();
    return ExporterConfiguration.fromArgs(
            io.camunda.exporter.config.ExporterConfiguration.class, exporter.getArgs())
        .getAuditLog();
  }

  private PhysicalTenantResolver newResolver() {
    final Camunda camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return PhysicalTenantResolver.of(environment, camunda);
  }

  /** Every explicitly declared tenant must carry its own security initialization block. */
  private void addSecurityInitialization() {
    environment.setProperty(
        TENANT_PREFIX + "security.initialization.default-roles.admin.users[0]",
        TENANT_A + "-admin");
  }
}
