/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class ProcessMessageSubscriptionDeleteProcessor
    implements TypedRecordProcessor<ProcessMessageSubscriptionRecord> {

  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to delete process message subscription for element with key '%d' and message name '%s', "
          + "but no such subscription was found.";

  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final SideEffectWriter sideEffectWriter;
  private final ProcessMessageSubscriptionState subscriptionState;
  private final TransientPendingSubscriptionState transientState;

  public ProcessMessageSubscriptionDeleteProcessor(
      final ProcessMessageSubscriptionState subscriptionState,
      final Writers writers,
      final TransientPendingSubscriptionState transientState) {
    this.subscriptionState = subscriptionState;
    this.transientState = transientState;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    sideEffectWriter = writers.sideEffect();
  }

  @Override
  public void processRecord(final TypedRecord<ProcessMessageSubscriptionRecord> command) {

    final ProcessMessageSubscriptionRecord subscriptionRecord = command.getValue();
    final long elementInstanceKey = subscriptionRecord.getElementInstanceKey();
    final String messageName = subscriptionRecord.getMessageName();
    final String tenantId = subscriptionRecord.getTenantId();
    final var subscription =
        subscriptionState.getSubscription(
            elementInstanceKey, subscriptionRecord.getMessageNameBuffer(), tenantId);

    if (subscription == null) {
      rejectCommand(command);
      return;
    }

    stateWriter.appendFollowUpEvent(
        subscription.getKey(), ProcessMessageSubscriptionIntent.DELETED, subscription.getRecord());

    // update transient state in a side-effect to ensure that these changes only take effect after
    // the command has been successfully processed
    sideEffectWriter.appendSideEffect(
        () -> {
          transientState.remove(new PendingSubscription(elementInstanceKey, messageName, tenantId));
          return true;
        });
  }

  private void rejectCommand(final TypedRecord<ProcessMessageSubscriptionRecord> command) {
    final var reason =
        String.format(
            NO_SUBSCRIPTION_FOUND_MESSAGE,
            command.getValue().getElementInstanceKey(),
            BufferUtil.bufferAsString(command.getValue().getMessageNameBuffer()));

    rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
  }
}
