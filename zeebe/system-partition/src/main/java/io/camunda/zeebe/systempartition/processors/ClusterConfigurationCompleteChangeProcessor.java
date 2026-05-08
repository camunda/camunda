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
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.systempartition.state.ClusterConfigurationState;

/**
 * Processor for {@link ClusterConfigurationIntent#COMPLETE_CHANGE} commands.
 *
 * <p>Emitted by the terminal service task of the modification BPMN once all pending operations have
 * been applied. The processor records the change as completed: the post-state, encoded into the
 * {@link ClusterConfigurationIntent#CHANGE_COMPLETED} event payload, has the active plan moved to
 * {@code lastChange} and the version bumped.
 *
 * <p>The actual transition (active → lastChange) is performed by {@link
 * ClusterConfiguration#advanceConfigurationChange} when the last operation is applied, so by the
 * time this processor runs, {@code current} should already represent the cleaned-up state. The
 * processor simply re-encodes that state into the event for downstream listeners.
 */
public final class ClusterConfigurationCompleteChangeProcessor
    implements TypedRecordProcessor<ClusterConfigurationRecord> {

  private final ClusterConfigurationState state;
  private final Writers writers;
  private final KeyGenerator keys;
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  public ClusterConfigurationCompleteChangeProcessor(
      final ClusterConfigurationState state, final Writers writers, final KeyGenerator keys) {
    this.state = state;
    this.writers = writers;
    this.keys = keys;
  }

  @Override
  public void processRecord(final TypedRecord<ClusterConfigurationRecord> command) {
    final ClusterConfiguration current = state.get();
    final ClusterConfigurationRecord event =
        new ClusterConfigurationRecord()
            .setRequestId(command.getValue().getRequestId())
            .setExpectedPreviousVersion(current.version())
            .setConfiguration(serializer.encode(current));
    writers
        .state()
        .appendFollowUpEvent(keys.nextKey(), ClusterConfigurationIntent.CHANGE_COMPLETED, event);
  }
}
