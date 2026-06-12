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

import io.camunda.configuration.Exporter;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExporterArgsOverlayTest {

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
  class CanonicalizeConfigKeys {

    @Test
    void shouldLowercaseKeys() {
      // given
      final Map<String, Object> map = Map.of("MyKey", "value");
      // when / then
      assertThat(ExporterArgsOverlay.canonicalizeConfigKeys(map)).containsKey("mykey");
    }

    @Test
    void shouldStripDashes() {
      // given
      final Map<String, Object> map = Map.of("index-prefix", "value");
      // when / then
      assertThat(ExporterArgsOverlay.canonicalizeConfigKeys(map)).containsKey("indexprefix");
    }

    @Test
    void shouldCollapseRelaxedFormVariants() {
      // given — indexPrefix and index-prefix both canonicalize to indexprefix; last write wins
      final Map<String, Object> base = Map.of("indexPrefix", "camel");
      final Map<String, Object> override = Map.of("index-prefix", "dashes");
      // when
      final Map<String, Object> mergedBase = ExporterArgsOverlay.canonicalizeConfigKeys(base);
      final Map<String, Object> mergedOverride =
          ExporterArgsOverlay.canonicalizeConfigKeys(override);
      final Map<String, Object> result = ExporterArgsOverlay.deepMerge(mergedBase, mergedOverride);
      // then — both forms collapse to the same canonical key; override wins
      assertThat(result).containsOnlyKeys("indexprefix").containsEntry("indexprefix", "dashes");
    }

    @Test
    void shouldRecurseIntoNestedMaps() {
      // given
      final Map<String, Object> nested = Map.of("Index-Prefix", "v");
      final Map<String, Object> map = Map.of("Outer-Key", nested);
      // when
      final Map<String, Object> result = ExporterArgsOverlay.canonicalizeConfigKeys(map);
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

  // ---- helpers ----

  private static Exporter exporter(
      final String className, final String jarPath, final Map<String, Object> args) {
    final Exporter e = new Exporter();
    e.setClassName(className);
    e.setJarPath(jarPath);
    e.setArgs(new LinkedHashMap<>(args));
    return e;
  }
}
