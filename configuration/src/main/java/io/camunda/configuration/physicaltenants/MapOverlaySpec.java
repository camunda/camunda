/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.bind.Binder;

/**
 * Declares one config root whose typed-POJO {@code Map<String, V>} properties must be deep-merged
 * per physical tenant by {@link PhysicalTenantMapOverlay}. The specs registered in {@link
 * PhysicalTenantMapOverlays#REGISTRY} are the single source of truth for which map paths are
 * deep-merged — both the overlay engine and the override golden test derive their surface from it.
 *
 * @param configPath property path of the config root below the {@code camunda} root (e.g. {@code
 *     document}, {@code security.authentication}); the shared root prefix and the per-tenant
 *     overlay prefix are both derived from it, because the resolver's two-bind requires the tenant
 *     override to live at the same relative path re-rooted under {@code physical-tenants.<id>}
 * @param rootType type the root prefix binds to
 * @param defaultFactory fallback when nothing is bound under the root prefix
 * @param maps the {@code Map<String, V>} properties reachable from the root that need the
 *     snapshot-then-rebind repair
 * @param prepare optional domain hook run after the root bind, before the tenant overlay
 * @param postProcess optional domain hook run after the overlay completed
 * @param installer attaches the resolved config to the per-tenant {@link Camunda}
 */
@NullMarked
record MapOverlaySpec<R>(
    String configPath,
    Class<R> rootType,
    Supplier<R> defaultFactory,
    List<MapDescriptor<R, ?>> maps,
    BiConsumer<OverlayContext, R> prepare,
    BiConsumer<OverlayContext, R> postProcess,
    BiConsumer<Camunda, R> installer) {

  static <R> BiConsumer<OverlayContext, R> noHook() {
    return (context, root) -> {};
  }

  /** Property prefix of the shared root config, e.g. {@code camunda.document}. */
  String rootPrefix() {
    return Camunda.PREFIX + "." + configPath;
  }

  /** Per-tenant overlay prefix, e.g. {@code camunda.physical-tenants.tenanta.document}. */
  String ptPrefix(final String tenantId) {
    return PhysicalTenantResolver.PHYSICAL_TENANTS_PREFIX + "." + tenantId + "." + configPath;
  }

  /**
   * A single per-tenant-overlaid {@code Map<String, V>} reachable from the config root {@code R}.
   * The engine repairs only the maps registered here; a typed map nested <em>inside</em> {@code V}
   * is not recursively merged — {@code MapOverlayNestedMapGuardTest} rejects such value types.
   *
   * @param subPath property path of the map relative to the root prefix (may span segments, e.g.
   *     {@code providers.oidc})
   * @param valueType the map's value type, used to guard/enumerate its per-field surface
   * @param accessor reads the live map from the bound root instance
   */
  record MapDescriptor<R, V>(
      String subPath, Class<V> valueType, Function<R, Map<String, V>> accessor) {}

  /** Per-invocation context handed to the {@code prepare}/{@code postProcess} hooks. */
  record OverlayContext(String tenantId, String ptPrefix, Binder binder) {}
}
