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
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.IgnoreTopLevelConverterNotFoundBindHandler;
import org.springframework.core.env.Environment;

/**
 * Generic snapshot-then-rebind overlay engine for per-physical-tenant configs whose {@code
 * Map<String, V>} properties need deep merging.
 *
 * <p>Spring's {@code MapBinder} merges maps at the entry level only: entries the tenant overlay
 * does not touch survive, but a touched entry is rebuilt from just the tenant's sub-keys — a tenant
 * overriding one field of a shared entry would silently lose every root field it did not restate.
 * Worse, Spring Boot 4.1 surfaces an empty YAML map ({@code providers: {}}) as an empty property
 * value, and binding that resets the whole map — dropping every inherited root entry. The engine
 * repairs both: bind root → snapshot each registered map → bind the tenant overlay onto the same
 * instance → re-establish the root ∪ overlay union by re-binding, for <em>every</em> key the root
 * declared (whether or not it survived the overlay bind), the tenant sub-prefix onto {@code
 * Bindable.ofInstance(rootValue)} so Spring layers only the tenant's delta onto the fully-populated
 * root POJO, and putting it back.
 *
 * <p>Boundary: only the maps registered in the spec are repaired. A typed {@code Map<String, Pojo>}
 * nested inside a registered map's value type would still lose untouched inner fields — {@code
 * MapOverlayNestedMapGuardTest} fails the build if such a value type is registered.
 */
@NullMarked
final class PhysicalTenantMapOverlay {

  /**
   * Spring Boot 4.1 surfaces an empty YAML map ({@code providers: {}}) as an empty-string property
   * at the map's own path. Because a spec's overlay bind is rooted at the config root itself, that
   * value sits at the top level of the bind and a raw {@link Binder#bind} fails with a {@code
   * ConverterNotFoundException}. Production {@code @ConfigurationProperties} binding tolerates
   * exactly this via {@link IgnoreTopLevelConverterNotFoundBindHandler} — use the same handler so
   * an empty tenant overlay means "nothing to overlay" instead of a startup crash.
   */
  private static final BindHandler EMPTY_TOLERANT_HANDLER =
      new IgnoreTopLevelConverterNotFoundBindHandler();

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
            .bind(spec.rootPrefix(), Bindable.of(spec.rootType()), EMPTY_TOLERANT_HANDLER)
            .orElseGet(spec.defaultFactory());

    final String ptPrefix = spec.ptPrefix(tenantId);

    // Snapshot every map BEFORE the overlay bind mutates it; defer the per-key rebind.
    final List<Runnable> rebinds = new ArrayList<>();
    for (final MapDescriptor<R, ?> descriptor : spec.maps()) {
      rebinds.add(prepareRebind(descriptor, root, binder, ptPrefix));
    }
    binder.bind(ptPrefix, Bindable.ofInstance(root), EMPTY_TOLERANT_HANDLER);
    rebinds.forEach(Runnable::run);

    spec.postProcess().accept(new OverlayContext(tenantId, ptPrefix, binder), root);
    return root;
  }

  private static <R, V> Runnable prepareRebind(
      final MapDescriptor<R, V> descriptor,
      final R root,
      final Binder binder,
      final String ptPrefix) {
    // A null map (a spec root that leaves this map unset) has no root keys to preserve, so there is
    // no rebind work: snapshot as empty and skip.
    final Map<String, V> rootMap = descriptor.accessor().apply(root);
    final Map<String, V> snapshot = rootMap == null ? Map.of() : new LinkedHashMap<>(rootMap);
    return () -> {
      if (snapshot.isEmpty()) {
        return;
      }
      // Re-fetch AFTER the overlay bind: Spring's MapBinder.merge returns a COPY and the bean
      // binder calls the setter, so the map captured before the bind may be stale. The re-fetched
      // map cannot be null here: every registered value root keeps its maps non-null
      // (field-initialized, null-coercing setters), and without a setter on MapDescriptor a null
      // map could not be repaired anyway.
      final Map<String, V> resolved = descriptor.accessor().apply(root);
      if (resolved == null) {
        return;
      }
      snapshot.forEach(
          (key, rootValue) -> {
            // Apply the tenant delta onto the pristine root POJO, then swap it back in. Both steps
            // are required: the bind layers the override, the put restores the merged result. The
            // put is unconditional — a root key missing from the post-overlay map means an empty
            // tenant map value reset it (Spring Boot 4.1 empty-YAML-map behavior), and inherited
            // entries must survive that too.
            binder.bind(
                ptPrefix + "." + descriptor.subPath() + "." + key, Bindable.ofInstance(rootValue));
            resolved.put(key, rootValue);
          });
    };
  }
}
