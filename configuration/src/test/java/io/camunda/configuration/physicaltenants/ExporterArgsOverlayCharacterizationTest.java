/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Exporter;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Spec test for the per-tenant exporter args deep-merge (issue #55155).
 *
 * <p>Seed: root {@code myexp} with {@code className=X}, {@code args.a=1}, {@code args.b=2}; tenant
 * {@code tenanta} overrides only {@code args.b=99}. Expected outcome after {@link
 * PhysicalTenantResolver#of}: {@code className} inherited from root ({@code "X"}) and {@code args
 * == {a=1, b=99}} (deep-merged).
 *
 * <p>This test was previously a characterization test pinning Spring's native REPLACE behavior. It
 * now serves as the regression guard for the custom deep-merge overlay implemented in {@link
 * ExporterArgsOverlay}.
 */
class ExporterArgsOverlayCharacterizationTest {

  private MockEnvironment environment;

  @BeforeEach
  void setUp() {
    environment = new MockEnvironment();
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldDeepMergeExporterArgsBetweenRootAndTenant() {
    // given a root exporter with a className and two args, and a tenant overriding only args.b
    final Map<String, Object> properties = new HashMap<>();
    properties.put("camunda.data.exporters.myexp.class-name", "X");
    properties.put("camunda.data.exporters.myexp.args.a", 1);
    properties.put("camunda.data.exporters.myexp.args.b", 2);
    properties.put("camunda.physical-tenants.tenanta.data.exporters.myexp.args.b", 99);
    // distinct storage location so the tenant does not collide with the synthesized 'default'
    // (cross-tenant isolation now runs inside PhysicalTenantResolver.of())
    properties.put(
        "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
        "tenanta");
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));

    final Camunda camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));

    // when the tenant configuration is resolved
    final Camunda tenantA =
        PhysicalTenantResolver.of(environment, camunda).forPhysicalTenant("tenanta");

    // then the tenant's exporter entry deep-merges onto the root entry
    final Exporter myexp = tenantA.getData().getExporters().get("myexp");
    assertThat(myexp).as("the exporter entry itself survives the overlay").isNotNull();
    // className is inherited from root because the tenant did not set it
    assertThat(myexp.getClassName()).as("className inherited from root").isEqualTo("X");
    // the overriding key takes the tenant value
    assertThat(myexp.getArgs())
        .as("overridden args.b takes the tenant value")
        .containsEntry("b", 99);
    // the non-overriding sibling key is preserved from root
    assertThat(myexp.getArgs())
        .as("sibling args.a is preserved from root (deep-merge, not replace)")
        .containsEntry("a", 1);
  }
}
