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
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Processes an {@code ECHO} command by emitting an {@code ECHOED} event.
 *
 * <p>Like {@link PingProcessor}, this is a demo "new feature" — its only purpose is to demonstrate
 * that admission gating composes: ECHO is gated at a lower ordinal than PING, so as the cluster's
 * ECV grows, the set of admissible commands grows monotonically with it.
 */
public final class EchoProcessor implements TypedRecordProcessor<ClusterVersionRecord> {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;

  public EchoProcessor(final Writers writers, final KeyGenerator keyGenerator) {
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<ClusterVersionRecord> command) {
    final long eventKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(eventKey, ClusterVersionIntent.ECHOED, command.getValue());
    responseWriter.writeEventOnCommand(
        eventKey, ClusterVersionIntent.ECHOED, command.getValue(), command);
  }
}
