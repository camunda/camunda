/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.ScopedSecurityDescriptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for {@link PhysicalTenantScopeProvider}.
 *
 * <p>All tests bind config from a {@link MockEnvironment} — no Spring context is loaded.
 */
class PhysicalTenantScopeProviderTest {

  @Test
  void shouldReturnEmptyListWhenNoPhysicalTenantsConfigured() {
    // given - no camunda.physical-tenants.* properties
    final var env = env(Map.of("camunda.security.authentication.method", "oidc"));

    // when
    final var provider = new PhysicalTenantScopeProvider(env);

    // then
    assertThat(provider.get()).isEmpty();
  }

  @Test
  void shouldReturnOneDescriptorPerExplicitlyConfiguredTenant() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                // Root has the tenanta named provider
                "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "tenanta-client",
                "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://idp/tenanta",
                "camunda.security.authentication.providers.oidc.tenanta.client-secret",
                    "tenanta-secret",
                // PT tenanta assigns itself
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "tenanta",
                // Root has the other named provider
                "camunda.security.authentication.providers.oidc.pt2.client-id", "pt2-client",
                "camunda.security.authentication.providers.oidc.pt2.issuer-uri", "http://idp/pt2",
                "camunda.security.authentication.providers.oidc.pt2.client-secret", "pt2-secret",
                // PT pt2 assigns itself
                "camunda.physical-tenants.pt2.security.authentication.providers.assigned[0]",
                    "pt2"));

    final var provider = new PhysicalTenantScopeProvider(env);
    final List<ScopedSecurityDescriptor> descriptors = provider.get();

    assertThat(descriptors).hasSize(2);
    final var basePaths = descriptors.stream().map(ScopedSecurityDescriptor::basePath).toList();
    assertThat(basePaths)
        .containsExactlyInAnyOrder("/physical-tenants/tenanta", "/physical-tenants/pt2");
  }

  @Test
  void shouldUseCorrectBasePathFormat() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.providers.oidc.myidp.client-id", "client",
                "camunda.security.authentication.providers.oidc.myidp.issuer-uri", "http://idp",
                "camunda.physical-tenants.myidp.security.authentication.providers.assigned[0]",
                    "myidp"));

    final var provider = new PhysicalTenantScopeProvider(env);

    assertThat(provider.get())
        .singleElement()
        .extracting(ScopedSecurityDescriptor::basePath)
        .isEqualTo("/physical-tenants/myidp");
  }

  @Test
  void shouldCarryNarrowedAuthConfigInDescriptor() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                // Root has two named providers
                "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "tenanta-client",
                "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://idp/tenanta",
                "camunda.security.authentication.providers.oidc.other.client-id", "other-client",
                "camunda.security.authentication.providers.oidc.other.issuer-uri",
                    "http://idp/other",
                // PT tenanta assigns only tenanta (NOT other)
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "tenanta"));

    final var provider = new PhysicalTenantScopeProvider(env);
    final ScopedSecurityDescriptor descriptor = provider.get().get(0);

    assertThat(descriptor.basePath()).isEqualTo("/physical-tenants/tenanta");
    final var auth = descriptor.authentication();
    // Only the assigned provider is present
    assertThat(auth.getProviders()).isNotNull();
    assertThat(auth.getProviders().getOidc()).containsKey("tenanta");
    assertThat(auth.getProviders().getOidc()).doesNotContainKey("other");
    assertThat(auth.getMethod()).isNotNull();
  }

  @Test
  void shouldSkipTenantWithNoAssignedProviders() {
    final var env =
        env(
            Map.of(
                // pt1 has a key under physical-tenants but no providers.assigned
                "camunda.physical-tenants.pt1.security.authentication.method", "oidc",
                // pt2 has everything
                "camunda.security.authentication.providers.oidc.myidp.client-id", "client",
                "camunda.security.authentication.providers.oidc.myidp.issuer-uri", "http://idp",
                "camunda.physical-tenants.pt2.security.authentication.providers.assigned[0]",
                    "myidp"));

    final var provider = new PhysicalTenantScopeProvider(env);
    final List<ScopedSecurityDescriptor> descriptors = provider.get();

    // Only pt2 gets a descriptor; pt1 is skipped (no assigned)
    assertThat(descriptors).hasSize(1);
    assertThat(descriptors.get(0).basePath()).isEqualTo("/physical-tenants/pt2");
  }

  private static MockEnvironment env(final Map<String, String> properties) {
    final MockEnvironment env = new MockEnvironment();
    properties.forEach(env::setProperty);
    return env;
  }
}
