/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Exporter;
import io.camunda.configuration.UnifiedConfigurationException;
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
 * Covers the step-1 rows of the ADR-0008 §2 decision table against the test-only mergers registered
 * via {@code src/test/resources/META-INF/services} (real-merger end-to-end coverage lives in {@code
 * PhysicalTenantExporterConfigIT}). The step-2 rows — the {@code exporters-assigned} manifest,
 * narrowing, and the configured-but-unassigned error — are gated on #56652 and deliberately absent
 * here; see {@link PhysicalTenantExporterConfigurations}.
 */
class PhysicalTenantExporterConfigurationsTest {

  private static final String TENANT = "tenanta";

  private MockEnvironment environment;
  private Map<String, Object> properties;

  @BeforeEach
  void setUp() {
    environment = new MockEnvironment();
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
    properties = new HashMap<>();
    // distinct storage location so the tenant does not collide with the synthesized 'default',
    // and the mandatory per-PT security initialization
    properties.put(
        "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
        TENANT);
    properties.put(
        "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
        "tenanta-admin");
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  private Camunda resolveTenantA() {
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    final Camunda camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return PhysicalTenantResolver.of(environment, camunda).forPhysicalTenant(TENANT);
  }

  @Test
  void shouldInheritRootEntryUnchangedWhenTenantDoesNotTouchIt() {
    // given — a root catalog entry the tenant never mentions
    properties.put("camunda.data.exporters.untouched.class-name", "com.acme.CustomExporter");
    properties.put("camunda.data.exporters.untouched.args.a", 1);

    // when
    final Exporter resolved = resolveTenantA().getData().getExporters().get("untouched");

    // then
    assertThat(resolved).isNotNull();
    assertThat(resolved.getClassName()).isEqualTo("com.acme.CustomExporter");
    assertThat(resolved.getArgs()).containsEntry("a", 1);
  }

  @Test
  void shouldDeepMergeArgsWhenExporterClassHasMerger() {
    // given — a catalog entry whose class has a merger, partially overridden by the tenant
    properties.put(
        "camunda.data.exporters.mergeable.class-name", TestExporterConfigMergers.MERGEABLE_CLASS);
    properties.put("camunda.data.exporters.mergeable.args.a", 1);
    properties.put("camunda.data.exporters.mergeable.args.b", 2);
    properties.put("camunda.physical-tenants.tenanta.data.exporters.mergeable.args.b", 99);

    // when
    final Exporter resolved = resolveTenantA().getData().getExporters().get("mergeable");

    // then — root className inherited, args merged by the discovered merger (tenant wins)
    assertThat(resolved.getClassName()).isEqualTo(TestExporterConfigMergers.MERGEABLE_CLASS);
    assertThat(resolved.getArgs())
        .containsEntry("a", 1)
        .containsEntry("b", 99)
        .containsEntry(
            TestExporterConfigMergers.MERGED_BY_KEY, TestExporterConfigMergers.MERGED_BY_VALUE);
  }

  @Test
  void shouldTakeTenantArgsAsDeclaredWhenExporterClassHasNoMerger() {
    // given — a catalog entry of a class without a merger (e.g. a custom exporter)
    properties.put("camunda.data.exporters.custom.class-name", "com.acme.CustomExporter");
    properties.put("camunda.data.exporters.custom.args.a", 1);
    properties.put("camunda.data.exporters.custom.args.b", 2);
    properties.put("camunda.physical-tenants.tenanta.data.exporters.custom.args.b", 99);

    // when
    final Exporter resolved = resolveTenantA().getData().getExporters().get("custom");

    // then — className/jarPath inherited from root, args exactly as the tenant declared
    // (whole-map replace: root args are not merged in)
    assertThat(resolved.getClassName()).isEqualTo("com.acme.CustomExporter");
    assertThat(resolved.getArgs()).containsOnlyKeys("b").containsEntry("b", 99);
  }

  @Test
  void shouldAllowRestatingTheRootClassName() {
    // given — the tenant restates the root's exact className alongside its partial args
    properties.put("camunda.data.exporters.custom.class-name", "com.acme.CustomExporter");
    properties.put("camunda.data.exporters.custom.args.a", 1);
    properties.put(
        "camunda.physical-tenants.tenanta.data.exporters.custom.class-name",
        "com.acme.CustomExporter");
    properties.put("camunda.physical-tenants.tenanta.data.exporters.custom.args.b", 5);

    // when
    final Exporter resolved = resolveTenantA().getData().getExporters().get("custom");

    // then
    assertThat(resolved.getClassName()).isEqualTo("com.acme.CustomExporter");
    assertThat(resolved.getArgs()).containsOnlyKeys("b");
  }

  @Test
  void shouldRejectClassNameDivergingFromRoot() {
    // given — the tenant changes the class of a root-declared exporter id
    properties.put("camunda.data.exporters.custom.class-name", "com.acme.CustomExporter");
    properties.put(
        "camunda.physical-tenants.tenanta.data.exporters.custom.class-name",
        "com.acme.OtherExporter");

    // when / then
    assertThatThrownBy(this::resolveTenantA)
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("tenanta")
        .hasMessageContaining("custom")
        .hasMessageContaining("com.acme.OtherExporter")
        .hasMessageContaining("tenant-private");
  }

  @Test
  void shouldRejectJarPathDivergingFromRoot() {
    // given
    properties.put("camunda.data.exporters.custom.class-name", "com.acme.CustomExporter");
    properties.put("camunda.data.exporters.custom.jar-path", "/opt/root.jar");
    properties.put(
        "camunda.physical-tenants.tenanta.data.exporters.custom.jar-path", "/opt/tenant.jar");

    // when / then
    assertThatThrownBy(this::resolveTenantA)
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("jar-path")
        .hasMessageContaining("/opt/tenant.jar");
  }

  @Test
  void shouldKeepTenantPrivateExporterExactlyAsDeclared() {
    // given — an id root does not declare
    properties.put(
        "camunda.physical-tenants.tenanta.data.exporters.private.class-name",
        "com.acme.TenantOnlyExporter");
    properties.put("camunda.physical-tenants.tenanta.data.exporters.private.args.x", "y");

    // when
    final Exporter resolved = resolveTenantA().getData().getExporters().get("private");

    // then
    assertThat(resolved.getClassName()).isEqualTo("com.acme.TenantOnlyExporter");
    assertThat(resolved.getArgs()).containsEntry("x", "y");
  }

  @Test
  void shouldTakeAutoconfiguredExporterTuningAsDeclared() {
    // given — root args-tuning for the autoconfigured camundaexporter, partially overridden by
    // the tenant; the id is outside the catalog, so no merge and no divergence rules apply
    properties.put("camunda.data.exporters.camundaexporter.args.a", 1);
    properties.put("camunda.data.exporters.camundaexporter.args.b", 2);
    properties.put("camunda.physical-tenants.tenanta.data.exporters.camundaexporter.args.b", 99);

    // when
    final Exporter resolved = resolveTenantA().getData().getExporters().get("camundaexporter");

    // then — the tenant's declaration is taken as-is (native binder semantics, no merge step)
    assertThat(resolved.getArgs()).containsOnlyKeys("b").containsEntry("b", 99);
  }

  @Test
  void shouldNotInheritRootDeclaredAutoconfiguredExporter() {
    // given — a root-declared explicit camundaexporter pinning the root connection, and a tenant
    // that does not declare the id at all
    properties.put(
        "camunda.data.exporters.camundaexporter.class-name", "io.camunda.exporter.CamundaExporter");
    properties.put("camunda.data.exporters.camundaexporter.args.connect.url", "http://root:9200");
    properties.put("camunda.data.exporters.camundaexporter.args.index.prefix", "root");

    // when
    final Exporter resolved = resolveTenantA().getData().getExporters().get("camundaexporter");

    // then — the root entry is not inherited: the tenant's camundaexporter is derived downstream
    // from the tenant's own secondary-storage properties, and the inherited root args would
    // override that derivation and route the tenant's exports into root's storage
    assertThat(resolved).isNull();
  }

  @Test
  void shouldWrapMergeFailureWithExporterAndTenantId() {
    // given — a catalog entry whose merger throws
    properties.put(
        "camunda.data.exporters.failing.class-name", TestExporterConfigMergers.FAILING_CLASS);
    properties.put("camunda.data.exporters.failing.args.a", 1);
    properties.put("camunda.physical-tenants.tenanta.data.exporters.failing.args.a", 2);

    // when / then
    assertThatThrownBy(this::resolveTenantA)
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("failing")
        .hasMessageContaining("tenanta")
        .hasMessageContaining("intentional test merge failure");
  }

  @Test
  void shouldRejectMultipleMergersClaimingTheSameClass() {
    // given — a catalog entry whose class two discovered mergers claim
    properties.put(
        "camunda.data.exporters.dup.class-name", TestExporterConfigMergers.DUPLICATE_CLAIMED_CLASS);
    properties.put("camunda.physical-tenants.tenanta.data.exporters.dup.args.a", 1);

    // when / then
    assertThatThrownBy(this::resolveTenantA)
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("Multiple ExporterConfigMerger implementations")
        .hasMessageContaining(TestExporterConfigMergers.DUPLICATE_CLAIMED_CLASS);
  }

  @Test
  void shouldNotRunTheExporterStepForTenantsWithoutExporterDeclarations() {
    // given — a root catalog only; the tenant declares nothing under data.exporters
    properties.put(
        "camunda.data.exporters.mergeable.class-name", TestExporterConfigMergers.MERGEABLE_CLASS);
    properties.put("camunda.data.exporters.mergeable.args.a", 1);

    // when
    final Exporter resolved = resolveTenantA().getData().getExporters().get("mergeable");

    // then — inherited via the two-bind, no merger marker present
    assertThat(resolved.getArgs())
        .containsEntry("a", 1)
        .doesNotContainKey(TestExporterConfigMergers.MERGED_BY_KEY);
  }

  /**
   * Pins the Binder behavior ADR-0008 step 2 depends on (its consequences section flags it as
   * fragile): an explicit {@code exporters-assigned: []}/{@code ""} must be distinguishable from an
   * absent key, since for exporters an empty manifest is valid ("no generic exporters") while an
   * absent one must be a boot error. Verified here ahead of step 2 so the fallback documented in
   * the ADR is not needed.
   */
  @Test
  void shouldDistinguishBoundEmptyExportersAssignedFromAbsentKey() {
    // given — an explicit empty exporters-assigned (yaml `[]` surfaces as an empty string value)
    properties.put("camunda.physical-tenants.tenanta.data.exporters-assigned", "");
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    final Binder binder = Binder.get(environment);

    // when
    final var boundEmpty =
        binder.bind(
            "camunda.physical-tenants.tenanta.data.exporters-assigned",
            Bindable.listOf(String.class));
    final var absent =
        binder.bind(
            "camunda.physical-tenants.tenantb.data.exporters-assigned",
            Bindable.listOf(String.class));

    // then
    assertThat(boundEmpty.isBound()).isTrue();
    assertThat(boundEmpty.get()).isEmpty();
    assertThat(absent.isBound()).isFalse();
  }
}
