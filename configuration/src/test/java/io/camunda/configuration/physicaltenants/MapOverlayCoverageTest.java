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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Coverage guard over the per-physical-tenant map-merge policy: <strong>every typed {@code
 * Map<String, Pojo>} reachable in the {@link Camunda} config tree must have a deliberate
 * decision</strong> — either deep-merged by a {@link MapOverlaySpec} registered in {@link
 * PhysicalTenantMapOverlays#REGISTRY}, or explicitly listed in {@link
 * MapOverlaySurface#DEFAULT_MERGE_ALLOWLIST} as keeping Spring's default entry-level merge.
 *
 * <p>The tree is walked reflectively from {@link Camunda} down through every nested config POJO, so
 * <strong>any new {@code Map<String, Pojo>} property automatically surfaces here</strong> and fails
 * the build until it is registered for deep-merge or added to the allowlist — the same forcing
 * function as {@link PhysicalTenantOverridablePropertiesGoldenTest}, but for the map-merge policy
 * rather than the override policy.
 *
 * <p><b>Boundary — the walk stops at each map.</b> A map's value type is not descended into:
 *
 * <ul>
 *   <li>a POJO map nested inside a <em>deep-merged</em> value type is guarded separately by {@link
 *       MapOverlayNestedMapGuardTest} (the engine does not merge it recursively);
 *   <li>a POJO map nested inside a <em>default-merged</em> entry is replaced wholesale together
 *       with that entry, which is the already-accepted default-merge behavior — no separate
 *       decision to make.
 * </ul>
 *
 * <p>Recursion is confined to {@code io.camunda} config types; JDK, framework, and other third
 * party types are treated as opaque so the walk stays inside the Camunda configuration surface.
 */
class MapOverlayCoverageTest {

  @Test
  void everyPojoValuedMapIsRegisteredOrAllowlisted() {
    final Map<String, Class<?>> pojoMaps = discoverPojoValuedMaps();
    final Set<String> deepMerged = MapOverlaySurface.deepMergedMapPaths();

    final List<String> unclassified = new ArrayList<>();
    pojoMaps.forEach(
        (path, valueType) -> {
          if (!deepMerged.contains(path)
              && !MapOverlaySurface.DEFAULT_MERGE_ALLOWLIST.contains(path)) {
            unclassified.add(path + " (Map value type " + valueType.getName() + ")");
          }
        });

    assertThat(unclassified)
        .as(
            "every Map<String, Pojo> in the Camunda config tree must have a per-physical-tenant "
                + "merge decision. For each map below, either register it for deep-merge in a "
                + "MapOverlaySpec (PhysicalTenantMapOverlays.REGISTRY) so a partial tenant override "
                + "keeps the entry's unrestated fields, or add it to "
                + "MapOverlaySurface.DEFAULT_MERGE_ALLOWLIST to accept Spring's entry-level merge.")
        .isEmpty();
  }

  @Test
  void everyDeepMergedMapIsReachableInTheTree() {
    // the registry must not reference a map path that no longer exists in the config tree
    assertThat(discoverPojoValuedMaps().keySet())
        .as(
            "every map registered for deep-merge in PhysicalTenantMapOverlays.REGISTRY must be "
                + "reachable as a Map<String, Pojo> in the Camunda config tree")
        .containsAll(MapOverlaySurface.deepMergedMapPaths());
  }

  @Test
  void shouldDiscoverTheKnownPojoValuedMaps() {
    // guards against a broken walk making the coverage assertion vacuously green
    assertThat(discoverPojoValuedMaps().keySet())
        .contains(
            "camunda.data.exporters",
            "camunda.document.aws",
            "camunda.security.authentication.providers.oidc");
  }

  private static Map<String, Class<?>> discoverPojoValuedMaps() {
    final Map<String, Class<?>> found = new TreeMap<>();
    collect(Camunda.class, Camunda.PREFIX, new LinkedHashSet<>(), found);
    return found;
  }

  private static void collect(
      final Class<?> type,
      final String path,
      final Set<Class<?>> onPath,
      final Map<String, Class<?>> found) {
    if (!onPath.add(type)) {
      return; // cycle on the current path
    }
    for (Class<?> current = type;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (final Field field : current.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
          continue;
        }
        final String fieldPath = path + "." + MapOverlaySurface.camelToKebab(field.getName());
        final Class<?> mapValueType = MapOverlaySurface.mapValueType(field);
        if (mapValueType != null) {
          // stop at maps (see class javadoc): record POJO-valued ones, ignore scalar-valued ones
          if (MapOverlaySurface.isPojo(mapValueType)) {
            found.put(fieldPath, mapValueType);
          }
          continue;
        }
        if (isCamundaConfigType(field.getType())) {
          collect(field.getType(), fieldPath, onPath, found);
        }
      }
    }
    onPath.remove(type);
  }

  /**
   * Only {@code io.camunda} POJOs are descended into: the Camunda configuration surface is entirely
   * Camunda-owned, and treating JDK/framework types as opaque keeps the walk from wandering into
   * unrelated third-party object graphs.
   */
  private static boolean isCamundaConfigType(final Class<?> type) {
    return MapOverlaySurface.isPojo(type) && type.getName().startsWith("io.camunda.");
  }
}
