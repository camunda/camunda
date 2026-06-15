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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantAssignedProvidersValidationTest {

  // -------------------------------------------------------------------------
  // Non-default OIDC tenant must declare a non-empty, valid selection
  // -------------------------------------------------------------------------

  @Test
  void shouldRejectNonDefaultOidcTenantWithoutAssigned() {
    // given an OIDC cluster and a non-default tenant declared (via a PT-only provider) but with no
    // providers.assigned
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.x.client-id",
                    "client-x"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("must declare a non-empty");
  }

  @Test
  void shouldRejectEmptyAssigned() throws IOException {
    // given an OIDC cluster and a tenant with an explicitly empty assigned list
    final Environment environment =
        yamlEnvironment(
            """
            camunda:
              security:
                authentication:
                  method: oidc
                  providers:
                    oidc:
                      a:
                        client-id: client-a
              physical-tenants:
                tenanta:
                  security:
                    authentication:
                      providers:
                        assigned: []
            """);

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("empty")
        .withMessageContaining("must select at least one provider");
  }

  @Test
  void shouldRejectBlankAssignedId() {
    // given a tenant whose assigned list contains a blank entry (e.g. `- ""` in yaml)
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[1]",
                    "a"));

    // when / then — a clear "blank entry" failure, not a confusing "unknown id(s) []"
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("blank entry");
  }

  @Test
  void shouldRejectUnknownAssignedId() {
    // given a tenant assigning a provider id that is not configured anywhere
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "nope"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("unknown OIDC provider id")
        .withMessageContaining("nope");
  }

  @Test
  void shouldAcceptValidNamedSelection() {
    // given a tenant assigning a configured cluster-level named provider
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.security.authentication.providers.oidc.b.client-id", "client-b",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "a"));

    // when / then
    assertThatCode(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptReservedOidcIdWhenDefaultSlotHasContent() {
    // given a configured default slot and a named provider, and a tenant assigning both
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.oidc.client-id", "root-client",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "oidc",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[1]",
                    "a"));

    // when / then
    assertThatCode(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectReservedOidcIdWhenNoDefaultSlotContent() {
    // given no default slot anywhere, but a tenant assigning the reserved "oidc" id
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "oidc"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("unknown OIDC provider id")
        .withMessageContaining("oidc");
  }

  @Test
  void shouldAcceptTenantOverlayProviderInSelection() {
    // given a tenant that declares its own (PT-only) provider and assigns it
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.ptonly.client-id",
                    "client-ptonly",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "ptonly"));

    // when / then
    assertThatCode(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectNamedProviderCollidingWithReservedOidcId() {
    // given a cluster-level named provider literally called "oidc"
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.oidc.client-id", "client-oidc",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "oidc"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("reserved");
  }

  // -------------------------------------------------------------------------
  // Default tenant semantics: implicit full set, no explicit assigned
  // -------------------------------------------------------------------------

  @Test
  void shouldAcceptDefaultTenantDeclaringValidAssigned() {
    // given the default tenant declaring a valid selection — allowed; it limits the default tenant
    // and (via the cluster-auth unification) the /v2 surface too
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.physical-tenants.default.security.authentication.providers.assigned[0]",
                    "a"));

    // when / then
    assertThatCode(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectDefaultTenantAssigningUnknownId() {
    // given the default tenant assigning an id that is not configured
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.physical-tenants.default.security.authentication.providers.assigned[0]",
                    "nope"));

    // when / then — the default tenant is validated like any other
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .withMessageContaining("default")
        .withMessageContaining("unknown OIDC provider id")
        .withMessageContaining("nope");
  }

  // -------------------------------------------------------------------------
  // Method awareness: assigned applies only to OIDC
  // -------------------------------------------------------------------------

  @Test
  void shouldRejectNonOidcTenantDeclaringAssigned() {
    // given a basic-auth cluster and a tenant that nonetheless declares a provider selection
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "basic",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "a"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("applies only to the OIDC");
  }

  @Test
  void shouldAcceptNonOidcTenantWithoutAssigned() {
    // given a basic-auth cluster and a tenant with an unrelated overlay key, no assigned
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "basic",
                "camunda.physical-tenants.tenanta.cluster.partition-count", "7"));

    // when / then
    assertThatCode(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptWhenNoPhysicalTenantsConfigured() {
    // given an OIDC cluster with no physical tenants at all
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a"));

    // when / then
    assertThatCode(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static MockEnvironment environmentWith(final Map<String, Object> properties) {
    final MockEnvironment environment = new MockEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    return environment;
  }

  private static Environment yamlEnvironment(final String yaml) throws IOException {
    final var loaded =
        new YamlPropertySourceLoader()
            .load("test", new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
    // MockEnvironment carries no system-property or env-var sources (unlike StandardEnvironment),
    // so the validation's full-source scan stays hermetic regardless of the runner's environment.
    final MockEnvironment environment = new MockEnvironment();
    loaded.forEach(environment.getPropertySources()::addFirst);
    return environment;
  }
}
