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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantOverridePolicyValidationTest {

  private static MockEnvironment environmentWith(final Map<String, Object> properties) {
    final MockEnvironment environment = new MockEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    return environment;
  }

  @Test
  void shouldRejectTenantOverridingClusterProperty() {
    // given a tenant overriding a cluster-wide cluster property
    final MockEnvironment environment =
        environmentWith(Map.of("camunda.physical-tenants.tenanta.cluster.size", 4));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .withMessageContaining("may not be overridden per physical tenant")
        .withMessageContaining("tenanta")
        .withMessageContaining("cluster.size");
  }

  @Test
  void shouldRejectTenantOverridingSystemProperty() {
    // given a tenant overriding a system property other than the clock carve-out
    final MockEnvironment environment =
        environmentWith(Map.of("camunda.physical-tenants.tenanta.system.cpu-thread-count", 8));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("system.cpu-thread-count");
  }

  @Test
  void shouldRejectTenantOverridingLicenseProperty() {
    // given a tenant overriding the cluster-wide license
    final MockEnvironment environment =
        environmentWith(Map.of("camunda.physical-tenants.tenanta.license.key", "secret"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("license.key");
  }

  @Test
  void shouldAllowTenantOverridingPartitionCountAndReplicationFactor() {
    // given a tenant overriding the cluster carve-outs that remain overridable per tenant
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.physical-tenants.tenanta.cluster.partition-count", 7,
                "camunda.physical-tenants.tenanta.cluster.replication-factor", 3));

    // when / then the carve-outs are permitted
    assertThatCode(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAllowTenantOverridingSystemClockControlled() {
    // given a tenant overriding the clock-controlled carve-out under the otherwise non-overridable
    // system subtree
    final MockEnvironment environment =
        environmentWith(Map.of("camunda.physical-tenants.tenanta.system.clock-controlled", true));

    // when / then the carve-out is permitted
    assertThatCode(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAllowTenantOverridingOverridableProperty() {
    // given a tenant overriding a freely-overridable property (secondary storage)
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta"));

    // when / then
    assertThatCode(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldApplyPolicyToExplicitlyDeclaredDefaultTenant() {
    // given the explicit 'default' tenant overriding a cluster-wide property
    final MockEnvironment environment =
        environmentWith(Map.of("camunda.physical-tenants.default.license.key", "secret"));

    // when / then the policy applies to 'default' like any other tenant
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .withMessageContaining("default")
        .withMessageContaining("license.key");
  }

  @Test
  void shouldReportViolationsAcrossMultipleTenants() {
    // given two tenants each overriding a different cluster-wide property
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.physical-tenants.tenanta.cluster.size", 4,
                "camunda.physical-tenants.tenantb.system.io-thread-count", 8));

    // when / then both are reported
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("cluster.size")
        .withMessageContaining("tenantb")
        .withMessageContaining("system.io-thread-count");
  }

  @Test
  void shouldRejectTenantOverridingApiRestExecutor() {
    // given a tenant overriding the cluster-wide REST executor config
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.physical-tenants.tenanta.api.rest.executor.max-pool-size-multiplier", 16));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("api.rest.executor.max-pool-size-multiplier");
  }

  @Test
  void shouldRejectTenantOverridingRdbmsMaxVarcharFieldLength() {
    // given a tenant overriding the cluster-wide rdbms max varchar field length
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.physical-tenants.tenanta.data.secondary-storage.rdbms.max-varchar-field-length",
                4000));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("data.secondary-storage.rdbms.max-varchar-field-length");
  }

  @Test
  void shouldNotThrowWhenNoTenantsAreDeclared() {
    // given only root configuration
    final MockEnvironment environment = environmentWith(Map.of("camunda.cluster.size", 4));

    // when / then root-level cluster configuration is allowed
    assertThatCode(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  // --- identity security property tests -------------------------------------------------------

  @Test
  void shouldRejectTenantOverridingAuthenticationMethod() {
    // given a tenant attempting to override the cluster-wide authentication method
    final MockEnvironment environment =
        environmentWith(
            Map.of("camunda.physical-tenants.tenanta.security.authentication.method", "basic"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .withMessageContaining("may not be overridden per physical tenant")
        .withMessageContaining("tenanta")
        .withMessageContaining("security.authentication.method");
  }

  @Test
  void shouldRejectTenantOverridingAuthenticationUnprotectedApi() {
    // given a tenant attempting to override the cluster-wide unprotected-api flag
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.physical-tenants.tenanta.security.authentication.unprotected-api", true));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .withMessageContaining("may not be overridden per physical tenant")
        .withMessageContaining("tenanta")
        .withMessageContaining("security.authentication.unprotected-api");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "security.csrf.enabled",
        "security.csrf.cookie-name",
        "security.http-headers.content-security-policy",
        "security.cluster-admin.user-id",
        "security.multi-tenancy.enabled"
      })
  void shouldRejectTenantOverridingNonOverridableSecuritySubpath(final String relativeKey) {
    // given a tenant overriding a specific sub-key under a non-overridable security path
    final MockEnvironment environment =
        environmentWith(Map.of("camunda.physical-tenants.tenanta." + relativeKey, "somevalue"));

    // when / then the entire subtree is blocked
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .withMessageContaining("may not be overridden per physical tenant")
        .withMessageContaining("tenanta")
        .withMessageContaining(relativeKey);
  }

  @Test
  void shouldAllowTenantOverridingOtherSecurityProperties() {
    // given a tenant overriding a security property that is not on the deny-list
    final MockEnvironment environment =
        environmentWith(
            Map.of("camunda.physical-tenants.tenanta.security.authorizations.enabled", true));

    // when / then freely overridable security properties are allowed
    assertThatCode(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "security.session",
        "security.session.persistent.enabled",
      })
  void shouldRejectTenantOverridingSessionProperties(final String relativeKey) {
    // given a tenant overriding a session property (installation-wide, must not vary per tenant)
    final MockEnvironment environment =
        environmentWith(Map.of("camunda.physical-tenants.tenanta." + relativeKey, "somevalue"));

    // when / then the entire session subtree is blocked
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .withMessageContaining("may not be overridden per physical tenant")
        .withMessageContaining("tenanta")
        .withMessageContaining(relativeKey);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "security.transport-layer-security.cluster.enabled",
        "security.transport-layer-security.cluster.certificate-chain-path",
        "security.transport-layer-security.cluster.key-store.password",
      })
  void shouldRejectTenantOverridingClusterTlsProperties(final String relativeKey) {
    // given a tenant overriding a cluster TLS property (installation-level infrastructure)
    final MockEnvironment environment =
        environmentWith(Map.of("camunda.physical-tenants.tenanta." + relativeKey, "somevalue"));

    // when / then the entire cluster TLS subtree is blocked
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .withMessageContaining("may not be overridden per physical tenant")
        .withMessageContaining("tenanta")
        .withMessageContaining(relativeKey);
  }

  @Test
  void shouldAllowTenantOverridingOidcConfiguration() {
    // given a tenant configuring its own OIDC provider
    final MockEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.physical-tenants.tenanta.security.authentication.oidc.issuer-uri",
                "https://idp.tenanta.example.com"));

    // when / then per-tenant OIDC configuration remains overridable
    assertThatCode(() -> PhysicalTenantOverridePolicyValidation.validate(environment))
        .doesNotThrowAnyException();
  }
}
