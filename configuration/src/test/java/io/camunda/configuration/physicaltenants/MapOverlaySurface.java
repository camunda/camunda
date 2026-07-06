/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.MapOverlaySpec.MapDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared reflection and policy support for the two guards over the per-physical-tenant map-overlay
 * surface: {@link MapOverlayCoverageTest} and {@link MapOverlayNestedMapGuardTest}. Extracted so
 * the {@code Map<String, V>} classification lives in one place — a divergent {@link #isPojo}
 * between the two guards is exactly the kind of drift these tests exist to prevent.
 *
 * <p>Every typed {@code Map<String, V>} in the {@link Camunda} config tree is handled per physical
 * tenant in exactly one of two ways:
 *
 * <ul>
 *   <li><b>deep-merged</b> by {@link PhysicalTenantMapOverlay} — the map is declared in a {@link
 *       MapOverlaySpec} registered in {@link PhysicalTenantMapOverlays#REGISTRY}, so a partial
 *       tenant override of one entry keeps that entry's unrestated root fields;
 *   <li><b>default-merged</b> — the map keeps Spring's entry-level {@code MapBinder} merge, where a
 *       touched entry is rebuilt from just the tenant's sub-keys. This is only safe when losing an
 *       entry's unrestated sibling fields does no harm, e.g. an opaque {@code Map<String, Object>}
 *       whose values have no bindable sub-fields. Such maps are listed in {@link
 *       #DEFAULT_MERGE_ALLOWLIST}.
 * </ul>
 */
final class MapOverlaySurface {

  /**
   * POJO-valued maps that deliberately keep Spring's default entry-level merge instead of the
   * per-tenant deep-merge engine. Paths are canonical dashed property names below the {@code
   * camunda} root; a {@code *} segment stands for an arbitrary map-entry key (used only for maps
   * reached <em>through</em> another map's value type).
   *
   * <ul>
   *   <li>{@code camunda.data.exporters} — the exporter entry is default-merged; deep-merging its
   *       {@code args} is a separate, opt-in mechanism (see {@link
   *       ExporterArgsOverlayCharacterizationTest}), not this overlay engine.
   *   <li>the OIDC {@code authorize-request-configuration.additional-parameters} maps ({@code
   *       Map<String, Object>} of opaque request parameters) — a touched entry has no bindable
   *       sub-fields to lose, so wholesale replacement is harmless. Reachable both via the flat
   *       {@code authentication.oidc} slot and, per named provider, under {@code
   *       authentication.providers.oidc.*}.
   *   <li>{@code camunda.data.primary-storage.rocks-db.column-family-options} — a {@code
   *       java.util.Properties} bag of opaque RocksDB tuning options, again with no bindable
   *       sub-fields per entry.
   * </ul>
   */
  static final Set<String> DEFAULT_MERGE_ALLOWLIST =
      Set.of(
          "camunda.data.exporters",
          "camunda.security.authentication.oidc.authorize-request-configuration.additional-parameters",
          "camunda.security.authentication.providers.oidc.*.authorize-request-configuration.additional-parameters",
          "camunda.data.primary-storage.rocks-db.column-family-options");

  private MapOverlaySurface() {}

  /**
   * Property paths of every map deep-merged by the overlay engine, derived from the live {@link
   * PhysicalTenantMapOverlays#REGISTRY}. Canonical dashed names below the {@code camunda} root,
   * e.g. {@code camunda.document.aws}, {@code camunda.security.authentication.providers.oidc}.
   */
  static Set<String> deepMergedMapPaths() {
    final Set<String> paths = new LinkedHashSet<>();
    for (final MapOverlaySpec<?> spec : PhysicalTenantMapOverlays.REGISTRY) {
      for (final MapDescriptor<?, ?> descriptor : spec.maps()) {
        paths.add(Camunda.PREFIX + "." + spec.configPath() + "." + descriptor.subPath());
      }
    }
    return paths;
  }

  /**
   * Whether losing an untouched entry's sibling fields on a partial tenant override would be
   * <em>silent config loss</em> — true for a value type with bindable sub-fields (a POJO), false
   * for scalars, enums, arrays, and JDK-owned types (whose {@code Map<String, V>} entries are
   * replaced wholesale with no sub-fields to lose). {@code Object} is treated as a POJO: an opaque
   * {@code Map<String, Object>} cannot be proven scalar, so it is forced through an explicit
   * classification rather than silently assumed safe.
   */
  static boolean isPojo(final Class<?> type) {
    if (type == Object.class) {
      return true;
    }
    if (type.isPrimitive() || type.isEnum() || type.isArray()) {
      return false;
    }
    final String name = type.getName();
    return !name.startsWith("java.") && !name.startsWith("javax.");
  }

  /** Returns the map's value class if the field is a {@code Map<?, V>}, otherwise {@code null}. */
  static Class<?> mapValueType(final Field field) {
    if (!Map.class.isAssignableFrom(field.getType())) {
      return null;
    }
    if (field.getGenericType() instanceof final ParameterizedType parameterized
        && parameterized.getActualTypeArguments().length == 2) {
      final Type valueArg = parameterized.getActualTypeArguments()[1];
      if (valueArg instanceof final Class<?> valueClass) {
        return valueClass;
      }
      if (valueArg instanceof final ParameterizedType nested
          && nested.getRawType() instanceof final Class<?> raw) {
        // e.g. Map<String, Map<..>> or Map<String, List<..>>: the value is a JDK container, not a
        // POJO — return the raw container class so isPojo classifies it as scalar-safe.
        return raw;
      }
    }
    // raw or wildcard-typed map: cannot prove the value type is scalar, treat as POJO-valued
    return Object.class;
  }

  /**
   * Canonical dashed form of a camelCase field name, matching Spring's relaxed property binding.
   */
  static String camelToKebab(final String camel) {
    return camel.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
  }
}
