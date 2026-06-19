/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableClusterVersionState;
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;

/** Removes the record's {@code flagName} from the suppressed-flags set. */
public final class FlagUnsuppressedApplier
    implements TypedEventApplier<ClusterVersionIntent, ClusterVersionRecord> {

  private final MutableClusterVersionState state;

  public FlagUnsuppressedApplier(final MutableClusterVersionState state) {
    this.state = state;
  }

  @Override
  public void applyState(final long key, final ClusterVersionRecord value) {
    state.unsuppressFlag(value.getFlagName());
  }
}
