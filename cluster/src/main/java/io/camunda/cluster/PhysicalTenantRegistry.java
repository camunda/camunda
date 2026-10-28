/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cluster;

import java.util.Collection;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * The live set of physical tenants on this node, with the health of each tenant. Unlike a plain
 * {@link PhysicalTenantIds}, the set changes while the node runs: {@link #known()} must be read at
 * the time of use and never copied.
 */
@NullMarked
public interface PhysicalTenantRegistry extends PhysicalTenantIds {

  Optional<PhysicalTenant> find(String physicalTenantId);

  /** The tenants that are currently part of this node, in the order they were added. */
  Collection<PhysicalTenant> tenants();

  /**
   * @return the health of a current or removed tenant; empty if the id was never known
   */
  Optional<PhysicalTenantHealth> health(String physicalTenantId);

  /**
   * Reports the health of a current tenant, e.g. {@link PhysicalTenantHealth.State#DEGRADED} by a
   * component that refreshes the secrets of a tenant. {@link PhysicalTenantHealth.State#REMOVED}
   * can only be reached by removing the tenant.
   *
   * @throws IllegalArgumentException if the tenant is not part of this node
   */
  void reportHealth(String physicalTenantId, PhysicalTenantHealth health);

  void addListener(Listener listener);

  void removeListener(Listener listener);

  /**
   * Observes changes to the tenant set. Callbacks run synchronously on the thread that changes the
   * set: {@link #onAdded} before the tenant module starts, {@link #onRemoved} after it stopped, and
   * {@link #onReplaced} while it is stopped. A listener can therefore own a per-tenant resource,
   * such as a scheduler, for the lifetime of the tenant module. An exception thrown by a listener
   * fails the change.
   */
  interface Listener {

    default void onAdded(final PhysicalTenant tenant) {}

    default void onReplaced(final PhysicalTenant previous, final PhysicalTenant current) {}

    default void onRemoved(final PhysicalTenant tenant) {}

    default void onHealthChanged(
        final String physicalTenantId, final PhysicalTenantHealth health) {}
  }
}
