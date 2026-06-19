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
 * v2 applier: activates the (line, ordinal) and logs the {@code gatedField} carried on the record.
 * Demonstrates a per-record behavior difference selected by record version — the contract the deck
 * names "versioned event appliers".
 */
public final class ClusterVersionAppliedV2Applier
    implements TypedEventApplier<ClusterVersionIntent, ClusterVersionRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterVersionAppliedV2Applier.class);

  private final MutableClusterVersionState state;

  public ClusterVersionAppliedV2Applier(final MutableClusterVersionState state) {
    this.state = state;
  }

  @Override
  public void applyState(final long key, final ClusterVersionRecord value) {
    state.activate(value.getLine(), value.getOrdinal());
    LOG.trace(
        "ECV v2 applied: ({}, {}) gatedField={}",
        value.getLine(),
        value.getOrdinal(),
        value.getGatedField());
  }

  @Override
  public Capability gatedBy() {
    return Capability.APPLIED_V2;
  }
}
