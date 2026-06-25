/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import io.camunda.eventbridge.clustermetadata.record.BrokerRecord;
import io.camunda.eventbridge.clustermetadata.state.broker.BrokerMetadata.BrokerStatus;
import io.camunda.eventbridge.clustermetadata.state.immutable.BrokerState;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the internal {@code FENCE_BROKER} command (from the {@code BrokerEvictionTask}): fences a
 * broker whose liveness session lapsed. It is self-guarded — a fence is a no-op unless the broker
 * still exists, is {@code ACTIVE}, and the command's epoch matches the registered one, so a broker
 * that re-registered (epoch bumped) since the eviction tick is not wrongly fenced. Holds no client
 * response; the new state is observed on the next read.
 */
public final class FenceBrokerProcessor implements TypedRecordProcessor<BrokerRecord> {

  private final Writers writers;
  private final BrokerState brokerState;

  public FenceBrokerProcessor(final Writers writers, final BrokerState brokerState) {
    this.writers = writers;
    this.brokerState = brokerState;
  }

  @Override
  public void processRecord(final TypedRecord<BrokerRecord> command) {
    final var cmd = command.getValue();
    final var existing = brokerState.get(cmd.getBrokerId());
    if (existing == null
        || existing.status() != BrokerStatus.ACTIVE
        || existing.brokerEpoch() != cmd.getBrokerEpoch()) {
      return;
    }
    final var event =
        new BrokerRecord()
            .setBrokerId(existing.brokerId())
            .setBrokerEpoch(existing.brokerEpoch())
            .setStatus(BrokerStatus.FENCED)
            .setIncarnation(existing.incarnation());
    writers.state().appendFollowUpEvent(command.getKey(), MetadataIntent.BROKER_FENCED, event);
  }
}
