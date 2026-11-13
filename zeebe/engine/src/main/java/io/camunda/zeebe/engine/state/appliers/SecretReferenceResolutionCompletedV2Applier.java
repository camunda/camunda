/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProbeState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import java.util.ArrayList;
import java.util.List;

/**
 * As version 1, and makes activatable again the probes parked on the secret reference that wait for
 * no other pending reference. Jobs are reactivated by the {@code BATCH_REACTIVATE_JOBS} chain
 * instead; probes are few per reference and need no batching.
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

    // collected first: making a probe activatable removes its entries from the visited index
    final List<Long> parkedProbes = new ArrayList<>();
    probeState.forEachProbeParkedOn(
        value.getStoreId(), value.getSecretReference(), parkedProbes::add);
    for (final long probeKey : parkedProbes) {
      if (!waitsForAPendingReference(probeKey)) {
        probeState.makeActivatable(probeKey);
      }
    }
  }

  private boolean waitsForAPendingReference(final long probeKey) {
    final boolean[] pending = {false};
    probeState.forEachSecretReferenceParkedOn(
        probeKey,
        (storeId, secretReference) -> {
          pending[0] = secretReferenceState.isPending(storeId, secretReference);
          return !pending[0];
        });
    return pending[0];
  }
}
