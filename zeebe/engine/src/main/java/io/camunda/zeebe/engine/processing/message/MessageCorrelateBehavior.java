/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import org.agrona.DirectBuffer;

public final class MessageCorrelateBehavior {

  private final MessageStartEventSubscriptionState startEventSubscriptionState;
  private final MessageState messageState;
  private final EventHandle eventHandle;

  public MessageCorrelateBehavior(
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final MessageState messageState,
      final EventHandle eventHandle) {
    this.startEventSubscriptionState = startEventSubscriptionState;
    this.messageState = messageState;
    this.eventHandle = eventHandle;
  }

  public Subscriptions correlateToMessageStartEvents(
      final String tenantId,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final DirectBuffer variables,
      final long messageKey) {
    final var correlatingSubscriptions = new Subscriptions();

    startEventSubscriptionState.visitSubscriptionsByMessageName(
        tenantId,
        messageName,
        subscription -> {
          final var subscriptionRecord = subscription.getRecord();
          final var bpmnProcessIdBuffer = subscriptionRecord.getBpmnProcessIdBuffer();

          // create only one instance of a process per correlation key
          // - allow multiple instance if correlation key is empty
          if (!correlatingSubscriptions.contains(bpmnProcessIdBuffer)
              && (correlationKey.capacity() == 0
                  || !messageState.existActiveProcessInstance(
                      tenantId, bpmnProcessIdBuffer, correlationKey))) {

            correlatingSubscriptions.add(subscriptionRecord);

            eventHandle.triggerMessageStartEvent(
                subscription.getKey(),
                subscriptionRecord,
                messageKey,
                messageName,
                correlationKey,
                variables);
          }
        });

    return correlatingSubscriptions;
  }
}
