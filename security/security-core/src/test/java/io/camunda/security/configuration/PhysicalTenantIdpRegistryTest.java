/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class PhysicalTenantIdpRegistryTest {

  @Test
  public void shouldReturnIdpsForKnownTenant() {
    // given
    final var registry =
        new PhysicalTenantIdpRegistry(
            Map.of(
                "default", List.of("default"),
                "risk-production", List.of("default", "provider-a")));

    // then
    assertThat(registry.getIdpsForTenant("default")).containsExactly("default");
    assertThat(registry.getIdpsForTenant("risk-production"))
        .containsExactly("default", "provider-a");
  }

  @Test
  public void shouldReturnTenantsForIdpInReverse() {
    // given
    final var registry =
        new PhysicalTenantIdpRegistry(
            Map.of(
                "default", List.of("default"),
                "risk-production", List.of("default", "provider-a")));

    // then
    assertThat(registry.getTenantsForIdp("default"))
        .containsExactlyInAnyOrder("default", "risk-production");
    assertThat(registry.getTenantsForIdp("provider-a")).containsExactly("risk-production");
  }

  @Test
  public void shouldReturnEmptyListForUnknownTenantOrIdp() {
    // given
    final var registry = new PhysicalTenantIdpRegistry(Map.of("default", List.of("default")));

    // then — null-safe, returns empty list rather than null
    assertThat(registry.getIdpsForTenant("does-not-exist")).isEmpty();
    assertThat(registry.getTenantsForIdp("does-not-exist")).isEmpty();
  }

  @Test
  public void shouldHandleEmptyOrNullInput() {
    // given — both null and empty map are valid (deployment with no assignments yet)
    final var fromNull = new PhysicalTenantIdpRegistry(null);
    final var fromEmpty = new PhysicalTenantIdpRegistry(Map.of());

    // then
    assertThat(fromNull.getIdpsForTenant("anything")).isEmpty();
    assertThat(fromNull.getTenantsForIdp("anything")).isEmpty();
    assertThat(fromEmpty.getIdpsForTenant("anything")).isEmpty();
    assertThat(fromEmpty.getTenantsForIdp("anything")).isEmpty();
  }

  @Test
  public void shouldNotReflectMutationsToInputMap() {
    // given
    final Map<String, List<String>> mutableInput = new HashMap<>();
    final List<String> mutableIdps = new ArrayList<>(List.of("default"));
    mutableInput.put("risk-production", mutableIdps);

    final var registry = new PhysicalTenantIdpRegistry(mutableInput);

    // when — caller mutates input after construction
    mutableInput.put("rogue-tenant", List.of("rogue-idp"));
    mutableIdps.add("provider-a");

    // then — registry is unaffected
    assertThat(registry.getIdpsForTenant("rogue-tenant")).isEmpty();
    assertThat(registry.getIdpsForTenant("risk-production")).containsExactly("default");
  }

  @Test
  public void shouldReturnImmutableLookupResults() {
    // given
    final var registry = new PhysicalTenantIdpRegistry(Map.of("default", List.of("default")));

    // then — caller cannot mutate stored lists
    assertThatThrownBy(() -> registry.getIdpsForTenant("default").add("hacker-idp"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> registry.getTenantsForIdp("default").add("hacker-tenant"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void shouldExposeAllConfiguredTenantIds() {
    // given
    final var registry =
        new PhysicalTenantIdpRegistry(
            Map.of(
                "default", List.of("default"),
                "risk-production", List.of("default", "provider-a")));

    // then
    assertThat(registry.tenantIds()).containsExactlyInAnyOrder("default", "risk-production");
  }
}
