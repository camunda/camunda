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
 * <p>Semantics under test: the returned config is the union of all cluster providers (root
 * providers ∪ PT overlay providers), then — for a non-default tenant that declares one — narrowed
 * to its {@code providers.assigned} selection (#54730). Per-PT OVERLAY (field overrides and PT-only
 * providers) is applied before narrowing.
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
  // 3b. PT overlay list REPLACES the root list (no index-merge inheritance)
  // -------------------------------------------------------------------------

  @Test
  void shouldReplaceRootListWhenPtDeclaresOwnList() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.tenanta.client-id", "root-client",
                "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://idp/tenanta",
                "camunda.security.authentication.providers.oidc.tenanta.audiences[0]", "root-aud-0",
                "camunda.security.authentication.providers.oidc.tenanta.audiences[1]", "root-aud-1",
                // PT overlay declares a single-element audiences list
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.audiences[0]",
                    "pt-aud"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", env);

    final var provider = cfg.getProviders().getOidc().get("tenanta");
    // PT list wins wholesale — root-aud-1 must NOT be inherited via index-merge.
    assertThat(provider.getAudiences()).containsExactly("pt-aud");
  }

  // -------------------------------------------------------------------------
  // 3c. PT-silent list INHERITS the root list
  // -------------------------------------------------------------------------

  @Test
  void shouldInheritRootListWhenPtOmitsIt() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.tenanta.client-id", "root-client",
                "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://idp/tenanta",
                "camunda.security.authentication.providers.oidc.tenanta.audiences[0]", "root-aud-0",
                "camunda.security.authentication.providers.oidc.tenanta.audiences[1]", "root-aud-1",
                // PT overlay overrides only client-id — says nothing about audiences
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.client-id",
                    "pt-client"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", env);

    final var provider = cfg.getProviders().getOidc().get("tenanta");
    assertThat(provider.getClientId()).isEqualTo("pt-client"); // overridden
    assertThat(provider.getAudiences()).containsExactly("root-aud-0", "root-aud-1"); // inherited
  }

  // -------------------------------------------------------------------------
  // 3d. A representative cluster shape: default slot "oidc" + a named provider "tenanta" whose name
  //     equals the PT id; the tenanta overlay overrides client/secret/audiences and inherits
  //     issuer-uri.
  // -------------------------------------------------------------------------

  @Test
  void shouldApplyTenantOverlayWithDefaultSlotAndSameNamedProvider() {
    final var env =
        env(
            Map.ofEntries(
                Map.entry("camunda.security.authentication.method", "oidc"),
                // default slot "oidc"
                Map.entry(
                    "camunda.security.authentication.oidc.client-id", "camunda-pt-default-client"),
                Map.entry(
                    "camunda.security.authentication.oidc.issuer-uri",
                    "http://localhost:8081/realms/default"),
                Map.entry("camunda.security.authentication.oidc.audiences[0]", "pt-default-aud"),
                // root view of named provider "tenanta"
                Map.entry(
                    "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "camunda-pt-default-via-tenanta-client"),
                Map.entry(
                    "camunda.security.authentication.providers.oidc.tenanta.client-secret",
                    "default-via-tenanta-secret"),
                Map.entry(
                    "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://localhost:8082/realms/tenanta"),
                Map.entry(
                    "camunda.security.authentication.providers.oidc.tenanta.audiences[0]",
                    "pt-default-via-tenanta-aud"),
                // tenanta PT overlay: override client/secret/audiences, inherit issuer-uri
                Map.entry(
                    "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.client-id",
                    "camunda-pt-tenanta-client"),
                Map.entry(
                    "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.client-secret",
                    "tenanta-secret"),
                Map.entry(
                    "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.audiences[0]",
                    "pt-tenanta-aud")));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", env);

    final var tenanta = cfg.getProviders().getOidc().get("tenanta");
    assertThat(tenanta).isNotNull();
    assertThat(tenanta.getAudiences()).containsExactly("pt-tenanta-aud"); // overlay wins
    assertThat(tenanta.getClientId()).isEqualTo("camunda-pt-tenanta-client"); // overlay wins
    assertThat(tenanta.getIssuerUri())
        .isEqualTo("http://localhost:8082/realms/tenanta"); // inherited from root
    // default slot intact
    assertThat(cfg.getOidc()).isNotNull();
    assertThat(cfg.getOidc().getAudiences()).containsExactly("pt-default-aud");
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
  // 6. Default slot with no client-id/issuer-uri carries no usable config
  // -------------------------------------------------------------------------

  @Test
  void shouldExposeEmptyDefaultSlotWhenNoFlatOidcConfigured() {
    // When authentication.* exists but no authentication.oidc.client-id/issuer-uri is set, the
    // default slot has no usable config. AuthenticationConfiguration.setOidc(null) coerces null to
    // a
    // fresh empty OidcConfiguration (the api setter never stores null), so getOidc() is non-null
    // but
    // carries neither client-id nor issuer-uri — exactly what CSL's flatten treats as "no default
    // provider". Assert that precise shape rather than a never-reachable null.
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                // Named provider only — no flat oidc.* slot
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.security.authentication.providers.oidc.a.issuer-uri", "http://idp/a"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("anytenant", env);

    assertThat(cfg.getOidc()).isNotNull();
    assertThat(cfg.getOidc().getClientId()).isNull();
    assertThat(cfg.getOidc().getIssuerUri()).isNull();
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
  // Real YAML-loaded environment (not MockEnvironment) — guards against a
  // MockEnvironment-vs-real-Environment binding discrepancy in the overlay merge.
  // -------------------------------------------------------------------------

  @Test
  void shouldApplyTenantOverlayAudienceFromYamlLoadedEnvironment() throws Exception {
    final String yaml =
        """
        camunda:
          security:
            authentication:
              method: oidc
              oidc:
                client-id: camunda-pt-default-client
                issuer-uri: http://localhost:8081/realms/default
                audiences: [pt-default-aud]
              providers:
                oidc:
                  tenanta:
                    client-id: camunda-pt-default-via-tenanta-client
                    client-secret: default-via-tenanta-secret
                    issuer-uri: http://localhost:8082/realms/tenanta
                    audiences: [pt-default-via-tenanta-aud]
          physical-tenants:
            tenanta:
              security:
                authentication:
                  providers:
                    oidc:
                      tenanta:
                        client-id: camunda-pt-tenanta-client
                        client-secret: tenanta-secret
                        audiences: [pt-tenanta-aud]
        """;

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", yamlEnv(yaml));

    final var tenanta = cfg.getProviders().getOidc().get("tenanta");
    assertThat(tenanta.getAudiences()).containsExactly("pt-tenanta-aud"); // overlay wins
    assertThat(tenanta.getIssuerUri())
        .isEqualTo("http://localhost:8082/realms/tenanta"); // inherited from root
    assertThat(tenanta.getClientId()).isEqualTo("camunda-pt-tenanta-client"); // overlay wins
  }

  // -------------------------------------------------------------------------
  // 7. providers.assigned narrows a non-default tenant to its selected providers (#54730)
  // -------------------------------------------------------------------------

  @Test
  void shouldNarrowToAssignedNamedProviderAndDropDefaultSlotAndUnlistedProvider() {
    // Cluster: a default slot + two named providers; tenanta is assigned ONLY the named "tenanta".
    // Expectation: the inherited root default slot is dropped (content-less → ignored by CSL
    // flatten), the unlisted "other" provider is removed, and only "tenanta" survives. This is the
    // cross-issuer acceptance behaviour: a root/default token no longer matches tenanta's chain.
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.oidc.client-id", "root-client",
                "camunda.security.authentication.oidc.issuer-uri", "http://idp/root",
                "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "client-tenanta",
                "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://idp/tenanta",
                "camunda.security.authentication.providers.oidc.other.client-id", "client-other",
                "camunda.security.authentication.providers.oidc.other.issuer-uri",
                    "http://idp/other",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "tenanta"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", env);

    // default slot dropped: present-but-content-less (the api setter never stores null).
    assertThat(cfg.getOidc()).isNotNull();
    assertThat(cfg.getOidc().getClientId()).isNull();
    assertThat(cfg.getOidc().getIssuerUri()).isNull();
    // only the assigned named provider survives.
    assertThat(cfg.getProviders().getOidc()).containsOnlyKeys("tenanta");
    assertThat(cfg.getProviders().getOidc().get("tenanta").getClientId())
        .isEqualTo("client-tenanta");
  }

  @Test
  void shouldKeepDefaultSlotWhenReservedOidcIdIsAssigned() {
    // assigned = [oidc, tenanta] → the reserved id "oidc" keeps the default slot alongside the
    // named provider; the unlisted "other" provider is still dropped.
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.oidc.client-id", "root-client",
                "camunda.security.authentication.oidc.issuer-uri", "http://idp/root",
                "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "client-tenanta",
                "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://idp/tenanta",
                "camunda.security.authentication.providers.oidc.other.client-id", "client-other",
                "camunda.security.authentication.providers.oidc.other.issuer-uri",
                    "http://idp/other",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "oidc",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[1]",
                    "tenanta"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", env);

    assertThat(cfg.getOidc().getClientId()).isEqualTo("root-client"); // default slot kept
    assertThat(cfg.getProviders().getOidc()).containsOnlyKeys("tenanta"); // "other" dropped
  }

  @Test
  void shouldNarrowDefaultTenantWhenAssignedDeclared() {
    // The default tenant is narrowed by its own assigned just like any other tenant. Its resolved
    // config also drives the cluster /v2 chain (see PhysicalTenantSecurityConfiguration), so a
    // default selection limits that surface too.
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.oidc.client-id", "root-client",
                "camunda.security.authentication.oidc.issuer-uri", "http://idp/root",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.security.authentication.providers.oidc.a.issuer-uri", "http://idp/a",
                "camunda.physical-tenants.default.security.authentication.providers.assigned[0]",
                    "a"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("default", env);

    // default slot dropped (oidc not assigned); only "a" kept
    assertThat(cfg.getOidc().getClientId()).isNull();
    assertThat(cfg.getProviders().getOidc()).containsOnlyKeys("a");
  }

  @Test
  void shouldKeepFullSetForDefaultTenantWhenNoAssignedDeclared() {
    // No assigned under default → full set (the common case; the alias and cluster surface keep all
    // providers).
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.oidc.client-id", "root-client",
                "camunda.security.authentication.oidc.issuer-uri", "http://idp/root",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.security.authentication.providers.oidc.a.issuer-uri", "http://idp/a"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("default", env);

    assertThat(cfg.getOidc().getClientId()).isEqualTo("root-client"); // default slot kept
    assertThat(cfg.getProviders().getOidc()).containsKey("a");
  }

  @Test
  void shouldKeepFullUnionWhenNoAssignedDeclared() {
    // A non-default tenant with no assigned list bound keeps the full union (selection is enforced
    // by the configuration layer, not this merge).
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.oidc.client-id", "root-client",
                "camunda.security.authentication.oidc.issuer-uri", "http://idp/root",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.security.authentication.providers.oidc.a.issuer-uri", "http://idp/a"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", env);

    assertThat(cfg.getOidc().getClientId()).isEqualTo("root-client");
    assertThat(cfg.getProviders().getOidc()).containsKey("a");
  }

  @Test
  void shouldIgnoreBlankAssignedEntryWhenNarrowing() {
    // Defensive: a blank entry in assigned must not break narrowing (it matches no provider and is
    // dropped). The configuration layer rejects blank entries at startup; this guards the merge in
    // case it runs first (e.g. via the cluster-unification BeanPostProcessor).
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.security.authentication.providers.oidc.a.issuer-uri", "http://idp/a",
                "camunda.security.authentication.providers.oidc.b.client-id", "client-b",
                "camunda.security.authentication.providers.oidc.b.issuer-uri", "http://idp/b",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[1]",
                    "a"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", env);

    // blank ignored; only the real id "a" is kept, "b" dropped — and no exception thrown.
    assertThat(cfg.getProviders().getOidc()).containsOnlyKeys("a");
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static MockEnvironment env(final Map<String, String> properties) {
    final MockEnvironment env = new MockEnvironment();
    properties.forEach(env::setProperty);
    return env;
  }

  private static org.springframework.core.env.Environment yamlEnv(final String yaml)
      throws java.io.IOException {
    final var loaded =
        new org.springframework.boot.env.YamlPropertySourceLoader()
            .load(
                "test",
                new org.springframework.core.io.ByteArrayResource(
                    yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    final var env = new org.springframework.core.env.StandardEnvironment();
    loaded.forEach(env.getPropertySources()::addFirst);
    return env;
  }
}
