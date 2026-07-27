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
 * Pinned characterization test for the resolved per-tenant exporter entry of a class
 * <em>without</em> an {@code ExporterConfigMerger} — the whole-map-replace row of the ADR-0008 §2
 * decision table.
 *
 * <p>Spring's native Binder constructs a <em>fresh</em> {@link Exporter} when the tenant touches
 * any key under {@code data.exporters.<id>.*}: sibling args and the root's {@code className} are
 * discarded (the historic behavior this test originally pinned). Since {@link
 * PhysicalTenantExporterConfigurations} recomputes the entry, the {@code className} is inherited
 * from the root entry again — but the <b>args replacement stands</b>: for an exporter class without
 * a discoverable merger (custom exporters), partial args inheritance is not offered, and the
 * tenant's args are taken exactly as declared. The seed: root {@code myexp} with {@code
 * className=X}, {@code args.a=1}, {@code args.b=2}; tenant {@code tenanta} overrides only {@code
 * args.b=99}.
 *
 * <p>The merge path (a class <em>with</em> a merger) and the remaining decision-table rows are
 * covered by {@link PhysicalTenantExporterConfigurationsTest}.
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

    // then the tenant's args have been REPLACED (class X has no merger), not merged
    final Exporter myexp = tenantA.getData().getExporters().get("myexp");
    assertThat(myexp).as("the exporter entry itself survives the overlay").isNotNull();
    // the overriding key is present with the tenant value
    assertThat(myexp.getArgs())
        .as("overridden args.b takes the tenant value")
        .containsEntry("b", 99);
    // and args the tenant did not restate are lost: no partial args inheritance without a merger
    assertThat(myexp.getArgs())
        .as("args is replaced wholesale — sibling args.a from the root is discarded")
        .containsOnlyKeys("b");
    assertThat(myexp.getClassName())
        .as(
            "className is inherited from the root entry by the exporter resolution step "
                + "(diverging from it is a boot error)")
        .isEqualTo("X");
  }
}
