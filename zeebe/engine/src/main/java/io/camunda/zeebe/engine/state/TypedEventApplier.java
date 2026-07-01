/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;

/** Applies state changes for a specific event to the {@link MutableProcessingState}. */
public interface TypedEventApplier<I extends Intent, V extends RecordValue> {

  void applyState(final long key, final V value);

  /**
   * The {@link Capability} that gates this applier version. Returning {@link Capability#BASELINE}
   * means "record-version-only, not ECV-gated" — the applier is always selectable for its
   * registered version. Returning any other capability means the applier becomes selectable only
   * once the cluster has activated that ordinal, and {@code EventAppliers.register} will
   * cross-check that the catalog actually lists this {@code (intent, version)} under it.
   *
   * <p>The default is {@link Capability#BASELINE} so existing single-version (v=1) and legacy
   * multi-version appliers compile without change. Any <em>new</em> applier version that should be
   * ECV-gated must override this to return the gating capability — and the boot-time check in
   * {@code EventAppliers} refuses to register a {@code version > 1} applier whose default BASELINE
   * classification is not vetted by the catalog or the transitional allowlist.
   */
  default Capability gatedBy() {
    return Capability.BASELINE;
  }
}
