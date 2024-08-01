/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.relocation;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedEventWriter;
import io.camunda.zeebe.engine.state.immutable.RelocationState;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;
import org.agrona.DirectBuffer;

final class MessageRelocationHelper {
  private MessageRelocationHelper() {}

  public static void completeRelocationOfCorrelationKey(
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior commandDistributionBehavior,
      final RelocationState relocationState,
      final DirectBuffer correlationKey,
      final TypedEventWriter stateWriter,
      final TypedCommandWriter commandWriter,
      final long recordKey) {
    // Check relocation queue
    final var queuedMessages = relocationState.getQueuedMessages();
    for (final var message : queuedMessages) {
      final var distributionKey = keyGenerator.nextKey();
      final var relocationRecord = new ScaleRecord();
      relocationRecord.setMessageRecord(message);
      commandDistributionBehavior.distributeCommand(
          distributionKey,
          ValueType.MESSAGE,
          MessageIntent.PUBLISH,
          message,
          List.of(
              relocationState
                  .getRoutingInfo()
                  .newPartitionForCorrelationKey(message.getCorrelationKeyBuffer())));
    }
    final var queuedMessageSubscriptions = relocationState.getQueuedMessageSubscriptions();
    for (final var messageSubscription : queuedMessageSubscriptions) {
      final var distributionKey = keyGenerator.nextKey();
      final var relocationRecord = new ScaleRecord();
      relocationRecord.setMessageSubscriptionRecord(messageSubscription);
      commandDistributionBehavior.distributeCommand(
          distributionKey,
          ValueType.MESSAGE_SUBSCRIPTION,
          MessageSubscriptionIntent.CREATE,
          messageSubscription,
          List.of(
              relocationState
                  .getRoutingInfo()
                  .newPartitionForCorrelationKey(messageSubscription.getCorrelationKeyBuffer())));
    }

    final var newScaleRecord = new ScaleRecord();
    newScaleRecord.setCorrelationKey(correlationKey);
    stateWriter.appendFollowUpEvent(
        recordKey, ScaleIntent.RELOCATE_CORRELATION_KEY_COMPLETED, newScaleRecord);

    commandWriter.appendFollowUpCommand(
        -1, ScaleIntent.RELOCATE_NEXT_CORRELATION_KEY, newScaleRecord);
  }
}
