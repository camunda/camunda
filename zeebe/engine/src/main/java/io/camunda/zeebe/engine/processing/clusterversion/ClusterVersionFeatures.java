/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clusterversion;

import io.camunda.zeebe.engine.state.immutable.ClusterVersionState;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;

/**
 * Processor-side gate. Lets a processor branch on whether a named {@link Capability} has been
 * activated yet, without mentioning an ordinal literal.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * if (features.isActive(Capability.DEMO_GATED_BRANCH)) {
 *   // new path
 * } else {
 *   // legacy path
 * }
 * }</pre>
 *
 * <p>Suppression takes precedence over the ECV gate: an operator can disable a capability via
 * SUPPRESS_FLAG even though the cluster has reached its ordinal — the rollback-lite kill switch.
 */
public final class ClusterVersionFeatures {

  private final ClusterVersionState state;

  public ClusterVersionFeatures(final ClusterVersionState state) {
    this.state = state;
  }

  /**
   * True iff the cluster's currently active ordinal satisfies the capability AND the capability has
   * not been explicitly suppressed by an operator.
   */
  public boolean isActive(final Capability capability) {
    if (state.isSuppressed(capability.name())) {
      return false;
    }
    return state.isAtLeast(capability.at());
  }
}
