/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static io.camunda.spring.utils.PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.api.model.config.ScopedSecurityDescriptor;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for {@link PhysicalTenantScopeProvider}.
 *
 * <p>All tests bind config from a {@link MockEnvironment} — no Spring context is loaded.
 *
 * <p>Semantics under test: every discovered tenant id yields a descriptor, plus the {@code default}
 * alias once any tenant is configured; per-PT provider selection ({@code assigned}) has been
 * dropped.
 */
class PhysicalTenantScopeProviderTest {

  @Test
  void shouldEmitNoDescriptorsWhenNoPhysicalTenantsConfigured() {
    // The gate short-circuits before any per-tenant config is built, so nothing is emitted even
    // though the cluster declares an auth method. The alias stays reachable because
    // SecurityPathAdapter gives the cluster chain /physical-tenants/default-prefixed paths for
    // exactly this case.
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

    // Two explicit tenants + the implicit default alias.
    assertThat(descriptors).hasSize(3);
    final var basePaths = descriptors.stream().map(ScopedSecurityDescriptor::basePath).toList();
    assertThat(basePaths)
        .containsExactlyInAnyOrder(
            "/physical-tenants/tenanta", "/physical-tenants/pt2", "/physical-tenants/default");
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

    // The explicit tenant plus the implicit default alias.
    assertThat(provider.get())
        .extracting(ScopedSecurityDescriptor::basePath)
        .containsExactlyInAnyOrder("/physical-tenants/myidp", "/physical-tenants/default");
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

    // Both tenants yield descriptors (union semantics — no assigned check), plus the default alias.
    assertThat(descriptors).hasSize(3);
    final var basePaths = descriptors.stream().map(ScopedSecurityDescriptor::basePath).toList();
    assertThat(basePaths)
        .containsExactlyInAnyOrder(
            "/physical-tenants/pt1", "/physical-tenants/pt2", "/physical-tenants/default");
  }

  @Test
  void shouldEmitDefaultAliasDescriptorFromRootConfigWhenPtModeActive() {
    // The default tenant is not declared under physical-tenants.*; it must still be exposed as an
    // alias at /physical-tenants/default, carrying the root/cluster providers (the /v2 surface).
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.oidc.client-id", "root-client",
                "camunda.security.authentication.oidc.issuer-uri", "http://idp/root",
                "camunda.security.authentication.oidc.audiences[0]", "root-aud",
                // A non-default tenant activates PT scoping.
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.client-id",
                    "pt-tenanta-client"));

    final var provider = new PhysicalTenantScopeProvider(env);

    final var defaultAlias =
        provider.get().stream()
            .filter(d -> d.basePath().equals("/physical-tenants/default"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected a /physical-tenants/default alias"));

    // The alias carries the root default slot (the unprefixed /v2 surface), not a PT overlay.
    assertThat(defaultAlias.authentication().getOidc()).isNotNull();
    assertThat(defaultAlias.authentication().getOidc().getClientId()).isEqualTo("root-client");
    assertThat(defaultAlias.authentication().getOidc().getAudiences()).containsExactly("root-aud");
  }

  @Test
  void shouldFailStartupWhenPhysicalTenantConfigCannotBeBuilt() {
    // given — an invalid enum value for `method` under a PT overlay causes Binder to throw
    final var env =
        env(
            Map.of(
                "camunda.physical-tenants.badtenant.security.authentication.method",
                "NOT_A_VALID_METHOD"));

    // when / then
    assertThatThrownBy(() -> new PhysicalTenantScopeProvider(env))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("badtenant");
  }

  @Test
  void shouldFailStartupWhenDefaultTenantConfigCannotBeBuilt() {
    // given — keep the root config valid and activate PT mode via a non-default tenant, then put an
    // invalid value specifically under the default overlay prefix so only
    // forPhysicalTenant("default")
    // fails — ensuring the implicit default alias follows the same fail-fast rule independently.
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                // A non-default tenant activates PT mode so the default alias is attempted
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.client-id",
                    "pt-client",
                // Invalid value under the default overlay — triggers a Binder failure only when
                // forPhysicalTenant("default", env) is called
                "camunda.physical-tenants.default.security.authentication.method",
                    "NOT_A_VALID_METHOD"));

    assertThatThrownBy(() -> new PhysicalTenantScopeProvider(env))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("default");
  }

  static Stream<Arguments> ptConfiguredShapes() {
    return Stream.of(
        Arguments.of(
            "one explicit tenant",
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "tenanta-client",
                "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://idp/tenanta",
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.client-id",
                    "pt-tenanta-client")),
        Arguments.of(
            "explicit default alongside one tenant",
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "tenanta-client",
                "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://idp/tenanta",
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.client-id",
                    "pt-tenanta-client",
                "camunda.physical-tenants.default.security.authentication.method", "oidc")));
  }

  /**
   * Once any physical tenant is configured, every tenant the config layer knows about gets a
   * descriptor and nothing else does — including {@code default}, which this provider once omitted.
   *
   * <p>The two sides compute the same set here, so this is a drift guard rather than an independent
   * derivation: it fails if either layer changes which tenants it knows about without the other.
   */
  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("ptConfiguredShapes")
  void shouldEmitOneDescriptorPerKnownPhysicalTenant(
      final String shape, final Map<String, String> properties) {
    // given
    final var env = env(properties);

    // when
    final var descriptors = new PhysicalTenantScopeProvider(env).get();

    // then
    assertThat(descriptors)
        .extracting(ScopedSecurityDescriptor::basePath)
        .containsExactlyInAnyOrderElementsOf(
            PhysicalTenantAuthConfigurations.forAllPhysicalTenants(env).keySet().stream()
                .map(id -> PHYSICAL_TENANTS_PATH_SEGMENT + id)
                .toList());
  }

  private static MockEnvironment env(final Map<String, String> properties) {
    final MockEnvironment env = new MockEnvironment();
    properties.forEach(env::setProperty);
    return env;
  }
}
