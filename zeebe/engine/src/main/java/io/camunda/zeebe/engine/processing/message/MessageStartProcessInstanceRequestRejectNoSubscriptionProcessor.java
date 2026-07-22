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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.state.immutable.MessageCorrelationState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@link MessageStartProcessInstanceRequestIntent#REJECT_NO_SUBSCRIPTION} command on
 * {@code P_K}, which is the rejection reply from {@code P_B} indicating that no matching start
 * event subscription exists on {@code P_B} (deployment-distribution race).
 *
 * <p>This processor writes the {@link
 * MessageStartProcessInstanceRequestIntent#NO_SUBSCRIPTION_REJECTED} follow-up event whose applier
 * does the bookkeeping cleanup (pending-ask state removal). The message stays buffered on {@code
 * P_K}, preserving the same semantics as when a local start-event subscription is missing.
 *
 * <p>When the delegating command was a synchronous {@code correlate} (rather than a {@code
 * publish}), the client is still awaiting a response; this processor additionally flushes the
 * deferred {@link io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent#NOT_CORRELATED}
 * event and a {@code NOT_FOUND} rejected response, and expires the fire-and-forget correlate
 * message so it is neither retried nor left buffered.
 */
@ExcludeAuthorizationCheck
public final class MessageStartProcessInstanceRequestRejectNoSubscriptionProcessor
    implements TypedRecordProcessor<MessageStartProcessInstanceRequestRecord> {

  private static final String SUBSCRIPTION_NOT_FOUND =
      "Expected to find subscription for message with name '%s' and correlation key '%s', but none was found.";

  private final StateWriter stateWriter;
  private final DeferredMessageStartCorrelationResponse deferredCorrelationResponse;

  public MessageStartProcessInstanceRequestRejectNoSubscriptionProcessor(
      final StateWriter stateWriter,
      final TypedResponseWriter responseWriter,
      final MessageCorrelationState messageCorrelationState,
      final MessageState messageState) {
    this.stateWriter = stateWriter;
    deferredCorrelationResponse =
        new DeferredMessageStartCorrelationResponse(
            stateWriter, responseWriter, messageCorrelationState, messageState);
  }

  @Override
  public void processRecord(final TypedRecord<MessageStartProcessInstanceRequestRecord> record) {
    final var reply = record.getValue();
    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageStartProcessInstanceRequestIntent.NO_SUBSCRIPTION_REJECTED, reply);

    deferredCorrelationResponse.writeNotCorrelatedResponse(
        reply,
        RejectionType.NOT_FOUND,
        SUBSCRIPTION_NOT_FOUND.formatted(reply.getMessageName(), reply.getCorrelationKey()));
  }
}
