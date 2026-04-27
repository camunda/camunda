/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.security.CamundaIdentityConfiguration.CamundaIdentityProperties;
import io.camunda.application.commons.security.CamundaIdentityConfiguration.CamundaPhysicalTenantsProperties;
import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

public class CamundaIdentityConfigurationTest {

  @Test
  public void shouldBindPhysicalTenantsFromYaml() {
    // given
    final Map<String, Object> yaml = new HashMap<>();
    yaml.put("camunda.physical-tenants[0].id", "default");
    yaml.put("camunda.physical-tenants[0].name", "Default");
    yaml.put(
        "camunda.physical-tenants[0].security.initialization.roles[0].role-id",
        "default-engine-admin");
    yaml.put(
        "camunda.physical-tenants[0].security.initialization.roles[0].name",
        "Default Engine Admin");
    yaml.put("camunda.physical-tenants[1].id", "risk-production");
    yaml.put("camunda.physical-tenants[1].name", "Risk Team Production");

    final var source = new MapConfigurationPropertySource(yaml);
    final var binder = new Binder(source);

    // when
    final var bound = binder.bind("camunda", CamundaPhysicalTenantsProperties.class).get();

    // then
    assertThat(bound.getPhysicalTenants()).hasSize(2);

    final var defaultTenant = bound.getPhysicalTenants().get(0);
    assertThat(defaultTenant.getId()).isEqualTo("default");
    assertThat(defaultTenant.getName()).isEqualTo("Default");
    assertThat(defaultTenant.getSecurity().getInitialization().getRoles()).hasSize(1);
    assertThat(defaultTenant.getSecurity().getInitialization().getRoles().get(0).roleId())
        .isEqualTo("default-engine-admin");

    assertThat(bound.getPhysicalTenants().get(1).getId()).isEqualTo("risk-production");
  }

  @Test
  public void shouldBindEngineIdpAssignmentsFromYaml() {
    // given
    final Map<String, Object> yaml = new HashMap<>();
    yaml.put("camunda.identity.engine-idp-assignments.default[0]", "default");
    yaml.put("camunda.identity.engine-idp-assignments.risk-production[0]", "default");
    yaml.put("camunda.identity.engine-idp-assignments.risk-production[1]", "provider-a");

    final var source = new MapConfigurationPropertySource(yaml);
    final var binder = new Binder(source);

    // when
    final var bound = binder.bind("camunda.identity", CamundaIdentityProperties.class).get();

    // then
    assertThat(bound.getEngineIdpAssignments()).containsKeys("default", "risk-production");
    assertThat(bound.getEngineIdpAssignments().get("default")).containsExactly("default");
    assertThat(bound.getEngineIdpAssignments().get("risk-production"))
        .containsExactly("default", "provider-a");
  }

  @Test
  public void shouldDefaultToEmptyCollectionsWhenNothingConfigured() {
    // given
    final var source = new MapConfigurationPropertySource(new HashMap<>());
    final var binder = new Binder(source);

    // when
    final var physicalTenants =
        binder
            .bind("camunda", CamundaPhysicalTenantsProperties.class)
            .orElse(new CamundaPhysicalTenantsProperties());
    final var identity =
        binder
            .bind("camunda.identity", CamundaIdentityProperties.class)
            .orElse(new CamundaIdentityProperties());

    // then
    assertThat(physicalTenants.getPhysicalTenants()).isEmpty();
    assertThat(identity.getEngineIdpAssignments()).isEmpty();
  }

  @Test
  public void shouldBindMultipleTenantsWithSharedProvider() {
    // given — same OIDC provider can serve multiple physical tenants
    final Map<String, Object> yaml = new HashMap<>();
    yaml.put("camunda.identity.engine-idp-assignments.default[0]", "shared-idp");
    yaml.put("camunda.identity.engine-idp-assignments.risk-production[0]", "shared-idp");

    final var source = new MapConfigurationPropertySource(yaml);
    final var binder = new Binder(source);

    // when
    final var bound = binder.bind("camunda.identity", CamundaIdentityProperties.class).get();

    // then
    assertThat(bound.getEngineIdpAssignments().get("default")).containsExactly("shared-idp");
    assertThat(bound.getEngineIdpAssignments().get("risk-production"))
        .containsExactly("shared-idp");
  }

  @Test
  public void shouldWireRegistryFromIdentityProperties() {
    // given
    final var properties = new CamundaIdentityProperties();
    properties.setEngineIdpAssignments(
        Map.of(
            "default", List.of("default"),
            "risk-production", List.of("default", "provider-a")));

    final var config = new CamundaIdentityConfiguration();

    // when
    final PhysicalTenantIdpRegistry registry = config.physicalTenantIdpRegistry(properties);

    // then
    assertThat(registry.getIdpsForTenant("risk-production"))
        .containsExactly("default", "provider-a");
    assertThat(registry.getTenantsForIdp("default"))
        .containsExactlyInAnyOrder("default", "risk-production");
  }
}
