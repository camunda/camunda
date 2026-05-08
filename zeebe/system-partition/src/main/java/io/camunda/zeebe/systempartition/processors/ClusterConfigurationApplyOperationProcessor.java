/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition.processors;

import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableClusterConfigurationState;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Processor for {@link ClusterConfigurationIntent#APPLY_OPERATION} commands.
 *
 * <p>The command is emitted by a worker after it has successfully applied an operation locally
 * (e.g. joined a partition, enabled an exporter). This processor performs a CAS check and then
 * advances the cluster configuration by removing the head pending operation and bumping the
 * version, emitting a {@link ClusterConfigurationIntent#OPERATION_APPLIED} event. On CAS miss it
 * emits {@link ClusterConfigurationIntent#REJECT} so the worker can retry.
 *
 * <p>The processor takes a {@link ConfigurationChangeAppliers} reference for parity with the
 * non-stream-processor path; in the current Phase 2 scaffolding it does not invoke the applier
 * because the applier API is async and intended to be used by the worker before the APPLY_OPERATION
 * command is submitted. Phase 3+ will reuse the applier's {@code init/applyOperation} transformer
 * chain to compute the post-state on the leader.
 */
public final class ClusterConfigurationApplyOperationProcessor
    implements TypedRecordProcessor<ClusterConfigurationRecord> {

  private final MutableClusterConfigurationState state;
  private final Writers writers;
  private final KeyGenerator keys;
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  @SuppressWarnings("unused") // wired for Phase 3+
  private final ConfigurationChangeAppliers appliers;

  public ClusterConfigurationApplyOperationProcessor(
      final MutableClusterConfigurationState state,
      final Writers writers,
      final KeyGenerator keys,
      final ConfigurationChangeAppliers appliers) {
    this.state = state;
    this.writers = writers;
    this.keys = keys;
    this.appliers = appliers;
  }

  @Override
  public void processRecord(final TypedRecord<ClusterConfigurationRecord> command) {
    final ClusterConfigurationRecord cmd = command.getValue();
    final ClusterConfiguration current = state.get();

    if (cmd.getExpectedPreviousVersion() != current.version()) {
      final ClusterConfigurationRecord rejected =
          new ClusterConfigurationRecord()
              .setRequestId(cmd.getRequestId())
              .setRejectionReason(
                  "CAS miss: expected="
                      + cmd.getExpectedPreviousVersion()
                      + " actual="
                      + current.version());
      writers
          .state()
          .appendFollowUpEvent(keys.nextKey(), ClusterConfigurationIntent.REJECT, rejected);
      return;
    }

    // The command carries the post-state as a proto-encoded ClusterConfiguration computed by the
    // worker (which already invoked the operation applier locally before submitting). The
    // processor records that state authoritatively; the StateApplier persists it on every replica.
    final byte[] payload = cmd.getConfiguration();
    final ClusterConfiguration next =
        payload != null && payload.length > 0
            ? serializer.decodeClusterTopology(payload, 0, payload.length)
            : current;

    final ClusterConfigurationRecord applied =
        new ClusterConfigurationRecord()
            .setRequestId(cmd.getRequestId())
            .setExpectedPreviousVersion(current.version())
            .setConfiguration(serializer.encode(next))
            .setAppliedOperation(cmd.getAppliedOperation());

    writers
        .state()
        .appendFollowUpEvent(keys.nextKey(), ClusterConfigurationIntent.OPERATION_APPLIED, applied);
  }
}
