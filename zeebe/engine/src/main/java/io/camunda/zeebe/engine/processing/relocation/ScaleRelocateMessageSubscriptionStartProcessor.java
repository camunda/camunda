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
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.RelocationState;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;
import org.agrona.collections.MutableBoolean;

public class ScaleRelocateMessageSubscriptionStartProcessor
    implements TypedRecordProcessor<ScaleRecord> {
  private final KeyGenerator keyGenerator;
  private final RelocationState relocationState;
  private final MessageSubscriptionState messageSubscriptionState;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public ScaleRelocateMessageSubscriptionStartProcessor(
      final KeyGenerator keyGenerator,
      final RelocationState relocationState,
      final MessageSubscriptionState messageSubscriptionState,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.keyGenerator = keyGenerator;
    this.relocationState = relocationState;
    this.messageSubscriptionState = messageSubscriptionState;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> record) {
    // Iterate over all subscriptions for the given correlation key
    // Distribute a command to for each message subscription
    final var correlationKey = record.getValue().getCorrelationKey();
    final var newPartition =
        relocationState.getRoutingInfo().newPartitionForCorrelationKey(correlationKey);

    final var foundAny = new MutableBoolean();
    messageSubscriptionState.visitSubscriptions(
        subscription -> {
          // TODO: visit directly by correlation key to make it efficient
          if (!subscription.getRecord().getCorrelationKeyBuffer().equals(correlationKey)) {
            return true;
          }

          foundAny.set(true);
          final var distributionKey = keyGenerator.nextKey();
          final var messageSubscription = subscription.getRecord();
          // TODO: What if `subscription.isCorrelating() is true?

          final var scaleRecord = new ScaleRecord();
          scaleRecord.setMessageSubscriptionRecord(messageSubscription);

          commandDistributionBehavior.distributeCommand(
              distributionKey,
              ValueType.SCALE,
              ScaleIntent.RELOCATE_MESSAGE_SUBSCRIPTION_APPLY,
              scaleRecord,
              List.of(newPartition),
              ValueType.SCALE,
              ScaleIntent.RELOCATE_MESSAGE_SUBSCRIPTION_COMPLETE,
              scaleRecord);

          return true;
        });

    if (!foundAny.get()) {
      // TODO: Continue with relocating messages
    }
  }
}
