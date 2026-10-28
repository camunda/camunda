/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cluster;

import io.camunda.cluster.PhysicalTenantHealth.State;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.NullMarked;

/**
 * Thread-safe {@link PhysicalTenantRegistry} whose set is changed by the component that starts and
 * stops tenant modules. Reads never block; changes are serialized.
 *
 * <p>A removed id stays reserved: adding it again is rejected until its data is purged.
 */
@NullMarked
public final class DefaultPhysicalTenantRegistry implements PhysicalTenantRegistry {

  private final Object lock = new Object();
  private final List<Listener> listeners = new CopyOnWriteArrayList<>();
  private final Map<String, PhysicalTenantHealth> health = new ConcurrentHashMap<>();
  private volatile Map<String, PhysicalTenant> tenants;

  public DefaultPhysicalTenantRegistry(final Collection<PhysicalTenant> initialTenants) {
    final var initial = new LinkedHashMap<String, PhysicalTenant>();
    initialTenants.forEach(
        tenant -> {
          if (initial.putIfAbsent(tenant.id(), tenant) != null) {
            throw new IllegalArgumentException(
                "Physical tenant '%s' is listed more than once".formatted(tenant.id()));
          }
          health.put(tenant.id(), PhysicalTenantHealth.of(State.STARTING));
        });
    tenants = Collections.unmodifiableMap(initial);
  }

  @Override
  public Set<String> known() {
    return tenants.keySet();
  }

  @Override
  public Optional<PhysicalTenant> find(final String physicalTenantId) {
    return Optional.ofNullable(tenants.get(physicalTenantId));
  }

  @Override
  public Collection<PhysicalTenant> tenants() {
    return tenants.values();
  }

  @Override
  public Optional<PhysicalTenantHealth> health(final String physicalTenantId) {
    return Optional.ofNullable(health.get(physicalTenantId));
  }

  @Override
  public void reportHealth(final String physicalTenantId, final PhysicalTenantHealth newHealth) {
    if (newHealth.state() == State.REMOVED) {
      throw new IllegalArgumentException(
          "Physical tenant '%s' can only become REMOVED by removing it"
              .formatted(physicalTenantId));
    }
    synchronized (lock) {
      requireCurrent(physicalTenantId);
      if (newHealth.equals(health.put(physicalTenantId, newHealth))) {
        return;
      }
    }
    listeners.forEach(listener -> listener.onHealthChanged(physicalTenantId, newHealth));
  }

  public boolean isRemoved(final String physicalTenantId) {
    return !tenants.containsKey(physicalTenantId)
        && health(physicalTenantId).map(h -> h.state() == State.REMOVED).orElse(false);
  }

  /**
   * @throws IllegalStateException if the id is part of this node, or was removed and is still
   *     reserved
   */
  public void add(final PhysicalTenant tenant) {
    final var startingHealth = PhysicalTenantHealth.of(State.STARTING);
    synchronized (lock) {
      if (tenants.containsKey(tenant.id())) {
        throw new IllegalStateException(
            "Physical tenant '%s' already exists".formatted(tenant.id()));
      }
      if (isRemoved(tenant.id())) {
        throw new IllegalStateException(
            "Physical tenant id '%s' was removed and stays reserved until its data is purged"
                .formatted(tenant.id()));
      }
      final var updated = new LinkedHashMap<>(tenants);
      updated.put(tenant.id(), tenant);
      tenants = Collections.unmodifiableMap(updated);
      health.put(tenant.id(), startingHealth);
    }
    listeners.forEach(listener -> listener.onAdded(tenant));
    listeners.forEach(listener -> listener.onHealthChanged(tenant.id(), startingHealth));
  }

  /**
   * Replaces the descriptor of a current tenant, e.g. to change its name, and keeps its
   * incarnation.
   *
   * @return the previous descriptor
   */
  public PhysicalTenant replace(final PhysicalTenant tenant) {
    final PhysicalTenant previous;
    synchronized (lock) {
      previous = requireCurrent(tenant.id());
      if (!previous.incarnationId().equals(tenant.incarnationId())) {
        throw new IllegalArgumentException(
            "A replace of physical tenant '%s' must keep its incarnation %s, but was %s"
                .formatted(tenant.id(), previous.incarnationId(), tenant.incarnationId()));
      }
      final var updated = new LinkedHashMap<>(tenants);
      updated.put(tenant.id(), tenant);
      tenants = Collections.unmodifiableMap(updated);
    }
    listeners.forEach(listener -> listener.onReplaced(previous, tenant));
    return previous;
  }

  /**
   * @return the removed descriptor
   */
  public PhysicalTenant remove(final String physicalTenantId) {
    final PhysicalTenant removed;
    final var removedHealth = PhysicalTenantHealth.of(State.REMOVED);
    synchronized (lock) {
      removed = requireCurrent(physicalTenantId);
      final var updated = new LinkedHashMap<>(tenants);
      updated.remove(physicalTenantId);
      tenants = Collections.unmodifiableMap(updated);
      health.put(physicalTenantId, removedHealth);
    }
    listeners.forEach(listener -> listener.onRemoved(removed));
    listeners.forEach(listener -> listener.onHealthChanged(physicalTenantId, removedHealth));
    return removed;
  }

  @Override
  public void addListener(final Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(final Listener listener) {
    listeners.remove(listener);
  }

  private PhysicalTenant requireCurrent(final String physicalTenantId) {
    final var tenant = tenants.get(physicalTenantId);
    if (tenant == null) {
      throw new IllegalArgumentException(
          "Unknown physical tenant '%s'".formatted(physicalTenantId));
    }
    return tenant;
  }
}
