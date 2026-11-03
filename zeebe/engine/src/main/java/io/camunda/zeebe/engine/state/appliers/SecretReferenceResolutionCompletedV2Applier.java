/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.ProbeState;
import io.camunda.zeebe.engine.state.mutable.MutableProbeState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import java.util.ArrayList;
import java.util.List;
import org.agrona.collections.MutableBoolean;

/**
 * Makes the probes waiting on the resolved reference activatable again, unless they still wait for
 * another one; version 1 knows nothing about probes. Waiting jobs are reactivated by their own
 * batch chain instead.
 */
public final class SecretReferenceResolutionCompletedV2Applier
    implements TypedEventApplier<SecretReferenceIntent, SecretReferenceRecord> {

  private final MutableSecretReferenceState secretReferenceState;
  private final MutableProbeState probeState;

  public SecretReferenceResolutionCompletedV2Applier(
      final MutableSecretReferenceState secretReferenceState, final MutableProbeState probeState) {
    this.secretReferenceState = secretReferenceState;
    this.probeState = probeState;
  }

  @Override
  public void applyState(final long key, final SecretReferenceRecord value) {
    secretReferenceState.removePendingSecretReference(
        value.getStoreId(), value.getSecretReference());

    final List<Long> waitingProbes = new ArrayList<>();
    probeState.visitProbesBySecretReference(
        value.getStoreId(), value.getSecretReference(), waitingProbes::add);
    for (final long probeKey : waitingProbes) {
      if (probeState.getState(probeKey) == ProbeState.State.WAITING_FOR_SECRET_RESOLUTION
          && !waitsForAnotherReference(probeKey)) {
        probeState.makeActivatable(probeKey);
      }
    }
  }

  private boolean waitsForAnotherReference(final long probeKey) {
    final var pending = new MutableBoolean(false);
    probeState.visitSecretReferencesByProbe(
        probeKey,
        (storeId, reference) -> {
          pending.set(secretReferenceState.isPending(storeId, reference));
          return !pending.get();
        });
    return pending.get();
  }
}
