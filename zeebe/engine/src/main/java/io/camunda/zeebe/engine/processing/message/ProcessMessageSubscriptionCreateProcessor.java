/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;

@ExcludeAuthorizationCheck
public final class ProcessMessageSubscriptionCreateProcessor
    implements TypedRecordProcessor<ProcessMessageSubscriptionRecord> {

  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to create process message subscription with element key '%d' and message name '%s', "
          + "but no such subscription was found";
  private static final String NOT_OPENING_MSG =
      "Expected to create process message subscription with element key '%d' and message name '%s', "
          + "but it is already %s";

  private final ProcessMessageSubscriptionState subscriptionState;
  private final TransientPendingSubscriptionState transientProcessMessageSubscriptionState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final SideEffectWriter sideEffectWriter;

  public ProcessMessageSubscriptionCreateProcessor(
      final ProcessMessageSubscriptionState subscriptionState,
      final Writers writers,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState) {
    this.subscriptionState = subscriptionState;
    this.transientProcessMessageSubscriptionState = transientProcessMessageSubscriptionState;
    stateWriter = writers.state();
    sideEffectWriter = writers.sideEffect();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<ProcessMessageSubscriptionRecord> command) {

    final ProcessMessageSubscriptionRecord subscriptionRecord = command.getValue();
    final long elementInstanceKey = subscriptionRecord.getElementInstanceKey();
    final String tenantId = subscriptionRecord.getTenantId();
    final String messageName = subscriptionRecord.getMessageName();
    final ProcessMessageSubscription subscription =
        subscriptionState.getSubscription(
            elementInstanceKey, subscriptionRecord.getMessageNameBuffer(), tenantId);

    if (subscription != null && subscription.isOpening()) {
      stateWriter.appendFollowUpEvent(
          subscription.getKey(),
          ProcessMessageSubscriptionIntent.CREATED,
          subscription.getRecord());

      // update transient state in a side-effect to ensure that these changes only take effect after
      // the command has been successfully processed
      sideEffectWriter.appendSideEffect(
          () -> {
            transientProcessMessageSubscriptionState.remove(
                new PendingSubscription(elementInstanceKey, messageName, tenantId));
            return true;
          });
    } else {
      rejectCommand(command, subscription);
    }
  }

  private void rejectCommand(
      final TypedRecord<ProcessMessageSubscriptionRecord> command,
      final ProcessMessageSubscription subscription) {
    final var record = command.getValue();
    final var elementInstanceKey = record.getElementInstanceKey();
    final String messageName = BufferUtil.bufferAsString(record.getMessageNameBuffer());

    if (subscription == null) {
      final var reason =
          String.format(NO_SUBSCRIPTION_FOUND_MESSAGE, elementInstanceKey, messageName);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);

    } else {
      final String state = subscription.isClosing() ? "closing" : "opened";
      final var reason = String.format(NOT_OPENING_MSG, elementInstanceKey, messageName, state);
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
    }
  }
}
