/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.relocation;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.RelocationState;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.DirectBuffer;

public class ScaleRelocateNextCorrelationKeyProcessor implements TypedRecordProcessor<ScaleRecord> {
  private final KeyGenerator keyGenerator;
  private final MessageState messageState;
  private final MessageSubscriptionState messageSubscriptionState;
  private final RelocationState relocationState;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public ScaleRelocateNextCorrelationKeyProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final MessageState messageState,
      final MessageSubscriptionState messageSubscriptionState,
      final RelocationState relocationState,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    commandWriter = writers.command();
    this.messageState = messageState;
    this.messageSubscriptionState = messageSubscriptionState;
    this.relocationState = relocationState;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> record) {
    final var nextCorrelationKey =
        findNextCorrelationKeyForRelocation(relocationState.getRoutingInfo());
    if (nextCorrelationKey == null) {
      // TODO: also notify other partitions
      // TODO: Define event applier, what info do we need from the key/value?
      commandWriter.appendFollowUpCommand(
          -1, ScaleIntent.RELOCATION_ON_PARTITION_COMPLETE, new ScaleRecord());
      final var distributionKey = keyGenerator.nextKey();

      commandDistributionBehavior.distributeCommand(
          distributionKey,
          ValueType.SCALE,
          ScaleIntent.RELOCATION_ON_PARTITION_COMPLETE,
          new ScaleRecord());
      return;
    }

    final var scaleRecord = new ScaleRecord();
    scaleRecord.setCorrelationKey(nextCorrelationKey);

    stateWriter.appendFollowUpEvent(
        record.getKey(), ScaleIntent.RELOCATE_CORRELATION_KEY_STARTED, scaleRecord);
    commandWriter.appendFollowUpCommand(
        record.getKey(), ScaleIntent.RELOCATE_MESSAGE_SUBSCRIPTION_START, scaleRecord);
  }

  private DirectBuffer findNextCorrelationKeyForRelocation(
      final RelocationState.RoutingInfo routingInfo) {
    final AtomicReference<DirectBuffer> found = new AtomicReference<>();
    messageSubscriptionState.visitSubscriptions(
        subscription -> {
          final var correlationKey = subscription.getRecord().getCorrelationKeyBuffer();
          final var oldPartition = routingInfo.oldPartitionForCorrelationKey(correlationKey);
          final var newPartition = routingInfo.newPartitionForCorrelationKey(correlationKey);
          if (oldPartition == newPartition) {
            return true;
          }

          found.set(correlationKey);
          return false;
        });
    if (found.get() != null) {
      return found.get();
    }

    messageState.visitMessages(
        message -> {
          final var correlationKey = message.getMessage().getCorrelationKeyBuffer();
          final var oldPartition = routingInfo.oldPartitionForCorrelationKey(correlationKey);
          final var newPartition = routingInfo.newPartitionForCorrelationKey(correlationKey);
          if (oldPartition == newPartition) {
            return true;
          }

          found.set(correlationKey);
          return false;
        });
    return found.get();
  }
}
