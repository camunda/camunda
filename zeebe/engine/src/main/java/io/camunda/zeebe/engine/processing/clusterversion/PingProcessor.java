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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Processes a {@code PING} command by emitting a {@code PINGED} event.
 *
 * <p>This processor intentionally does not re-check the gate — admission is the gateway's
 * responsibility. By the time a PING reaches the log, {@link ClusterVersionGate} has already
 * approved it. If a stale or buggy gateway lets a forbidden PING through, the engine would still
 * apply it; defense in depth (a redundant processor-side check) is a refinement out of PoC scope.
 */
public final class PingProcessor implements TypedRecordProcessor<ClusterVersionRecord> {

  /** Marker prepended to the gated field when {@link Feature#DEMO_GATED_BRANCH} is on. */
  static final String DEMO_BRANCH_MARKER = "[v3-branch] ";

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final ClusterVersionFeatures features;

  public PingProcessor(
      final ProcessingState state, final Writers writers, final KeyGenerator keyGenerator) {
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    responseWriter = writers.response();
    features = new ClusterVersionFeatures(state.getClusterVersionState());
  }

  @Override
  public void processRecord(final TypedRecord<ClusterVersionRecord> command) {
    final var event = command.getValue();
    // Processor-side gate: the new behavior runs only when the feature is active. The feature is
    // declared in ClusterVersionCatalog (DEMO_GATED_BRANCH at ordinal 810/3); no (line, ordinal)
    // literal appears here.
    if (features.isActive(Capability.DEMO_GATED_BRANCH)) {
      event.setGatedField(DEMO_BRANCH_MARKER + event.getGatedField());
    }
    final long eventKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(eventKey, ClusterVersionIntent.PINGED, event);
    responseWriter.writeEventOnCommand(eventKey, ClusterVersionIntent.PINGED, event, command);
  }
}
