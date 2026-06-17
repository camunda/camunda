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
}
