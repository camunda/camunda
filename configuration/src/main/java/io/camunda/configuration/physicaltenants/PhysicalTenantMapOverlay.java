/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.physicaltenants.MapOverlaySpec.MapDescriptor;
import io.camunda.configuration.physicaltenants.MapOverlaySpec.OverlayContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Generic snapshot-then-rebind overlay engine for per-physical-tenant configs whose {@code
 * Map<String, V>} properties need deep merging.
 *
 * <p>Spring's {@code MapBinder} merges maps at the entry level only: entries the tenant overlay
 * does not touch survive, but a touched entry is rebuilt from just the tenant's sub-keys — a tenant
 * overriding one field of a shared entry would silently lose every root field it did not restate.
 * The engine repairs this: bind root → snapshot each registered map → bind the tenant overlay onto
 * the same instance → for each key the root declared, re-bind the tenant sub-prefix onto {@code
 * Bindable.ofInstance(rootValue)} so Spring layers only the tenant's delta onto the fully-populated
 * root POJO, and put it back.
 *
 * <p>Boundary: only the maps registered in the spec are repaired. A typed {@code Map<String, Pojo>}
 * nested inside a registered map's value type would still lose untouched inner fields — {@code
 * MapOverlayNestedMapGuardTest} fails the build if such a value type is registered.
 */
@NullMarked
final class PhysicalTenantMapOverlay {

  private PhysicalTenantMapOverlay() {}

  /**
   * Resolves the spec's config root for one tenant. The root POJO is bound fresh from the
   * environment on every invocation and mutated in place by the overlay — it must never be cached
   * or shared across tenants.
   */
  static <R> R overlay(
      final MapOverlaySpec<R> spec, final String tenantId, final Environment environment) {
    final Binder binder = Binder.get(environment);
    final R root =
        binder
            .bind(spec.rootPrefix(), Bindable.of(spec.rootType()))
            .orElseGet(spec.defaultFactory());

    final String ptPrefix = spec.ptPrefix(tenantId);
    final OverlayContext context = new OverlayContext(tenantId, ptPrefix, binder);
    spec.prepare().accept(context, root);

    // Snapshot every map BEFORE the overlay bind mutates it; defer the per-key rebind.
    final List<Runnable> rebinds = new ArrayList<>();
    for (final MapDescriptor<R, ?> descriptor : spec.maps()) {
      rebinds.add(prepareRebind(descriptor, root, binder, ptPrefix));
    }
    binder.bind(ptPrefix, Bindable.ofInstance(root));
    rebinds.forEach(Runnable::run);

    spec.postProcess().accept(context, root);
    return root;
  }

  private static <R, V> Runnable prepareRebind(
      final MapDescriptor<R, V> descriptor,
      final R root,
      final Binder binder,
      final String ptPrefix) {
    final Map<String, V> snapshot = new LinkedHashMap<>(descriptor.accessor().apply(root));
    return () -> {
      // Re-fetch AFTER the overlay bind: Spring's MapBinder.merge returns a COPY and the bean
      // binder calls the setter, so the map captured before the bind may be stale.
      final Map<String, V> resolved = descriptor.accessor().apply(root);
      snapshot.forEach(
          (key, rootValue) -> {
            if (resolved.containsKey(key)) {
              // Apply the tenant delta onto the pristine root POJO, then swap it back in for the
              // Binder-created entry that holds only the tenant-specified fields. Both steps are
              // required: the bind layers the override, the put restores the merged result.
              binder.bind(
                  ptPrefix + "." + descriptor.subPath() + "." + key,
                  Bindable.ofInstance(rootValue));
              resolved.put(key, rootValue);
            }
          });
    };
  }
}
