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
 * Pinned characterization test for the collection merge policy of per-tenant configuration.
 *
 * <p>The resolver binds {@code camunda.*} then {@code camunda.physical-tenants.<id>.*} into the
 * same {@link Camunda} instance. For a {@code Map}-typed property such as an exporter's {@code
 * args} ({@code Map<String, Object>}), it is not obvious whether Spring's Binder deep-merges the
 * tenant override onto the root entry (preserving sibling keys) or replaces the whole value.
 *
 * <p>This test pins the observed behavior so the merge policy can be decided from evidence rather
 * than intuition. The seed: root {@code myexp} with {@code className=X}, {@code args.a=1}, {@code
 * args.b=2}; tenant {@code tenanta} overrides only {@code args.b=99}.
 *
 * <p><b>Observed outcome: REPLACE, not deep-merge.</b> When the tenant sets any key under {@code
 * data.exporters.myexp.args}, Spring's Binder constructs a <em>fresh</em> {@link Exporter} for the
 * {@code myexp} map key instead of binding into the root's existing instance. The result is an
 * exporter with {@code args == {b=99}} only: the sibling {@code args.a=1} and the root {@code
 * className=X} are both discarded. (This differs from a nested non-map bean such as {@code
 * cluster}, whose sibling fields survive — see {@code PhysicalTenantBinderTest} — because a {@code
 * Map} <em>value</em> is not seeded from the existing entry during the second bind.)
 *
 * <p><b>Consequence for the merge policy:</b> native Spring overlay does NOT satisfy the issue's
 * deep-merge requirement for {@code Map<String, Object>} exporter args. A custom overlay is
 * therefore required — but that is a separate deliverable to be agreed with the user before
 * building; this test only locks in the native behavior so a future merge implementation has a
 * baseline to change.
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
  void shouldCharacterizeExporterArgsOverlayBetweenRootAndTenant() {
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
    // the tenant must provide its own initialization block (required per-PT override)
    properties.put(
        "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
        "tenanta-admin");
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));

    final Camunda camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));

    // when the tenant configuration is resolved
    final Camunda tenantA =
        PhysicalTenantResolver.of(environment, camunda).forPhysicalTenant("tenanta");

    // then the tenant's exporter entry has been REPLACED, not merged onto the root entry
    final Exporter myexp = tenantA.getData().getExporters().get("myexp");
    assertThat(myexp).as("the exporter entry itself survives the overlay").isNotNull();
    // the overriding key is present with the tenant value
    assertThat(myexp.getArgs())
        .as("overridden args.b takes the tenant value")
        .containsEntry("b", 99);
    // and everything not restated by the tenant is lost: a fresh Exporter was constructed
    assertThat(myexp.getArgs())
        .as("args is replaced wholesale — sibling args.a from the root is discarded")
        .containsOnlyKeys("b");
    assertThat(myexp.getClassName())
        .as("className from the root is discarded by the replace overlay")
        .isNull();
  }
}
