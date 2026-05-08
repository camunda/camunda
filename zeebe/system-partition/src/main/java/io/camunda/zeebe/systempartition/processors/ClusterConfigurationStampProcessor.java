/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition.processors;

import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.systempartition.state.ClusterConfigurationState;

/**
 * Processor for {@link ClusterConfigurationIntent#STAMP_CHANGE_PLAN} commands.
 *
 * <p>Validates that no other change plan is currently active and that the caller's expected
 * previous version matches the persisted version (CAS check). On success, writes a {@link
 * ClusterConfigurationIntent#CHANGE_PLAN_STAMPED} event with the proposed configuration and emits a
 * follow-up {@code modification_starter} BPMN instance creation command.
 *
 * <p>On CAS miss or active plan, writes a {@link ClusterConfigurationIntent#REJECT} event.
 */
public final class ClusterConfigurationStampProcessor
    implements TypedRecordProcessor<ClusterConfigurationRecord> {

  private static final String MODIFICATION_STARTER_PROCESS_ID = "modification_starter";

  private final ClusterConfigurationState state;
  private final Writers writers;
  private final KeyGenerator keys;
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  public ClusterConfigurationStampProcessor(
      final ClusterConfigurationState state, final Writers writers, final KeyGenerator keys) {
    this.state = state;
    this.writers = writers;
    this.keys = keys;
  }

  @Override
  public void processRecord(final TypedRecord<ClusterConfigurationRecord> command) {
    final ClusterConfigurationRecord cmd = command.getValue();
    final ClusterConfiguration current = state.get();

    if (current.hasPendingChanges()) {
      writers
          .rejection()
          .appendRejection(
              command,
              RejectionType.INVALID_STATE,
              "Cannot stamp change plan: another plan is active");
      return;
    }

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

    // Decode the proposed configuration carrying the new pending operations and emit a
    // CHANGE_PLAN_STAMPED event with the (next) version.
    final byte[] payload = cmd.getConfiguration();
    final ClusterConfiguration proposed =
        serializer.decodeClusterTopology(payload, 0, payload.length);

    final ClusterConfigurationRecord stamped =
        new ClusterConfigurationRecord()
            .setRequestId(cmd.getRequestId())
            .setExpectedPreviousVersion(current.version())
            .setConfiguration(serializer.encode(proposed));

    writers
        .state()
        .appendFollowUpEvent(
            keys.nextKey(), ClusterConfigurationIntent.CHANGE_PLAN_STAMPED, stamped);

    // Auto-start the modification BPMN by emitting a ProcessInstanceCreation command. The engine
    // (running on the same stream processor) will create the instance on the next iteration.
    BpmnInstanceStarter.requestStart(writers, MODIFICATION_STARTER_PROCESS_ID, proposed);
  }
}
