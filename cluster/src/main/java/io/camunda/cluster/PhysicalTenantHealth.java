/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cluster;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The health of one physical tenant on this node. It never affects the liveness of the node: a
 * tenant that is restarting, degraded or failed leaves the other tenants and the node running.
 *
 * @param state the lifecycle state
 * @param detail why the tenant is in this state, for operators; {@code null} when there is nothing
 *     to add
 */
@NullMarked
public record PhysicalTenantHealth(State state, @Nullable String detail) {

  public PhysicalTenantHealth {
    Objects.requireNonNull(state, "state must not be null");
  }

  public static PhysicalTenantHealth of(final State state) {
    return new PhysicalTenantHealth(state, null);
  }

  public enum State {
    /** The tenant module is starting and does not serve requests yet. */
    STARTING,
    /** The tenant module runs and every local partition of the tenant is installed. */
    HEALTHY,
    /** The tenant module is stopping or starting again to apply a configuration change. */
    RESTARTING,
    /** The tenant serves requests, but a component reports a problem, e.g. a stale secret. */
    DEGRADED,
    /** The tenant module could not start and needs another configuration change to recover. */
    FAILED,
    /** The tenant was removed from this node; its data stays on disk. */
    REMOVED;

    public boolean isServing() {
      return this == HEALTHY || this == DEGRADED;
    }
  }
}
