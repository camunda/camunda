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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class ExporterArgsOverlayTest {

  // ---- helpers ----

  private static Exporter exporter(
      final String className, final String jarPath, final Map<String, Object> args) {
    final Exporter e = new Exporter();
    e.setClassName(className);
    e.setJarPath(jarPath);
    e.setArgs(new LinkedHashMap<>(args));
    return e;
  }

  @Nested
  class DeepMerge {

    @Test
    void shouldReturnOverrideValueForScalar() {
      // given
      final Map<String, Object> base = Map.of("key", "base");
      final Map<String, Object> override = Map.of("key", "override");
      // when
      final Map<String, Object> result = ExporterArgsOverlay.deepMerge(base, override);
      // then
      assertThat(result).containsEntry("key", "override");
    }

    @Test
    void shouldRecurseIntoNestedMaps() {
      // given
      final Map<String, Object> baseNested = new LinkedHashMap<>();
      baseNested.put("x", 1);
      baseNested.put("y", 2);
      final Map<String, Object> base = Map.of("nested", baseNested);

      final Map<String, Object> overrideNested = Map.of("y", 99);
      final Map<String, Object> override = Map.of("nested", overrideNested);
      // when
      final Map<String, Object> result = ExporterArgsOverlay.deepMerge(base, override);
      // then
      @SuppressWarnings("unchecked")
      final Map<String, Object> resultNested = (Map<String, Object>) result.get("nested");
      assertThat(resultNested).containsEntry("x", 1).containsEntry("y", 99);
    }

    @Test
    void shouldRecurseAtArbitraryDepth() {
      // given — three levels deep
      final Map<String, Object> base = Map.of("a", Map.of("b", Map.of("c", "base")));
      final Map<String, Object> override = Map.of("a", Map.of("b", Map.of("c", "override")));
      // when
      final Map<String, Object> result = ExporterArgsOverlay.deepMerge(base, override);
      // then
      @SuppressWarnings("unchecked")
      final Map<String, Object> a = (Map<String, Object>) result.get("a");
      @SuppressWarnings("unchecked")
      final Map<String, Object> b = (Map<String, Object>) a.get("b");
      assertThat(b).containsEntry("c", "override");
    }

    @Test
    void shouldReplaceListsWithoutAppending() {
      // given
      final Map<String, Object> base = Map.of("list", List.of("a", "b"));
      final Map<String, Object> override = Map.of("list", List.of("c"));
      // when
      final Map<String, Object> result = ExporterArgsOverlay.deepMerge(base, override);
      // then
      assertThat(result).containsEntry("list", List.of("c"));
    }

    @Test
    void shouldPreserveDisjointBaseKeys() {
      // given
      final Map<String, Object> base = Map.of("baseOnly", "value", "shared", "base");
      final Map<String, Object> override = Map.of("overrideOnly", "new", "shared", "override");
      // when
      final Map<String, Object> result = ExporterArgsOverlay.deepMerge(base, override);
      // then
      assertThat(result)
          .containsEntry("baseOnly", "value")
          .containsEntry("overrideOnly", "new")
          .containsEntry("shared", "override");
    }

    @Test
    void shouldNotRemoveBaseKeys() {
      // given — override has no "toKeep" key
      final Map<String, Object> base = Map.of("toKeep", "value", "toOverride", "old");
      final Map<String, Object> override = Map.of("toOverride", "new");
      // when
      final Map<String, Object> result = ExporterArgsOverlay.deepMerge(base, override);
      // then
      assertThat(result).containsEntry("toKeep", "value").containsEntry("toOverride", "new");
    }

    @Test
    void shouldReturnUnchangedBaseWhenOverrideIsEmpty() {
      // given
      final Map<String, Object> base = Map.of("k", "v");
      // when
      final Map<String, Object> result = ExporterArgsOverlay.deepMerge(base, Map.of());
      // then
      assertThat(result).isEqualTo(base);
    }
  }

  @Nested
  class NormalizeConfigKeys {

    @Test
    void shouldLowercaseKeys() {
      // given
      final Map<String, Object> map = Map.of("MyKey", "value");
      // when / then
      assertThat(ExporterArgsOverlay.normalizeConfigKeys(map)).containsKey("mykey");
    }

    @Test
    void shouldStripDashes() {
      // given
      final Map<String, Object> map = Map.of("index-prefix", "value");
      // when / then
      assertThat(ExporterArgsOverlay.normalizeConfigKeys(map)).containsKey("indexprefix");
    }

    @Test
    void shouldCollapseRelaxedFormVariants() {
      // given — indexPrefix and index-prefix both normalize to indexprefix; last write wins
      final Map<String, Object> base = Map.of("indexPrefix", "camel");
      final Map<String, Object> override = Map.of("index-prefix", "dashes");
      // when
      final Map<String, Object> normalizedBase = ExporterArgsOverlay.normalizeConfigKeys(base);
      final Map<String, Object> normalizedOverride =
          ExporterArgsOverlay.normalizeConfigKeys(override);
      final Map<String, Object> result =
          ExporterArgsOverlay.deepMerge(normalizedBase, normalizedOverride);
      // then — both forms collapse to the same normalized key; override wins
      assertThat(result).containsOnlyKeys("indexprefix").containsEntry("indexprefix", "dashes");
    }

    @Test
    void shouldRecurseIntoNestedMaps() {
      // given
      final Map<String, Object> nested = Map.of("Index-Prefix", "v");
      final Map<String, Object> map = Map.of("Outer-Key", nested);
      // when
      final Map<String, Object> result = ExporterArgsOverlay.normalizeConfigKeys(map);
      // then
      assertThat(result).containsKey("outerkey");
      @SuppressWarnings("unchecked")
      final Map<String, Object> resultNested = (Map<String, Object>) result.get("outerkey");
      assertThat(resultNested).containsKey("indexprefix");
    }
  }

  @Nested
  class Overlay {

    @Test
    void shouldInheritRootFieldsWhenTenantSetsNothing() {
      // given
      final Exporter root = exporter("com.Root", "/path/root.jar", Map.of("a", 1));
      // when
      final Map<String, Exporter> result =
          ExporterArgsOverlay.overlay("t1", Map.of("exp", root), Map.of());
      // then
      final Exporter merged = result.get("exp");
      assertThat(merged.getClassName()).isEqualTo("com.Root");
      assertThat(merged.getJarPath()).isEqualTo("/path/root.jar");
      assertThat(merged.getArgs()).containsEntry("a", 1);
    }

    @Test
    void shouldOverrideIndividualArgsAndPreserveSiblings() {
      // given
      final Exporter root = exporter("com.Root", null, Map.of("a", 1, "b", 2));
      final Exporter tenant = exporter(null, null, Map.of("b", 99));
      // when
      final Map<String, Exporter> result =
          ExporterArgsOverlay.overlay("t1", Map.of("exp", root), Map.of("exp", tenant));
      // then
      final Exporter merged = result.get("exp");
      assertThat(merged.getClassName()).isEqualTo("com.Root");
      assertThat(merged.getArgs()).containsEntry("a", 1).containsEntry("b", 99);
    }

    @Test
    void shouldAllowDedicatedTenantOnlyExporter() {
      // given — tenant declares an exporter not present in root at all
      final Exporter tenant = exporter("com.Custom", null, Map.of("x", 7));
      // when
      final Map<String, Exporter> result =
          ExporterArgsOverlay.overlay("t1", Map.of(), Map.of("custom", tenant));
      // then
      final Exporter merged = result.get("custom");
      assertThat(merged.getClassName()).isEqualTo("com.Custom");
      assertThat(merged.getArgs()).containsEntry("x", 7);
    }

    @Test
    void shouldApplyTenantClassNameWhenRootAbsent() {
      // given — root has no className (autoconfigured); tenant sets one for a dedicated exporter
      final Exporter root = exporter(null, null, Map.of("a", 1));
      final Exporter tenant = exporter("com.Tenant", null, Map.of());
      // when
      final Map<String, Exporter> result =
          ExporterArgsOverlay.overlay("t1", Map.of("exp", root), Map.of("exp", tenant));
      // then — allowed: root left className unset
      assertThat(result.get("exp").getClassName()).isEqualTo("com.Tenant");
    }

    @Test
    void shouldPreserveAllRootExporters() {
      // given — tenant only overrides one of two root exporters
      final Exporter rootA = exporter("com.A", null, Map.of());
      final Exporter rootB = exporter("com.B", null, Map.of());
      final Exporter tenantA = exporter(null, null, Map.of("k", "v"));
      // when
      final Map<String, Exporter> result =
          ExporterArgsOverlay.overlay("t1", Map.of("a", rootA, "b", rootB), Map.of("a", tenantA));
      // then — both ids survive
      assertThat(result).containsKeys("a", "b");
      assertThat(result.get("b").getClassName()).isEqualTo("com.B");
    }

    @Test
    void shouldDeepMergeRealCamundaExporterArgs() {
      // given — root configures bulk settings and a three-level history.retention block,
      // using property names from ExporterConfiguration (bulk.memory-limit, history.retention.*)
      final Map<String, Object> rootBulk = new LinkedHashMap<>();
      rootBulk.put("size", 5000);
      rootBulk.put("delay", 1);
      rootBulk.put("memory-limit", 20); // dash-form: normalized to memorylimit

      final Map<String, Object> rootRetention = new LinkedHashMap<>();
      rootRetention.put("minimum-age", "30d"); // normalized to minimumage
      // TODO a tenant should use a different retention policy if it shares the same ES/OS instance,
      // we need to add a validation on that when implementing ES/OS epic
      rootRetention.put("policy-name", "camunda-retention"); // normalized to policyname

      final Map<String, Object> rootHistory = new LinkedHashMap<>();
      rootHistory.put("rollover-interval", "1d"); // normalized to rolloverinterval
      rootHistory.put("retention", rootRetention);

      final Map<String, Object> rootArgs = new LinkedHashMap<>();
      rootArgs.put("bulk", rootBulk);
      rootArgs.put("history", rootHistory);

      // tenant overrides only bulk.size and history.retention.minimum-age
      final Map<String, Object> tenantArgs =
          Map.of(
              "bulk", Map.of("size", 1000),
              "history", Map.of("retention", Map.of("minimum-age", "90d")));

      final Exporter root = exporter(null, null, rootArgs);
      final Exporter tenant = exporter(null, null, tenantArgs);

      // when
      final Map<String, Exporter> result =
          ExporterArgsOverlay.overlay(
              "t1", Map.of("camundaexporter", root), Map.of("camundaexporter", tenant));
      final Map<String, Object> args = result.get("camundaexporter").getArgs();

      // then bulk: size overridden; delay and memory-limit (→ memorylimit after normalization) kept
      @SuppressWarnings("unchecked")
      final Map<String, Object> bulk = (Map<String, Object>) args.get("bulk");
      assertThat(bulk)
          .containsEntry("size", 1000)
          .containsEntry("delay", 1)
          .containsEntry("memorylimit", 20);

      // then history: rollover-interval (→ rolloverinterval) kept; retention deep-merged
      @SuppressWarnings("unchecked")
      final Map<String, Object> history = (Map<String, Object>) args.get("history");
      assertThat(history).containsEntry("rolloverinterval", "1d");

      @SuppressWarnings("unchecked")
      final Map<String, Object> retention = (Map<String, Object>) history.get("retention");
      assertThat(retention)
          .containsEntry("minimumage", "90d") // overridden
          .containsEntry("policyname", "camunda-retention"); // inherited
    }
  }

  @Nested
  class DivergenceCheck {

    @Test
    void shouldRejectWhenTenantReclassesExplicitRootClassName() {
      // given — root explicitly declares className; tenant sets a different one
      final Exporter root = exporter("com.Root", null, Map.of());
      final Exporter tenant = exporter("com.Different", null, Map.of());
      // when / then
      assertThatThrownBy(
              () ->
                  ExporterArgsOverlay.overlay(
                      "myTenant", Map.of("exp", root), Map.of("exp", tenant)))
          .isInstanceOf(UnifiedConfigurationException.class)
          .hasMessageContaining("myTenant")
          .hasMessageContaining("exp")
          .hasMessageContaining("className");
    }

    @Test
    void shouldRejectWhenTenantReclassesExplicitRootJarPath() {
      // given
      final Exporter root = exporter(null, "/root.jar", Map.of());
      final Exporter tenant = exporter(null, "/tenant.jar", Map.of());
      // when / then
      assertThatThrownBy(
              () ->
                  ExporterArgsOverlay.overlay(
                      "myTenant", Map.of("exp", root), Map.of("exp", tenant)))
          .isInstanceOf(UnifiedConfigurationException.class)
          .hasMessageContaining("jarPath");
    }

    @Test
    void shouldAllowTenantSettingClassNameWhenRootAbsent() {
      // given — root has no className (autoconfigured pattern)
      final Exporter root = exporter(null, null, Map.of("a", 1));
      final Exporter tenant = exporter("com.Tenant", null, Map.of());
      // when / then — must not throw
      assertThat(ExporterArgsOverlay.overlay("t1", Map.of("exp", root), Map.of("exp", tenant)))
          .containsKey("exp");
    }

    @Test
    void shouldAllowRootSettingClassNameWhenTenantAbsent() {
      // given — tenant sets no className
      final Exporter root = exporter("com.Root", null, Map.of());
      final Exporter tenant = exporter(null, null, Map.of("b", 2));
      // when / then — must not throw
      assertThat(ExporterArgsOverlay.overlay("t1", Map.of("exp", root), Map.of("exp", tenant)))
          .containsKey("exp");
    }

    @Test
    void shouldAllowIdenticalClassNames() {
      // given — both sides agree on the same className
      final Exporter root = exporter("com.Same", null, Map.of());
      final Exporter tenant = exporter("com.Same", null, Map.of());
      // when / then — no divergence
      assertThat(ExporterArgsOverlay.overlay("t1", Map.of("exp", root), Map.of("exp", tenant)))
          .containsKey("exp");
    }
  }

  @Nested
  class ViaResolver {

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
    void shouldDeepMergeExporterArgsThroughFullResolver() {
      // given a root exporter with a className and two args, and a tenant overriding only args.b
      final Map<String, Object> properties = new HashMap<>();
      properties.put("camunda.data.exporters.myexp.class-name", "X");
      properties.put("camunda.data.exporters.myexp.args.a", 1);
      properties.put("camunda.data.exporters.myexp.args.b", 2);
      properties.put("camunda.physical-tenants.tenanta.data.exporters.myexp.args.b", 99);
      properties.put(
          "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
          "tenanta");
      environment.getPropertySources().addFirst(new MapPropertySource("test", properties));

      final Camunda camunda = new Camunda();
      Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));

      // when
      final Camunda tenantA =
          PhysicalTenantResolver.of(environment, camunda).forPhysicalTenant("tenanta");

      // then className is inherited and args are deep-merged
      final Exporter myexp = tenantA.getData().getExporters().get("myexp");
      assertThat(myexp).isNotNull();
      assertThat(myexp.getClassName()).as("className inherited from root").isEqualTo("X");
      assertThat(myexp.getArgs()).as("tenant value wins for overridden key").containsEntry("b", 99);
      assertThat(myexp.getArgs())
          .as("sibling key preserved from root (deep-merge, not replace)")
          .containsEntry("a", 1);
    }

    @Test
    void shouldInheritAllRootExportersWhenTenantDeclaresNone() {
      // given root has two exporters; the tenant overrides nothing under data.exporters
      final Map<String, Object> properties = new HashMap<>();
      properties.put("camunda.data.exporters.exp1.class-name", "com.Exp1");
      properties.put("camunda.data.exporters.exp1.args.k", "v");
      properties.put("camunda.data.exporters.exp2.class-name", "com.Exp2");
      properties.put(
          "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
          "tenanta");
      environment.getPropertySources().addFirst(new MapPropertySource("test", properties));

      final Camunda camunda = new Camunda();
      Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));

      // when
      final Camunda tenantA =
          PhysicalTenantResolver.of(environment, camunda).forPhysicalTenant("tenanta");

      // then both root exporters are present on the tenant with fields intact
      assertThat(tenantA.getData().getExporters()).containsKeys("exp1", "exp2");
      assertThat(tenantA.getData().getExporters().get("exp1").getClassName()).isEqualTo("com.Exp1");
      assertThat(tenantA.getData().getExporters().get("exp1").getArgs()).containsEntry("k", "v");
      assertThat(tenantA.getData().getExporters().get("exp2").getClassName()).isEqualTo("com.Exp2");
    }

    @Test
    void shouldAllowTenantDedicatedExporterAlongsideRootExporters() {
      // given root has one exporter; the tenant also declares a second, dedicated one
      final Map<String, Object> properties = new HashMap<>();
      properties.put("camunda.data.exporters.rootexp.class-name", "com.Root");
      properties.put(
          "camunda.physical-tenants.tenanta.data.exporters.tenantexp.class-name", "com.Tenant");
      properties.put(
          "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
          "tenanta");
      environment.getPropertySources().addFirst(new MapPropertySource("test", properties));

      final Camunda camunda = new Camunda();
      Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));

      // when
      final Camunda tenantA =
          PhysicalTenantResolver.of(environment, camunda).forPhysicalTenant("tenanta");

      // then both exporters are present in the tenant's resolved config
      assertThat(tenantA.getData().getExporters()).containsKeys("rootexp", "tenantexp");
      assertThat(tenantA.getData().getExporters().get("rootexp").getClassName())
          .isEqualTo("com.Root");
      assertThat(tenantA.getData().getExporters().get("tenantexp").getClassName())
          .isEqualTo("com.Tenant");
    }

    @Test
    void shouldRejectTenantThatReclassesRootExporter() {
      // given root declares an explicit className; tenant sets a different value for the same id
      final Map<String, Object> properties = new HashMap<>();
      properties.put("camunda.data.exporters.myexp.class-name", "com.Root");
      properties.put(
          "camunda.physical-tenants.tenanta.data.exporters.myexp.class-name", "com.Different");
      properties.put(
          "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
          "tenanta");
      environment.getPropertySources().addFirst(new MapPropertySource("test", properties));

      final Camunda camunda = new Camunda();
      Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));

      // when / then — fail fast naming tenant, exporter id, and field
      assertThatThrownBy(() -> PhysicalTenantResolver.of(environment, camunda))
          .isInstanceOf(UnifiedConfigurationException.class)
          .hasMessageContaining("tenanta")
          .hasMessageContaining("myexp")
          .hasMessageContaining("className");
    }
  }
}
