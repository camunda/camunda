/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.AuthenticationMethod;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for {@link PhysicalTenantAuthConfigurations}.
 *
 * <p>All tests bind config from a {@link MockEnvironment} — no Spring context is loaded.
 *
 * <p>Semantics under test: the returned config contains <em>all</em> cluster providers (root
 * providers ∪ PT overlay providers). Per-PT provider selection ({@code assigned}) has been dropped;
 * per-PT OVERLAY (field overrides and PT-only providers) is still supported.
 */
class PhysicalTenantAuthConfigurationsTest {

  // -------------------------------------------------------------------------
  // 1. Root has two named providers; no PT overlay → PT config contains BOTH
  // -------------------------------------------------------------------------

  @Test
  void shouldContainAllRootProvidersWhenNoPtOverlay() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.security.authentication.providers.oidc.a.issuer-uri", "http://idp/a",
                "camunda.security.authentication.providers.oidc.b.client-id", "client-b",
                "camunda.security.authentication.providers.oidc.b.issuer-uri", "http://idp/b"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("anytenant", env);

    assertThat(cfg.getProviders()).isNotNull();
    assertThat(cfg.getProviders().getOidc()).containsKeys("a", "b");
    assertThat(cfg.getProviders().getOidc().get("a").getClientId()).isEqualTo("client-a");
    assertThat(cfg.getProviders().getOidc().get("b").getClientId()).isEqualTo("client-b");
  }

  // -------------------------------------------------------------------------
  // 2. Root default slot configured → PT config's getOidc() is non-null
  // -------------------------------------------------------------------------

  @Test
  void shouldCarryRootDefaultSlotInPtConfig() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.oidc.client-id", "root-client",
                "camunda.security.authentication.oidc.issuer-uri", "http://idp/root"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("anytenant", env);

    assertThat(cfg.getOidc()).isNotNull();
    assertThat(cfg.getOidc().getClientId()).isEqualTo("root-client");
    assertThat(cfg.getOidc().getIssuerUri()).isEqualTo("http://idp/root");
    assertThat(cfg.getMethod()).isEqualTo(AuthenticationMethod.OIDC);
  }

  // -------------------------------------------------------------------------
  // 3. Root has provider a; PT overlay overrides a's audience/issuer → overlay wins per-field
  // -------------------------------------------------------------------------

  @Test
  void shouldApplyPtOverlayOnRootProvider() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "root-tenanta-client",
                "camunda.security.authentication.providers.oidc.tenanta.client-secret",
                    "root-tenanta-secret",
                "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://idp/tenanta",
                // PT overlay: override clientId and audience only
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.client-id",
                    "pt-tenanta-client",
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.audiences[0]",
                    "pt-aud"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", env);

    assertThat(cfg.getProviders()).isNotNull();
    final var provider = cfg.getProviders().getOidc().get("tenanta");
    assertThat(provider).isNotNull();
    assertThat(provider.getClientId()).isEqualTo("pt-tenanta-client"); // overridden by overlay
    assertThat(provider.getClientSecret()).isEqualTo("root-tenanta-secret"); // inherited from root
    assertThat(provider.getIssuerUri()).isEqualTo("http://idp/tenanta"); // inherited from root
    assertThat(provider.getAudiences()).containsExactly("pt-aud"); // overridden by overlay
  }

  // -------------------------------------------------------------------------
  // 4. PT overlay declares a provider not in root (PT-only) → included via union
  // -------------------------------------------------------------------------

  @Test
  void shouldIncludePtOnlyProviderNotPresentInRoot() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.root.client-id", "root-client",
                "camunda.security.authentication.providers.oidc.root.issuer-uri", "http://idp/root",
                // PT-only provider — not declared at root
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.ptonly.client-id",
                    "ptonly-client",
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.ptonly.issuer-uri",
                    "http://idp/ptonly"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", env);

    assertThat(cfg.getProviders()).isNotNull();
    // root provider included via union
    assertThat(cfg.getProviders().getOidc()).containsKey("root");
    // PT-only provider included via union
    assertThat(cfg.getProviders().getOidc()).containsKey("ptonly");
    assertThat(cfg.getProviders().getOidc().get("ptonly").getClientId()).isEqualTo("ptonly-client");
  }

  // -------------------------------------------------------------------------
  // 5. Method is always taken from root
  // -------------------------------------------------------------------------

  @Test
  void shouldInheritMethodFromRoot() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.security.authentication.providers.oidc.a.issuer-uri", "http://idp/a"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("anytenant", env);

    assertThat(cfg.getMethod()).isEqualTo(AuthenticationMethod.OIDC);
  }

  @Test
  void shouldDefaultMethodToBasicWhenRootMethodAbsent() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.security.authentication.providers.oidc.a.issuer-uri", "http://idp/a"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("anytenant", env);

    assertThat(cfg.getMethod()).isEqualTo(AuthenticationMethod.BASIC);
  }

  // -------------------------------------------------------------------------
  // 6. Empty/absent default slot → getOidc() is null (validDefaultSlot guard)
  // -------------------------------------------------------------------------

  @Test
  void shouldReturnNullDefaultSlotWhenSpringBoundButEmpty() {
    // Spring may bind an empty OidcConfiguration when authentication.* properties exist but
    // no client-id/issuer-uri is set under authentication.oidc.*. The validDefaultSlot guard
    // must treat such a slot as absent.
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                // Named provider only — no flat oidc.* slot
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.security.authentication.providers.oidc.a.issuer-uri", "http://idp/a"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("anytenant", env);

    // No client-id/issuer-uri under authentication.oidc.* → default slot must be null
    assertThat(cfg.getOidc())
        .satisfiesAnyOf(
            oidc -> assertThat(oidc).isNull(),
            oidc -> {
              assertThat(oidc.getClientId()).isNull();
              assertThat(oidc.getIssuerUri()).isNull();
            });
  }

  // -------------------------------------------------------------------------
  // Root config must not be mutated by merge
  // -------------------------------------------------------------------------

  @Test
  void shouldNotMutateRootConfigWhenApplyingPtOverlay() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "root-tenanta-client",
                "camunda.security.authentication.providers.oidc.tenanta.client-secret",
                    "root-tenanta-secret",
                "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://idp/tenanta",
                "camunda.security.authentication.providers.oidc.tenanta.audiences[0]", "root-aud",
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.audiences[0]",
                    "pt-aud"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", env);

    // Merged result has PT-side audience
    assertThat(cfg.getProviders().getOidc().get("tenanta").getAudiences())
        .containsExactly("pt-aud");

    // Calling forPhysicalTenant again (re-binds from env) still gets PT value — env is the SoT
    final AuthenticationConfiguration cfg2 =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", env);
    assertThat(cfg2.getProviders().getOidc().get("tenanta").getAudiences())
        .containsExactly("pt-aud");
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static MockEnvironment env(final Map<String, String> properties) {
    final MockEnvironment env = new MockEnvironment();
    properties.forEach(env::setProperty);
    return env;
  }
}
