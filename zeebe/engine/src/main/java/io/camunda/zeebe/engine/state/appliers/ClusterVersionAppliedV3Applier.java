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
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v3 applier: activates the (line, ordinal) and logs with a v3 marker. Behaviorally compatible with
 * v1/v2 for the state mutation; the observable difference is the recordVersion stamped on the
 * APPLIED event and the v3-tagged log output. Registered with {@code Requirement(810, 3)} so it is
 * only selected once the cluster has activated that ordinal.
 */
public final class ClusterVersionAppliedV3Applier
    implements TypedEventApplier<ClusterVersionIntent, ClusterVersionRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterVersionAppliedV3Applier.class);

  private final MutableClusterVersionState state;

  public ClusterVersionAppliedV3Applier(final MutableClusterVersionState state) {
    this.state = state;
  }

  @Override
  public void applyState(final long key, final ClusterVersionRecord value) {
    state.activate(value.getLine(), value.getOrdinal());
    LOG.trace(
        "ECV v3 applied: ({}, {}) gatedField={}",
        value.getLine(),
        value.getOrdinal(),
        value.getGatedField());
  }

  @Override
  public Capability gatedBy() {
    return Capability.DEMO_GATED_BRANCH;
  }
}
