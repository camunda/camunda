/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MessageSubscriptionCorrelatedApplier
    implements TypedEventApplier<MessageSubscriptionIntent, MessageSubscriptionRecord> {

  private static final Logger LOG =
      LoggerFactory.getLogger(MessageSubscriptionCorrelatedApplier.class);

  private final MutableMessageSubscriptionState messageSubscriptionState;

  public MessageSubscriptionCorrelatedApplier(
      final MutableMessageSubscriptionState messageSubscriptionState) {
    this.messageSubscriptionState = messageSubscriptionState;
  }

  @Override
  public void applyState(final long key, final MessageSubscriptionRecord value) {
    // TODO (saig0): the record doesn't contain the sent time but it's required for cleaning (#6364)
    // - workaround: load the subscription from the state instead of using the record directly
    final var subscription =
        messageSubscriptionState.get(value.getElementInstanceKey(), value.getMessageNameBuffer());

    if (subscription.getRecord().getMessageKey() != value.getMessageKey()) {
      // This concerns the acknowledgement of a retried correlate process message subscription
      // command. The message subscription was already marked as correlated for this message, and
      // another message has started correlating. There's no need to update the state.
      LOG.warn(
          """
          Expected to acknowledge correlating message with key '{}' to subscription with key '{}' \
          but the subscription is already correlating to another message with key '{}'""",
          value.getMessageKey(),
          key,
          subscription.getRecord().getMessageKey());
      return;

    } else if (!subscription.isCorrelating()) {
      // This concerns the acknowledgement of a retried correlate process message subscription
      // command. The message subscription was already marked as correlated. No need to update the
      // state.
      LOG.debug(
          """
          Expected to acknowledge correlating message with key '{}' to subscription with key '{}' \
          but the subscription is already correlating'""",
          value.getMessageKey(),
          key);
      return;
    }

    LOG.info(
        "Acknowledged correlating message with key '{}' to subscription with key '{}'",
        value.getMessageKey(),
        key);

    if (value.isInterrupting()) {
      messageSubscriptionState.remove(subscription);
    } else {
      messageSubscriptionState.updateToCorrelatedState(subscription);
    }
  }
}
