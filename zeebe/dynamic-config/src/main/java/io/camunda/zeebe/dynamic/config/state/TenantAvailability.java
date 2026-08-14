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
 * Whether a physical tenant (a {@link PartitionGroupConfiguration}) is currently disabled — i.e.
 * removed from every broker's local static configuration, but still retaining its partition
 * assignment and data so it can resume where it left off if the tenant is reconfigured.
 *
 * <p>{@code version} is this sub-record's own version, deliberately independent of the enclosing
 * {@link PartitionGroupConfiguration#version()}: toggling availability must not bump the group
 * version, since availability changes are orthogonal to partition-assignment/plan-boundary changes.
 * Merge is highest-own-version-wins ({@link #merge(TenantAvailability)}), the same pattern as
 * {@link BrokerPartitionState}, and must be applied independently of whichever branch of {@link
 * PartitionGroupConfiguration#merge(PartitionGroupConfiguration)} is taken — otherwise an unrelated
 * bump of the group's top-level version could silently overwrite a more recently toggled value.
 *
 * @param version this sub-record's own version, bumped only by {@link #disable()}/{@link #enable()}
 * @param disabled whether the tenant is currently disabled
 */
@NullMarked
public record TenantAvailability(long version, boolean disabled) {

  public static final long INITIAL_VERSION = 0;

  public static TenantAvailability enabled() {
    return new TenantAvailability(INITIAL_VERSION, false);
  }

  /**
   * Returns a disabled instance with an incremented version, or {@code this} if already disabled.
   */
  public TenantAvailability disable() {
    return disabled ? this : new TenantAvailability(version + 1, true);
  }

  /**
   * Returns an enabled instance with an incremented version, or {@code this} if already enabled.
   */
  public TenantAvailability enable() {
    return disabled ? new TenantAvailability(version + 1, false) : this;
  }

  /**
   * Returns whichever of this and {@code other} has the higher own version; {@code this} wins ties.
   */
  TenantAvailability merge(final TenantAvailability other) {
    return version >= other.version ? this : other;
  }
}
