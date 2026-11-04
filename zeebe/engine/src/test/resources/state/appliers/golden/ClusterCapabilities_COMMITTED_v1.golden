/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableClusterCapabilitiesState;
import io.camunda.zeebe.protocol.impl.record.value.capabilities.ClusterCapabilitiesRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterCapabilitiesIntent;
import io.camunda.zeebe.util.ecv.CapabilitySet;

/**
 * Adds the committed capabilities to the partition (union, never smaller).
 *
 * <p>Engine guard: if the event has a capability that this broker does not support, applying it
 * fails. On replay, or after the install of a snapshot, this stops the partition on this broker, so
 * a broker that the refusal check did not stop cannot process with capabilities it does not know.
 */
final class ClusterCapabilitiesCommittedApplier
    implements TypedEventApplier<ClusterCapabilitiesIntent, ClusterCapabilitiesRecord> {

  private final MutableClusterCapabilitiesState state;

  ClusterCapabilitiesCommittedApplier(final MutableClusterCapabilitiesState state) {
    this.state = state;
  }

  @Override
  public void applyState(final long key, final ClusterCapabilitiesRecord value) {
    final var committed = CapabilitySet.decode(value.getEnabledCapabilities());
    final var unsupported = committed.minus(state.getSupportedCapabilities());
    if (!unsupported.isEmpty()) {
      throw new UnsupportedCapabilitiesException(committed, unsupported);
    }
    state.addEnabledCapabilities(committed);
  }

  /** Thrown by the engine guard. */
  public static final class UnsupportedCapabilitiesException extends IllegalStateException {
    UnsupportedCapabilitiesException(
        final CapabilitySet committed, final CapabilitySet unsupported) {
      super(
          ("Expected to apply the enabled capabilities %s, but this broker does not support %s."
                  + " Start the broker with a version that supports all enabled capabilities.")
              .formatted(committed, unsupported));
    }
  }
}
