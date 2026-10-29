/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.env.Environment;

/**
 * The authentication configuration each physical tenant's HTTP security serves on this node, the
 * {@code default} tenant included. It starts as the startup configuration and follows every change
 * that the security library applied, so it never runs ahead of the chains: a change that was kept
 * back (an unreachable identity provider) leaves the previous configuration here too.
 *
 * <p>Host-side state that is derived from a tenant's configuration, such as its claim converters,
 * reads it through {@link #derive}, so that it is rebuilt from the configuration the tenant is
 * served with and released when the tenant is removed.
 */
@NullMarked
public final class PhysicalTenantAuthentications {

  private final Map<String, AuthenticationConfiguration> byPhysicalTenant =
      new ConcurrentHashMap<>();
  private final List<Derived<?>> derived = new CopyOnWriteArrayList<>();

  public PhysicalTenantAuthentications(
      final Map<String, AuthenticationConfiguration> startupConfiguration) {
    byPhysicalTenant.putAll(startupConfiguration);
  }

  /** The tenants of the startup configuration, {@code default} included. */
  public static PhysicalTenantAuthentications of(final Environment environment) {
    return new PhysicalTenantAuthentications(
        PhysicalTenantAuthConfigurations.forAllPhysicalTenants(environment));
  }

  public Optional<AuthenticationConfiguration> find(final String physicalTenantId) {
    return Optional.ofNullable(byPhysicalTenant.get(physicalTenantId));
  }

  public Set<String> physicalTenantIds() {
    return Set.copyOf(byPhysicalTenant.keySet());
  }

  /** Records the configuration the tenant's HTTP security serves from now on. */
  public void put(final String physicalTenantId, final AuthenticationConfiguration authentication) {
    byPhysicalTenant.put(physicalTenantId, authentication);
  }

  /** Forgets a removed tenant, and everything derived from its configuration. */
  public void remove(final String physicalTenantId) {
    byPhysicalTenant.remove(physicalTenantId);
    derived.forEach(view -> view.evict(physicalTenantId));
  }

  /**
   * A per-tenant value built from the tenant's configuration, built again whenever the
   * configuration the tenant is served with changes.
   */
  public <T> Derived<T> derive(final BiFunction<String, AuthenticationConfiguration, T> build) {
    final var view = new Derived<>(this, build);
    derived.add(view);
    return view;
  }

  /** A per-tenant value that follows the tenant's configuration. */
  public static final class Derived<T> {

    private final PhysicalTenantAuthentications source;
    private final BiFunction<String, AuthenticationConfiguration, T> build;
    private final Map<String, Built<T>> byPhysicalTenant = new ConcurrentHashMap<>();

    private Derived(
        final PhysicalTenantAuthentications source,
        final BiFunction<String, AuthenticationConfiguration, T> build) {
      this.source = source;
      this.build = build;
    }

    /**
     * @return the value built from the configuration the tenant is served with now, or empty if the
     *     tenant is not served
     */
    public Optional<T> get(final String physicalTenantId) {
      final var authentication = source.byPhysicalTenant.get(physicalTenantId);
      if (authentication == null) {
        byPhysicalTenant.remove(physicalTenantId);
        return Optional.empty();
      }
      final var current = byPhysicalTenant.get(physicalTenantId);
      if (current != null && current.from() == authentication) {
        return Optional.of(current.value());
      }
      return Optional.of(
          byPhysicalTenant
              .compute(
                  physicalTenantId,
                  (id, built) ->
                      built != null && built.from() == authentication
                          ? built
                          : new Built<>(authentication, build.apply(id, authentication)))
              .value());
    }

    /** The tenants a value is held for, which never exceeds the served tenants for long. */
    public Set<String> heldFor() {
      return Set.copyOf(byPhysicalTenant.keySet());
    }

    private void evict(final String physicalTenantId) {
      byPhysicalTenant.remove(physicalTenantId);
    }

    private record Built<T>(AuthenticationConfiguration from, T value) {}
  }
}
