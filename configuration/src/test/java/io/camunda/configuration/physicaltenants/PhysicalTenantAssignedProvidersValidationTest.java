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

  @Test
  void shouldRejectNamedOidcProviderEvenWhenDefaultOmitsAssigned() {
    // given a named provider "oidc" and only the default tenant, which omits assigned (implicit
    // full set) — the collision must still fail fast, not be deferred until a selection is declared
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.oidc.client-id", "client-oidc",
                // a key under physical-tenants.default so the default tenant is discovered, with no
                // providers.assigned
                "camunda.physical-tenants.default.security.authentication.providers.oidc.x.client-id",
                    "client-x"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .withMessageContaining("default")
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

  @Test
  void shouldRejectIssuerCollisionWhenNoPhysicalTenantsConfigured() {
    // given an OIDC cluster with no physical tenants and two root-level providers sharing the same
    // issuer — the synthesized default tenant uses the full set, so the collision must be caught
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.issuer-uri",
                    "https://idp.example.com",
                "camunda.security.authentication.providers.oidc.b.issuer-uri",
                    "https://idp.example.com"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .withMessageContaining("default")
        .withMessageContaining("same issuer URI")
        .withMessageContaining("https://idp.example.com");
  }

  // -------------------------------------------------------------------------
  // Issuer URI collision: assigned list
  // -------------------------------------------------------------------------

  @Test
  void shouldRejectTwoNamedProvidersWithSameIssuerInAssigned() {
    // given two named providers sharing the same issuer-uri, both in a tenant's assigned list
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.issuer-uri",
                    "https://idp.example.com",
                "camunda.security.authentication.providers.oidc.b.issuer-uri",
                    "https://idp.example.com",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "a",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[1]",
                    "b"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("same issuer URI")
        .withMessageContaining("https://idp.example.com");
  }

  @Test
  void shouldRejectDefaultSlotAndNamedProviderSharingIssuerInAssigned() {
    // given the default slot and a named provider both configured with the same issuer-uri,
    // and a tenant assigning both via the reserved "oidc" id and the named id
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.oidc.issuer-uri", "https://idp.example.com",
                "camunda.security.authentication.providers.oidc.a.issuer-uri",
                    "https://idp.example.com",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "oidc",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[1]",
                    "a"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .withMessageContaining("tenanta")
        .withMessageContaining("same issuer URI")
        .withMessageContaining("https://idp.example.com");
  }

  @Test
  void shouldAcceptAssignedProvidersWithDistinctIssuers() {
    // given two named providers with different issuers, both in a tenant's assigned list
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.issuer-uri",
                    "https://idp-a.example.com",
                "camunda.security.authentication.providers.oidc.b.issuer-uri",
                    "https://idp-b.example.com",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "a",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[1]",
                    "b"));

    // when / then
    assertThatCode(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptWhenTwoProvidersShareIssuerButOnlyOneIsAssigned() {
    // given two cluster-level providers sharing the same issuer, but each tenant assigning only
    // one — the collision check is scoped to each tenant's effective set, not the root set.
    // The default tenant must also declare an explicit assigned to avoid seeing both colliding
    // providers in its implicit full set.
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.issuer-uri",
                    "https://idp.example.com",
                "camunda.security.authentication.providers.oidc.b.issuer-uri",
                    "https://idp.example.com",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "a",
                // constrain the default tenant too so it does not see both colliding providers
                "camunda.physical-tenants.default.security.authentication.providers.assigned[0]",
                    "a"));

    // when / then — no collision in the effective assigned set for either tenant
    assertThatCode(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldNotReportFalseCollisionForDuplicateIdInAssigned() {
    // given a single named provider whose id appears twice in assigned (a config typo) — the same
    // provider id resolves to the same issuer once, so there is no real collision
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.issuer-uri",
                    "https://idp.example.com",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "a",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[1]",
                    "a"));

    // when / then — deduplication prevents a false issuer-collision report
    assertThatCode(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptAssignedProvidersWithNullIssuers() {
    // given assigned providers that have no issuer-uri configured (null issuers are not a
    // collision)
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.client-id", "client-a",
                "camunda.security.authentication.providers.oidc.b.client-id", "client-b",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "a",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[1]",
                    "b"));

    // when / then
    assertThatCode(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRespectTenantOverlayIssuerWhenCheckingCollisions() {
    // given root providers "a" and "b" sharing the same issuer — a collision at root level.
    // tenanta overrides "a" to a distinct issuer via its PT overlay, so its effective set has no
    // collision. The default tenant is constrained to a single-provider assigned so it does not
    // see the root-level collision.
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.issuer-uri",
                    "https://idp.example.com",
                "camunda.security.authentication.providers.oidc.b.issuer-uri",
                    "https://idp.example.com",
                // tenanta overrides provider "a" to a different issuer
                "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.a.issuer-uri",
                    "https://other-idp.example.com",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]",
                    "a",
                "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[1]",
                    "b",
                // constrain the default tenant so it does not see both colliding root providers
                "camunda.physical-tenants.default.security.authentication.providers.assigned[0]",
                    "a"));

    // when / then — overlay makes the effective issuers distinct for tenanta
    assertThatCode(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  // -------------------------------------------------------------------------
  // Issuer URI collision: default tenant with implicit full set (no assigned)
  // -------------------------------------------------------------------------

  @Test
  void shouldRejectDefaultTenantWithoutAssignedWhenFullSetHasIssuerCollision() {
    // given two cluster-level named providers sharing the same issuer, and the default tenant
    // omitting providers.assigned (implicit full set) — the collision must fail fast
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.issuer-uri",
                    "https://idp.example.com",
                "camunda.security.authentication.providers.oidc.b.issuer-uri",
                    "https://idp.example.com",
                // trigger default tenant discovery with no assigned
                "camunda.physical-tenants.default.security.authentication.providers.oidc.a.client-id",
                    "client-a"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .withMessageContaining("default")
        .withMessageContaining("same issuer URI")
        .withMessageContaining("https://idp.example.com");
  }

  @Test
  void shouldAcceptDefaultTenantWithoutAssignedWhenFullSetHasDistinctIssuers() {
    // given two cluster-level named providers with distinct issuers, default tenant with no
    // assigned
    final Environment environment =
        environmentWith(
            Map.of(
                "camunda.security.authentication.method", "oidc",
                "camunda.security.authentication.providers.oidc.a.issuer-uri",
                    "https://idp-a.example.com",
                "camunda.security.authentication.providers.oidc.b.issuer-uri",
                    "https://idp-b.example.com",
                "camunda.physical-tenants.default.security.authentication.providers.oidc.a.client-id",
                    "client-a"));

    // when / then
    assertThatCode(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectTenantIdExceeding64Characters() {
    // given a tenant id one character over the shared length limit — the only discovery-reachable
    // invalid case, since Form.UNIFORM normalizes any format-invalid segment (e.g. dashes/upper
    // case) into a valid id before the shared validation runs
    final String tooLong = "a".repeat(65);
    final Environment environment =
        environmentWith(Map.of("camunda.physical-tenants." + tooLong + ".cluster.size", 4));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantAssignedProvidersValidation.validate(environment))
        .withMessageContaining("Invalid physical tenant id");
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
