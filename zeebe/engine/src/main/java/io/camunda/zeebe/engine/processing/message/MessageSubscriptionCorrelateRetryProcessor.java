/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;

/**
 * Handles {@link
 * io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent#CORRELATE_RETRY}, sent by the
 * process instance partition for each {@code OPENED} process message subscription of an instance
 * that just finished resuming (see {@code ProcessInstanceResumeProcessor}).
 *
 * <p>Simply re-runs {@link MessageCorrelator#correlateNextMessage}, the same deadline/dedup-aware
 * check used everywhere else a subscription looks for a message to correlate - this is what makes
 * the retry TTL-correct: a message that expired while the instance was suspended is skipped, one
 * that is still valid correlates.
 */
@ExcludeAuthorizationCheck
public final class MessageSubscriptionCorrelateRetryProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord>,
        SuspensionAware<MessageSubscriptionRecord> {

  private static final String NOTHING_TO_RETRY_MESSAGE =
      "Expected to retry correlation for subscription with element key '%d' and message name '%s', "
          + "but no correlatable message was found";

  private final MessageSubscriptionState subscriptionState;
  private final MessageCorrelator messageCorrelator;
  private final TypedRejectionWriter rejectionWriter;

  public MessageSubscriptionCorrelateRetryProcessor(
      final int partitionId,
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender,
      final Writers writers,
      final InstantSource clock) {
    this.subscriptionState = subscriptionState;
    rejectionWriter = writers.rejection();
    messageCorrelator =
        new MessageCorrelator(
            partitionId, messageState, commandSender, writers.state(), writers.sideEffect(), clock);
  }

  @Override
  public SuspensionBehavior suspensionBehavior(
      final TypedRecord<MessageSubscriptionRecord> record) {
    // Runs on the message partition; SuspensionState is partition-local so cross-partition PIs
    // are never visible here. This command is the resume re-poll and must always execute.
    return SuspensionBehavior.PROCESS;
  }

  @Override
  public void processRecord(final TypedRecord<MessageSubscriptionRecord> command) {
    final var value = command.getValue();
    final var subscription =
        subscriptionState.get(value.getElementInstanceKey(), value.getMessageNameBuffer());

    if (subscription == null || subscription.isCorrelating()) {
      // the subscription is gone, or already busy correlating a message that arrived after resume
      // - nothing to retry
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          NOTHING_TO_RETRY_MESSAGE.formatted(
              value.getElementInstanceKey(),
              BufferUtil.bufferAsString(value.getMessageNameBuffer())));
      return;
    }

    final boolean correlated =
        messageCorrelator.correlateNextMessage(subscription.getKey(), subscription.getRecord());
    if (!correlated) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          NOTHING_TO_RETRY_MESSAGE.formatted(
              value.getElementInstanceKey(),
              BufferUtil.bufferAsString(value.getMessageNameBuffer())));
    }
  }
}
