/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import org.jspecify.annotations.NullMarked;

/**
 * Whether a physical tenant (a {@link PartitionGroupConfiguration}) is currently running, and if
 * not why. The three cases are mutually exclusive, so they are one {@link State} rather than
 * independent flags.
 *
 * <p>{@code version} is this sub-record's own version, deliberately independent of the enclosing
 * {@link PartitionGroupConfiguration#version()}: changing availability must not bump the group
 * version, since availability is orthogonal to partition-assignment/plan-boundary changes. Merge is
 * highest-own-version-wins ({@link #merge(TenantAvailability)}), the same pattern as {@link
 * BrokerPartitionState}, and must be applied independently of whichever branch of {@link
 * PartitionGroupConfiguration#merge(PartitionGroupConfiguration)} is taken — otherwise an unrelated
 * bump of the group's top-level version could silently overwrite a more recently changed value.
 *
 * <p>That independent version is also why a removal converges instead of vanishing: {@link
 * CurrentClusterConfiguration#merge(CurrentClusterConfiguration)} unions the partition-group map by
 * key, so a deleted group would just be resurrected by gossip from any broker that still holds a
 * copy. Recording the removal here and bumping this version instead lets it win over every stale
 * copy.
 *
 * @param version this sub-record's own version, bumped on every state change
 * @param state whether the tenant is running, and if not why
 */
@NullMarked
public record TenantAvailability(long version, State state) {

  public static final long INITIAL_VERSION = 0;

  public static TenantAvailability enabled() {
    return new TenantAvailability(INITIAL_VERSION, State.ENABLED);
  }

  /**
   * Returns a {@link State#DISABLED} instance with an incremented version, or {@code this} if the
   * tenant is already out of service — whether disabled or removed.
   */
  public TenantAvailability disable() {
    return state == State.ENABLED ? new TenantAvailability(version + 1, State.DISABLED) : this;
  }

  /**
   * Returns an {@link State#ENABLED} instance with an incremented version, or {@code this} if
   * already enabled. Returns {@code this} for a removed tenant: {@link State#REMOVED} is terminal
   * and is not undone by the tenant reappearing in static configuration.
   */
  public TenantAvailability enable() {
    return state == State.DISABLED ? new TenantAvailability(version + 1, State.ENABLED) : this;
  }

  /**
   * Returns a {@link State#REMOVED} instance with an incremented version, or {@code this} if
   * already removed. The version bump is what makes the removal survive a merge with a peer that
   * has not seen it yet.
   */
  public TenantAvailability remove() {
    return state == State.REMOVED ? this : new TenantAvailability(version + 1, State.REMOVED);
  }

  /**
   * Returns whichever of this and {@code other} has the higher own version; {@code this} wins ties.
   */
  TenantAvailability merge(final TenantAvailability other) {
    return version >= other.version ? this : other;
  }

  /** Why a physical tenant is or is not running. */
  public enum State {
    /** Configured on the brokers and running normally. */
    ENABLED,
    /**
     * Removed from every broker's local static configuration, but still retaining its partition
     * assignment and data so it can resume where it left off if the tenant is reconfigured.
     * Disabling is a safety barrier against losing a tenant's data by accident, not a statement
     * that the data is expendable — which is why a disabled tenant still stops a broker holding its
     * partitions from leaving the cluster.
     */
    DISABLED,
    /**
     * Explicitly discarded by an operator. Like {@link #DISABLED} the tenant runs nowhere, so
     * {@link PartitionGroupConfiguration#isDisabled()} excludes it just the same; unlike {@link
     * #DISABLED}, a broker holding only removed tenants' partitions may leave the cluster.
     *
     * <p><strong>Terminal</strong>, and that is load-bearing: data left on disk is only safe
     * because this id can never be re-enabled ({@link TenantAvailability#enable()} is a no-op) or
     * re-provisioned ({@code PhysicalTenantProvisioningInitializer} skips ids already present in
     * {@code CurrentClusterConfiguration#partitionGroups()}).
     *
     * <p>This is also why {@code PhysicalTenantAvailabilityInitializer} — which re-derives
     * availability from the coordinator's own static configuration — cannot undo a removal even if
     * coordinatorship moves to a broker that still lists the tenant.
     */
    REMOVED
  }
}
