/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Drives the real {@link PhysicalTenantResolver} so the per-tenant {@link
 * CamundaSecurityLibraryProperties} values come from actual config binding of {@code
 * camunda.physical-tenants.<id>.security.*} overrides — not from stubs.
 *
 * <p>Only {@code security.authorizations.*} is exercised here: {@code security.multi-tenancy.*} is
 * intentionally non-overridable per physical tenant (it is on the {@code
 * PhysicalTenantOverridePolicyValidation} deny-list — multi-tenancy enablement is cluster-wide).
 */
class PhysicalTenantSecurityPropertiesConfigurationTest {

  private final PhysicalTenantSecurityPropertiesConfiguration configuration =
      new PhysicalTenantSecurityPropertiesConfiguration();

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
  void shouldResolveAuthorizationsEnabledPerTenantFromRealBinding() {
    // given authorizations enabled at the root, overridden to disabled for tenanta
    setProperties(
        Map.of(
            "camunda.security.authorizations.enabled", "true",
            "camunda.physical-tenants.tenanta.security.authorizations.enabled", "false",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta"),
        "tenanta");

    // when
    final var properties = resolve();

    // then the override is bound per tenant; default keeps the root value
    assertThat(
            propertiesOf(properties, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .getAuthorizations()
                .isEnabled())
        .isTrue();
    assertThat(propertiesOf(properties, "tenanta").getAuthorizations().isEnabled()).isFalse();
  }

  @Test
  void shouldResolveAuthorizationsIndependentlyForTwoTenants() {
    // given two tenants with opposite authorization-enabled flags
    setProperties(
        Map.of(
            "camunda.physical-tenants.tenanta.security.authorizations.enabled", "true",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta",
            "camunda.physical-tenants.tenantb.security.authorizations.enabled", "false",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                "tenantb"),
        "tenanta",
        "tenantb");

    // when
    final var properties = resolve();

    // then each tenant resolves its own flag independently
    assertThat(propertiesOf(properties, "tenanta").getAuthorizations().isEnabled()).isTrue();
    assertThat(propertiesOf(properties, "tenantb").getAuthorizations().isEnabled()).isFalse();
  }

  @Test
  void shouldAlwaysContainDefaultEntryReflectingRoot() {
    // given only root configuration is set (no tenants declared)
    setProperties(Map.of("camunda.security.authorizations.enabled", "true"));

    // when
    final var properties = resolve();

    // then a default entry is synthesized from the root and reflects its values
    assertThat(properties.propertiesByPhysicalTenant())
        .containsOnlyKeys(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(
            propertiesOf(properties, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .getAuthorizations()
                .isEnabled())
        .isTrue();
  }

  private PhysicalTenantSecurityProperties resolve() {
    final Camunda camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    final PhysicalTenantResolver resolver = PhysicalTenantResolver.of(environment, camunda);
    return configuration.physicalTenantSecurityProperties(resolver);
  }

  private static CamundaSecurityLibraryProperties propertiesOf(
      final PhysicalTenantSecurityProperties properties, final String physicalTenantId) {
    final var resolved = properties.propertiesByPhysicalTenant().get(physicalTenantId);
    assertThat(resolved).as("properties for physical tenant '%s'", physicalTenantId).isNotNull();
    return resolved;
  }

  /**
   * Sets {@code properties} and, for each given non-default tenant, adds the minimal {@code
   * security.initialization} block that every explicitly-configured tenant requires to pass
   * resolver validation.
   */
  private void setProperties(final Map<String, Object> properties, final String... tenantIds) {
    final Map<String, Object> all = new HashMap<>(properties);
    for (final String tenantId : tenantIds) {
      all.put(
          "camunda.physical-tenants."
              + tenantId
              + ".security.initialization.default-roles.admin.users[0]",
          tenantId + "-admin");
    }
    environment.getPropertySources().addFirst(new MapPropertySource("test", all));
  }
}
