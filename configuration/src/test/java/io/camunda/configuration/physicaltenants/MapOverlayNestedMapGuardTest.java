/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.physicaltenants.MapOverlaySpec.MapDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Enforces the documented boundary of {@link PhysicalTenantMapOverlay}: the engine repairs only the
 * maps registered in a spec, so a typed {@code Map<String, Pojo>} nested <em>inside</em> a
 * registered map's value type would NOT be recursively merged — a tenant overriding one field of an
 * inner entry would silently lose the inner fields it did not restate (config loss).
 *
 * <p>This test walks the value-type graph of every registry entry and fails if such a nested
 * POJO-valued map appears. Maps with scalar values (e.g. OIDC's {@code
 * authorize-request.additional-parameters}, a {@code Map<String, String>}) are safe: a touched key
 * is replaced whole and has no sibling fields to lose. If this test starts failing after a value
 * type gained a nested POJO map (possibly in the external security library), the engine must learn
 * recursive merging first — a {@code BindHandler}-based mechanism is the known fallback able to
 * seed recursively.
 */
class MapOverlayNestedMapGuardTest {

  @Test
  void shouldNotRegisterValueTypesContainingNestedPojoMaps() {
    final List<String> violations = new ArrayList<>();
    for (final MapOverlaySpec<?> spec : PhysicalTenantMapOverlays.REGISTRY) {
      for (final MapDescriptor<?, ?> descriptor : spec.maps()) {
        collectNestedPojoMaps(
            descriptor.valueType(),
            spec.rootPrefix() + "." + descriptor.subPath() + ".*",
            new LinkedHashSet<>(),
            violations);
      }
    }
    assertThat(violations)
        .as(
            "registered map value types must not contain nested POJO-valued maps — "
                + "the overlay engine does not merge them recursively and untouched inner "
                + "fields would be silently dropped on partial tenant override")
        .isEmpty();
  }

  private static void collectNestedPojoMaps(
      final Class<?> type,
      final String path,
      final Set<Class<?>> visited,
      final List<String> violations) {
    if (!visited.add(type)) {
      return;
    }
    for (Class<?> current = type;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (final Field field : current.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        final String fieldPath = path + "." + field.getName();
        final Class<?> mapValueType = mapValueType(field);
        if (mapValueType != null) {
          if (isPojo(mapValueType)) {
            violations.add(fieldPath + " is a Map with POJO value type " + mapValueType.getName());
          }
          continue;
        }
        if (isPojo(field.getType())) {
          collectNestedPojoMaps(field.getType(), fieldPath, visited, violations);
        }
      }
    }
  }

  /** Returns the map's value class if the field is a {@code Map<?, V>}, otherwise {@code null}. */
  private static Class<?> mapValueType(final Field field) {
    if (!Map.class.isAssignableFrom(field.getType())) {
      return null;
    }
    if (field.getGenericType() instanceof final ParameterizedType parameterized
        && parameterized.getActualTypeArguments().length == 2
        && parameterized.getActualTypeArguments()[1] instanceof final Class<?> valueClass) {
      return valueClass;
    }
    // raw or wildcard-typed map: cannot prove the value type is scalar, treat as POJO-valued
    return Object.class;
  }

  private static boolean isPojo(final Class<?> type) {
    if (type.isPrimitive() || type.isEnum() || type.isArray()) {
      return false;
    }
    final String name = type.getName();
    return !name.startsWith("java.") && !name.startsWith("javax.");
  }

  /** Guards against a silently empty registry making the walk above vacuously green. */
  @Test
  void shouldWalkTheRealRegistry() {
    assertThat(PhysicalTenantMapOverlays.REGISTRY).hasSizeGreaterThanOrEqualTo(2);
  }
}
