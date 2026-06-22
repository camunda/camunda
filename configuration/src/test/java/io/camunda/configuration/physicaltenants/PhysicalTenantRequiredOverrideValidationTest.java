/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.configuration.UnifiedConfigurationException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantRequiredOverrideValidationTest {

  private static MockEnvironment environmentWith(final Map<String, Object> properties) {
    final MockEnvironment environment = new MockEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    return environment;
  }

  @Test
  void shouldRejectTenantWithoutInitializationBlock() {
    // given a non-default tenant that only overrides an unrelated property
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta"));

    // when / then resolution fails fast, naming the offending tenant
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantRequiredOverrideValidation.validate(environment))
        .withMessageContaining("camunda.physical-tenants.<id>.security.initialization")
        .withMessageContaining("tenanta");
  }

  @Test
  void shouldReportEveryTenantMissingInitialization() {
    // given two non-default tenants, neither declaring an initialization block
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.physical-tenants.tenanta.cluster.partition-count", 7,
                "camunda.physical-tenants.tenantb.cluster.partition-count", 3));

    // when / then both are reported
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantRequiredOverrideValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb");
  }

  @Test
  void shouldAllowTenantThatDeclaresInitializationBlock() {
    // given a non-default tenant providing its own initialization block
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
                "tenanta-admin"));

    // when / then the requirement is satisfied
    assertThatCode(() -> PhysicalTenantRequiredOverrideValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAllowTenantDeclaringAnyKeyUnderInitialization() {
    // given a tenant that declares a deeply nested initialization key
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.physical-tenants.tenanta.security.initialization.users[0].username",
                "demo",
                "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta"));

    // when / then any key at or under security.initialization satisfies the requirement
    assertThatCode(() -> PhysicalTenantRequiredOverrideValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldNotRequireInitializationForTheDefaultTenant() {
    // given an explicitly-declared 'default' tenant without an initialization block
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.physical-tenants.default.data.secondary-storage.elasticsearch.index-prefix",
                "custom"));

    // when / then the default tenant inherits the top-level initialization and is exempt
    assertThatCode(() -> PhysicalTenantRequiredOverrideValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldOnlyRejectTenantsThatAreMissingInitialization() {
    // given one tenant with an initialization block and one without
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
                "tenanta-admin",
                "camunda.physical-tenants.tenantb.cluster.partition-count",
                3));

    // when / then only the tenant without initialization is reported
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantRequiredOverrideValidation.validate(environment))
        .withMessageContaining("tenantb")
        .withMessageNotContaining("tenanta");
  }

  @Test
  void shouldNotThrowWhenNoTenantsAreDeclared() {
    // given only root configuration, no physical tenants
    final MockEnvironment environment = environmentWith(Map.of("camunda.cluster.size", 5));

    // when / then there is nothing to validate
    assertThatCode(() -> PhysicalTenantRequiredOverrideValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldNotRequireInitializationWhenTenantAuthorizationDisabled() {
    // given a non-default tenant with authorization disabled and no initialization block
    final MockEnvironment environment =
        environmentWith(
            Map.of("camunda.physical-tenants.tenanta.security.authorization.enabled", false));

    // when / then the initialization block is not required for an authz-disabled tenant
    assertThatCode(() -> PhysicalTenantRequiredOverrideValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldNotRequireInitializationWhenRootAuthorizationDisabled() {
    // given root authorization disabled and a tenant that does not override it or declare init
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.security.authorization.enabled",
                false,
                "camunda.physical-tenants.tenanta.cluster.partition-count",
                3));

    // when / then the tenant inherits the disabled root authorization and is exempt
    assertThatCode(() -> PhysicalTenantRequiredOverrideValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRequireInitializationWhenTenantAuthorizationEnabledExplicitly() {
    // given a tenant that explicitly enables authorization but declares no initialization block
    final MockEnvironment environment =
        environmentWith(
            Map.of("camunda.physical-tenants.tenanta.security.authorization.enabled", true));

    // when / then initialization is still required, and the offending tenant is named
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantRequiredOverrideValidation.validate(environment))
        .withMessageContaining("camunda.physical-tenants.<id>.security.initialization")
        .withMessageContaining("tenanta");
  }

  @Test
  void shouldOnlyExemptTenantsWithAuthorizationDisabled() {
    // given one authz-disabled tenant and one authz-enabled tenant, neither with init
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.physical-tenants.tenanta.security.authorization.enabled", false,
                "camunda.physical-tenants.tenantb.security.authorization.enabled", true));

    // when / then only the authz-enabled tenant is reported as missing initialization
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantRequiredOverrideValidation.validate(environment))
        .withMessageContaining("tenantb")
        .withMessageNotContaining("tenanta");
  }

  @Test
  void shouldRequireInitializationWhenAuthorizationValueIsBlank() {
    // given a non-default tenant whose authorization.enabled is present but blank (YAML
    // 'enabled:'), with no initialization block and no root override
    final MockEnvironment environment =
        environmentWith(
            Map.of("camunda.physical-tenants.tenanta.security.authorization.enabled", ""));

    // when / then Spring binds a blank value to "unbound", so authorization resolves to the
    // default (enabled) and initialization is still required — a clear configuration error,
    // not a NullPointerException (which would not match UnifiedConfigurationException)
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantRequiredOverrideValidation.validate(environment))
        .withMessageContaining("camunda.physical-tenants.<id>.security.initialization")
        .withMessageContaining("tenanta");
  }
}
