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
 *
 * <p>Semantics under test: every discovered tenant id yields a descriptor; per-PT provider
 * selection ({@code assigned}) has been dropped.
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
                // PT tenanta has at least one property configured (overlay)
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.client-id",
                    "pt-tenanta-client",
                // Root has the other named provider
                "camunda.security.authentication.providers.oidc.pt2.client-id", "pt2-client",
                "camunda.security.authentication.providers.oidc.pt2.issuer-uri", "http://idp/pt2",
                "camunda.security.authentication.providers.oidc.pt2.client-secret", "pt2-secret",
                // PT pt2 has at least one property configured
                "camunda.physical-tenants.pt2.security.authentication.providers.oidc.pt2.client-id",
                    "pt-pt2-client"));

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
                // PT myidp has at least one key (overlay)
                "camunda.physical-tenants.myidp.security.authentication.providers.oidc.myidp.client-id",
                    "pt-client"));

    final var provider = new PhysicalTenantScopeProvider(env);

    assertThat(provider.get())
        .singleElement()
        .extracting(ScopedSecurityDescriptor::basePath)
        .isEqualTo("/physical-tenants/myidp");
  }

  @Test
  void shouldCarryMergedAuthConfigInDescriptor() {
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
                // PT tenanta has an overlay property so it gets discovered
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.client-id",
                    "pt-tenanta-client"));

    final var provider = new PhysicalTenantScopeProvider(env);
    final ScopedSecurityDescriptor descriptor = provider.get().get(0);

    assertThat(descriptor.basePath()).isEqualTo("/physical-tenants/tenanta");
    final var auth = descriptor.authentication();
    // All root providers are included (union semantics)
    assertThat(auth.getProviders()).isNotNull();
    assertThat(auth.getProviders().getOidc()).containsKey("tenanta");
    assertThat(auth.getProviders().getOidc()).containsKey("other");
    assertThat(auth.getMethod()).isNotNull();
  }

  @Test
  void shouldReturnDescriptorForEveryDiscoveredTenant() {
    // Both pt1 (no overlay providers) and pt2 (with overlay) yield descriptors —
    // no assigned check skips tenants any more.
    final var env =
        env(
            Map.of(
                // pt1 has a key under physical-tenants but no overlay providers
                "camunda.physical-tenants.pt1.security.authentication.method", "oidc",
                // pt2 has everything
                "camunda.security.authentication.providers.oidc.myidp.client-id", "client",
                "camunda.security.authentication.providers.oidc.myidp.issuer-uri", "http://idp",
                "camunda.physical-tenants.pt2.security.authentication.providers.oidc.myidp.client-id",
                    "pt2-client"));

    final var provider = new PhysicalTenantScopeProvider(env);
    final List<ScopedSecurityDescriptor> descriptors = provider.get();

    // Both tenants now yield descriptors (union semantics — no assigned check)
    assertThat(descriptors).hasSize(2);
    final var basePaths = descriptors.stream().map(ScopedSecurityDescriptor::basePath).toList();
    assertThat(basePaths)
        .containsExactlyInAnyOrder("/physical-tenants/pt1", "/physical-tenants/pt2");
  }

  private static MockEnvironment env(final Map<String, String> properties) {
    final MockEnvironment env = new MockEnvironment();
    properties.forEach(env::setProperty);
    return env;
  }
}
