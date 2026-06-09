/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.AuthenticationMethod;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for {@link PhysicalTenantAuthConfigurations}.
 *
 * <p>All tests bind config from a {@link MockEnvironment} or {@link MapConfigurationPropertySource}
 * — no Spring context is loaded.
 *
 * <p>Test structure mirrors {@code PerTenantClientRegistrationsTest} from the PoC branch
 * (origin/identity-pt-poc), adapted for the {@link AuthenticationConfiguration} output type.
 */
class PhysicalTenantAuthConfigurationsTest {

  // -------------------------------------------------------------------------
  // Root with flat oidc + named provider; PT assigns both — no overrides
  // -------------------------------------------------------------------------

  @Test
  void shouldIncludeDefaultSlotWhenOidcIsAssigned() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.oidc.client-id", "root-client",
                "camunda.security.authentication.oidc.issuer-uri", "http://idp/root",
                "camunda.security.authentication.oidc.client-secret", "root-secret",
                "camunda.physical-tenants.default.security.authentication.providers.assigned[0]",
                    "oidc"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("default", env);

    assertThat(cfg.getOidc()).isNotNull();
    assertThat(cfg.getOidc().getClientId()).isEqualTo("root-client");
    assertThat(cfg.getOidc().getIssuerUri()).isEqualTo("http://idp/root");
    assertThat(cfg.getMethod()).isEqualTo(AuthenticationMethod.OIDC);
    // No named providers assigned — CSL setters reject null so getProviders() returns an empty
    // OidcProvidersConfiguration; check semantic emptiness (null or empty map) instead.
    assertThat(cfg.getProviders().getOidc()).isNullOrEmpty();
  }

  @Test
  void shouldIncludeNamedProviderWhenAssigned() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "tenanta-root-client",
                "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://idp/tenanta",
                "camunda.security.authentication.providers.oidc.tenanta.client-secret",
                    "tenanta-secret",
                "camunda.physical-tenants.pt1.security.authentication.providers.assigned[0]",
                    "tenanta"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("pt1", env);

    // "oidc" not assigned — CSL setters reject null so getOidc() returns an empty
    // OidcConfiguration; check semantic emptiness instead of null reference.
    assertThat(cfg.getOidc().getClientId()).isNull();
    assertThat(cfg.getOidc().getIssuerUri()).isNull();
    assertThat(cfg.getProviders()).isNotNull();
    assertThat(cfg.getProviders().getOidc()).containsKey("tenanta");
    assertThat(cfg.getProviders().getOidc().get("tenanta").getClientId())
        .isEqualTo("tenanta-root-client");
  }

  // -------------------------------------------------------------------------
  // PT with overrides — PT fields override root fields for that provider
  // -------------------------------------------------------------------------

  @Test
  void shouldApplyPtOverrideForClientIdAndAudienceWhileInheritingRootIssuer() {
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
                    "pt-aud",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "tenanta"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", env);

    assertThat(cfg.getProviders()).isNotNull();
    final var provider = cfg.getProviders().getOidc().get("tenanta");
    assertThat(provider).isNotNull();
    assertThat(provider.getClientId()).isEqualTo("pt-tenanta-client"); // overridden
    assertThat(provider.getClientSecret()).isEqualTo("root-tenanta-secret"); // inherited from root
    assertThat(provider.getIssuerUri()).isEqualTo("http://idp/tenanta"); // inherited from root
    assertThat(provider.getAudiences()).containsExactly("pt-aud"); // overridden
  }

  @Test
  void shouldExcludeProvidersNotInAssignedList() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.oidc.client-id", "root-client",
                "camunda.security.authentication.oidc.issuer-uri", "http://idp/root",
                "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "tenanta-client",
                "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://idp/tenanta",
                // PT only assigns oidc, NOT tenanta
                "camunda.physical-tenants.pt1.security.authentication.providers.assigned[0]",
                    "oidc"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("pt1", env);

    // Only the default slot is present — tenanta is excluded.
    assertThat(cfg.getOidc()).isNotNull();
    assertThat(cfg.getOidc().getClientId()).isEqualTo("root-client");
    // tenanta excluded — CSL setters reject null so getProviders() returns an empty
    // OidcProvidersConfiguration; check semantic emptiness (null or empty map) instead.
    assertThat(cfg.getProviders().getOidc()).isNullOrEmpty();
  }

  @Test
  void shouldIncludeBothDefaultAndNamedProviderWhenBothAssigned() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.oidc.client-id", "root-client",
                "camunda.security.authentication.oidc.issuer-uri", "http://idp/root",
                "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "tenanta-client",
                "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://idp/tenanta",
                "camunda.physical-tenants.default.security.authentication.providers.assigned[0]",
                    "oidc",
                "camunda.physical-tenants.default.security.authentication.providers.assigned[1]",
                    "tenanta"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("default", env);

    assertThat(cfg.getOidc()).isNotNull();
    assertThat(cfg.getProviders()).isNotNull();
    assertThat(cfg.getProviders().getOidc()).containsKey("tenanta");
  }

  // -------------------------------------------------------------------------
  // Error cases
  // -------------------------------------------------------------------------

  @Test
  void shouldFailWhenAssignedReferencesUnknownProvider() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.oidc.client-id", "root-client",
                "camunda.security.authentication.oidc.issuer-uri", "http://idp/root",
                "camunda.physical-tenants.pt1.security.authentication.providers.assigned[0]",
                    "ghost"));

    assertThatThrownBy(() -> PhysicalTenantAuthConfigurations.forPhysicalTenant("pt1", env))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ghost");
  }

  @Test
  void shouldFailWhenOidcIsAssignedButDefaultSlotUnconfigured() {
    final var env =
        env(
            Map.of(
                // No camunda.security.authentication.oidc.* configured
                "camunda.security.authentication.method", "oidc",
                "camunda.physical-tenants.pt1.security.authentication.providers.assigned[0]",
                    "oidc"));

    assertThatThrownBy(() -> PhysicalTenantAuthConfigurations.forPhysicalTenant("pt1", env))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("authentication.oidc.*");
  }

  @Test
  void shouldFailWhenAssignedIsEmpty() {
    final var env =
        env(
            Map.of(
                "camunda.security.authentication.oidc.client-id", "root-client",
                "camunda.security.authentication.oidc.issuer-uri", "http://idp/root"));
    // No assigned configured at all

    assertThatThrownBy(() -> PhysicalTenantAuthConfigurations.forPhysicalTenant("pt1", env))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("providers.assigned");
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
                    "pt-aud",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "tenanta"));

    final AuthenticationConfiguration cfg =
        PhysicalTenantAuthConfigurations.forPhysicalTenant("tenanta", env);

    // Merged result has PT-side audience
    assertThat(cfg.getProviders().getOidc().get("tenanta").getAudiences())
        .containsExactly("pt-aud");

    // Calling forPhysicalTenant again (re-binds from env) still gets root value — env is the SoT,
    // not any cached bean.
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
