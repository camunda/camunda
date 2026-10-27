/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Direct coverage of the shared merger helper. Its two callers ({@code
 * BrokerBasedPropertiesOverride} and {@code PhysicalTenantExporterConfigurations}) exercise it end
 * to end through Spring binding, but each only reaches the shapes its own config path produces —
 * neither, for instance, ever declares a list-valued exporter arg. Since the whole point of sharing
 * this logic is that it is subtle enough to be worth having exactly one copy of, the copy gets its
 * own tests: the defensive-copy contract in particular is only load-bearing for nesting the callers
 * do not currently exercise.
 */
class ExporterArgsMergersTest {

  private static final String CLASS_A = "com.acme.ExporterA";
  private static final String CLASS_B = "com.acme.ExporterB";

  @Test
  void shouldReturnNullWhenClassNameIsNull() {
    // given — an entry with no class name at all (a legal state for a partially-bound entry)
    final List<ExporterConfigMerger> mergers = List.of(new ClaimingMerger(CLASS_A));

    // when / then — no class to match on means no merger, not a lookup failure
    assertThat(ExporterArgsMergers.find(mergers, null, "exporter 'foo'")).isNull();
  }

  @Test
  void shouldReturnNullWhenNoMergerClaimsTheClass() {
    // given
    final List<ExporterConfigMerger> mergers = List.of(new ClaimingMerger(CLASS_A));

    // when / then — caller falls back to whole-map replace
    assertThat(ExporterArgsMergers.find(mergers, CLASS_B, "exporter 'foo'")).isNull();
  }

  @Test
  void shouldReturnTheSingleClaimant() {
    // given
    final ExporterConfigMerger claimant = new ClaimingMerger(CLASS_A);
    final List<ExporterConfigMerger> mergers = List.of(claimant, new ClaimingMerger(CLASS_B));

    // when / then
    assertThat(ExporterArgsMergers.find(mergers, CLASS_A, "exporter 'foo'")).isSameAs(claimant);
  }

  @Test
  void shouldRejectMultipleMergersClaimingTheSameClass() {
    // given — two mergers owning one exporter class; which one wins would be arbitrary
    final List<ExporterConfigMerger> mergers =
        List.of(new ClaimingMerger(CLASS_A), new ClaimingMerger(CLASS_A));

    // when / then — the caller's context names the offending entry so the operator can find it
    assertThatThrownBy(() -> ExporterArgsMergers.find(mergers, CLASS_A, "exporter 'foo'"))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("Multiple ExporterConfigMerger implementations")
        .hasMessageContaining(CLASS_A)
        .hasMessageContaining("exporter 'foo'");
  }

  @Test
  void shouldMergeWithOverlayWinningPerKey() {
    // given
    final Map<String, Object> base = new LinkedHashMap<>(Map.of("a", 1, "b", 2));
    final Map<String, Object> overlay = new LinkedHashMap<>(Map.of("b", 99));

    // when
    final Map<String, Object> merged =
        ExporterArgsMergers.merge(new TopLevelMerger(), base, overlay, "exporter 'foo'");

    // then
    assertThat(merged).containsEntry("a", 1).containsEntry("b", 99);
  }

  @Test
  void shouldTreatNullArgsAsEmpty() {
    // given — an exporter entry may legally carry no args at all
    final AtomicReference<Map<String, Object>> seenBase = new AtomicReference<>();
    final AtomicReference<Map<String, Object>> seenOverlay = new AtomicReference<>();
    final ExporterConfigMerger capturing =
        recording(
            (b, o) -> {
              seenBase.set(b);
              seenOverlay.set(o);
              return b;
            });

    // when
    ExporterArgsMergers.merge(capturing, null, null, "exporter 'foo'");

    // then — the SPI contract promises non-null maps, so nulls are normalized before the call
    assertThat(seenBase.get()).isNotNull().isEmpty();
    assertThat(seenOverlay.get()).isNotNull().isEmpty();
  }

  @Test
  void shouldKeepWrapperMessageStableWhenMergerFailsWithoutAMessage() {
    // given — a merger that throws with no message at all
    final ExporterConfigMerger failing =
        recording(
            (b, o) -> {
              throw new IllegalStateException();
            });

    // when / then — the top-level text must never render "null"; the cause carries the detail
    assertThatThrownBy(
            () -> ExporterArgsMergers.merge(failing, Map.of(), Map.of(), "exporter 'foo'"))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessage("Failed to merge exporter args for exporter 'foo'")
        .hasRootCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectMutationOfTopLevelInputs() {
    // given
    final ExporterConfigMerger mutating = recording((b, o) -> b.put("injected", true));

    // when / then
    assertThatThrownBy(
            () ->
                ExporterArgsMergers.merge(
                    mutating, new LinkedHashMap<>(Map.of("a", 1)), Map.of(), "exporter 'foo'"))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasRootCauseInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldRejectMutationOfNestedMaps() {
    // given — the shape real exporter args have (connect/index/history are nested maps)
    final Map<String, Object> base = new LinkedHashMap<>();
    base.put("history", new LinkedHashMap<>(Map.of("rolloverInterval", "2d")));
    final ExporterConfigMerger mutating =
        recording(
            (b, o) -> {
              @SuppressWarnings("unchecked")
              final var nested = (Map<String, Object>) b.get("history");
              return nested.put("rolloverInterval", "tampered");
            });

    // when / then — a shallow copy would let this through and corrupt the caller's live map
    assertThatThrownBy(() -> ExporterArgsMergers.merge(mutating, base, Map.of(), "exporter 'foo'"))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasRootCauseInstanceOf(UnsupportedOperationException.class);

    // and the caller's map is untouched
    assertThat(base).extracting("history").isEqualTo(Map.of("rolloverInterval", "2d"));
  }

  @Test
  void shouldRejectMutationOfNestedLists() {
    // given — list-valued args exist in real configs (e.g. index rules), and neither caller's
    // tests reach this shape, so nothing else pins the list branch of the defensive copy
    final Map<String, Object> base = new LinkedHashMap<>();
    base.put("hosts", new ArrayList<>(List.of("http://a:9200")));
    final ExporterConfigMerger mutating =
        recording(
            (b, o) -> {
              @SuppressWarnings("unchecked")
              final var hosts = (List<String>) b.get("hosts");
              return hosts.add("http://evil:9200");
            });

    // when / then
    assertThatThrownBy(() -> ExporterArgsMergers.merge(mutating, base, Map.of(), "exporter 'foo'"))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasRootCauseInstanceOf(UnsupportedOperationException.class);

    // and the caller's list is untouched
    assertThat(base).extracting("hosts").isEqualTo(List.of("http://a:9200"));
  }

  @Test
  void shouldRejectMutationOfMapsNestedInsideLists() {
    // given — recursion has to cross the map->list->map boundary, not just map->map
    final Map<String, Object> base = new LinkedHashMap<>();
    base.put("rules", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("name", "r1")))));
    final ExporterConfigMerger mutating =
        recording(
            (b, o) -> {
              @SuppressWarnings("unchecked")
              final var rules = (List<Map<String, Object>>) b.get("rules");
              return rules.getFirst().put("name", "tampered");
            });

    // when / then
    assertThatThrownBy(() -> ExporterArgsMergers.merge(mutating, base, Map.of(), "exporter 'foo'"))
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasRootCauseInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldCopyInputsWithoutLosingContentOrOrder() {
    // given — nesting must survive the defensive copy intact, or the merge silently sees less
    // than the operator configured
    final Map<String, Object> base = new LinkedHashMap<>();
    base.put("z", 1);
    base.put("a", 2);
    base.put("nested", new LinkedHashMap<>(Map.of("inner", List.of(1, 2, 3))));
    final AtomicReference<Map<String, Object>> seen = new AtomicReference<>();
    final ExporterConfigMerger capturing =
        recording(
            (b, o) -> {
              seen.set(b);
              return b;
            });

    // when
    ExporterArgsMergers.merge(capturing, base, Map.of(), "exporter 'foo'");

    // then
    assertThat(seen.get()).containsExactlyEntriesOf(base);
    assertThat(seen.get()).containsKeys("z", "a", "nested");
  }

  @Test
  void shouldDiscoverMergersRegisteredOnTheClasspath() {
    // when — the module's own test services file registers several mergers
    final List<ExporterConfigMerger> mergers = ExporterArgsMergers.load();

    // then — discovery works and returns instantiated providers, not just descriptors
    assertThat(mergers).isNotEmpty().doesNotContainNull();
  }

  private static ExporterConfigMerger recording(final MergeBody body) {
    return new ExporterConfigMerger() {
      @Override
      public boolean supports(final String className) {
        return true;
      }

      @Override
      public Map<String, Object> merge(
          final Map<String, Object> rootArgs, final Map<String, Object> tenantArgs) {
        body.apply(rootArgs, tenantArgs);
        return rootArgs;
      }
    };
  }

  /** Merger that claims one class and is never expected to merge. */
  private record ClaimingMerger(String claimed) implements ExporterConfigMerger {

    @Override
    public boolean supports(final String className) {
      return claimed.equals(className);
    }

    @Override
    public Map<String, Object> merge(
        final Map<String, Object> rootArgs, final Map<String, Object> tenantArgs) {
      return tenantArgs;
    }
  }

  /** Merger that merges top level only, overlay winning. */
  private static final class TopLevelMerger implements ExporterConfigMerger {

    @Override
    public boolean supports(final String className) {
      return true;
    }

    @Override
    public Map<String, Object> merge(
        final Map<String, Object> rootArgs, final Map<String, Object> tenantArgs) {
      final Map<String, Object> merged = new LinkedHashMap<>(rootArgs);
      merged.putAll(tenantArgs);
      return merged;
    }
  }

  @FunctionalInterface
  private interface MergeBody {
    Object apply(Map<String, Object> rootArgs, Map<String, Object> tenantArgs);
  }
}
