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
import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

public class CamundaIdentityConfigurationTest {

  @Test
  public void shouldBindEngineIdpAssignmentsFromYaml() {
    // given
    final Map<String, Object> yaml = new HashMap<>();
    yaml.put("camunda.identity.engine-idp-assignments.default-engine[0]", "default");
    yaml.put("camunda.identity.engine-idp-assignments.risk-production[0]", "default");
    yaml.put("camunda.identity.engine-idp-assignments.risk-production[1]", "provider-a");

    final var source = new MapConfigurationPropertySource(yaml);
    final var binder = new Binder(source);

    // when
    final var bound = binder.bind("camunda.identity", CamundaIdentityProperties.class).get();

    // then
    assertThat(bound.getEngineIdpAssignments()).containsKeys("default-engine", "risk-production");
    assertThat(bound.getEngineIdpAssignments().get("default-engine")).containsExactly("default");
    assertThat(bound.getEngineIdpAssignments().get("risk-production"))
        .containsExactly("default", "provider-a");
  }

  @Test
  public void shouldDefaultToEmptyAssignmentsWhenNothingConfigured() {
    // given
    final var source = new MapConfigurationPropertySource(new HashMap<>());
    final var binder = new Binder(source);

    // when
    final var identity =
        binder
            .bind("camunda.identity", CamundaIdentityProperties.class)
            .orElse(new CamundaIdentityProperties());

    // then
    assertThat(identity.getEngineIdpAssignments()).isEmpty();
  }

  @Test
  public void shouldBindMultipleTenantsWithSharedProvider() {
    // given — same OIDC provider can serve multiple physical tenants
    final Map<String, Object> yaml = new HashMap<>();
    yaml.put("camunda.identity.engine-idp-assignments.default-engine[0]", "shared-idp");
    yaml.put("camunda.identity.engine-idp-assignments.risk-production[0]", "shared-idp");

    final var source = new MapConfigurationPropertySource(yaml);
    final var binder = new Binder(source);

    // when
    final var bound = binder.bind("camunda.identity", CamundaIdentityProperties.class).get();

    // then
    assertThat(bound.getEngineIdpAssignments().get("default-engine")).containsExactly("shared-idp");
    assertThat(bound.getEngineIdpAssignments().get("risk-production"))
        .containsExactly("shared-idp");
  }

  @Test
  public void shouldWireRegistryFromIdentityProperties() {
    // given
    final var properties = new CamundaIdentityProperties();
    properties.setEngineIdpAssignments(
        Map.of(
            "default-engine", List.of("default"),
            "risk-production", List.of("default", "provider-a")));

    final var config = new CamundaIdentityConfiguration();

    // when
    final PhysicalTenantIdpRegistry registry = config.physicalTenantIdpRegistry(properties);

    // then
    assertThat(registry.getIdpsForTenant("risk-production"))
        .containsExactly("default", "provider-a");
    assertThat(registry.tenantIds()).containsExactlyInAnyOrder("default-engine", "risk-production");
  }
}
