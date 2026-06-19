/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clusterversion;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ClusterVersionState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Processes a {@code RAISE} command by appending an {@code APPLIED} event.
 *
 * <p>This is the activation seam from the deck: the active ECV is determined by which APPLIED event
 * was last replayed. The processor itself reads (but never mutates) state — all mutation goes
 * through the applier path.
 *
 * <p>The processor does <em>not</em> select an applier version. Each applier version declares its
 * ECV requirement at registration in {@code EventAppliers}, and the state writer (via {@link
 * io.camunda.zeebe.engine.state.EventApplier#selectVersionFor(io.camunda.zeebe.protocol.record.intent.Intent)
 * EventApplier.selectVersionFor}) automatically picks the highest version safe to emit under the
 * cluster's currently active ECV. Write sites stay oblivious to the gate; adding a new applier
 * version means adding a row at the registration site.
 */
public final class RaiseClusterVersionProcessor
    implements TypedRecordProcessor<ClusterVersionRecord> {

  private final ClusterVersionState clusterVersionState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final ClusterVersionUpdateListener updateListener;

  public RaiseClusterVersionProcessor(
      final ProcessingState state,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ClusterVersionUpdateListener updateListener) {
    clusterVersionState = state.getClusterVersionState();
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.updateListener = updateListener;
  }

  @Override
  public void processRecord(final TypedRecord<ClusterVersionRecord> command) {
    final var requested = command.getValue();
    final int targetLine = requested.getLine();
    final int targetOrdinal = requested.getOrdinal();
    final int activeLine = clusterVersionState.getActiveLine();
    final int activeOrdinal = clusterVersionState.getActiveOrdinal();

    // Reject only on a strictly-lower target — re-raising to the current value is a deliberate
    // no-op success. This matches MongoDB's setFCV idempotency: retries, automation, and "did
    // the previous raise complete?" probes can repeat the request without operator confusion.
    final boolean isStrictlyLower =
        activeLine > targetLine || (activeLine == targetLine && activeOrdinal > targetOrdinal);
    if (isStrictlyLower) {
      final var msg =
          "Expected to raise cluster version to (%d, %d), but current is already (%d, %d)"
              .formatted(targetLine, targetOrdinal, activeLine, activeOrdinal);
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, msg);
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, msg);
      return;
    }

    // Populate the optional gated-field unconditionally. Whether it is interpreted by the applier
    // depends on which applier version the state writer selects — a decision made centrally based
    // on the requirements declared in EventAppliers, not here.
    if (requested.getGatedField().isEmpty()) {
      requested.setGatedField("gated-default");
    }

    final long eventKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(eventKey, ClusterVersionIntent.APPLIED, requested);
    responseWriter.writeEventOnCommand(eventKey, ClusterVersionIntent.APPLIED, requested, command);

    // After the state writer applies the event, clusterVersionState reflects the new active ECV.
    // Notify the listener so the broker's admission layer can refresh its cached snapshot.
    updateListener.onClusterVersionUpdate(
        clusterVersionState.getActiveLine(), clusterVersionState.getActiveOrdinal());
  }
}
