/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.metrics.MessageCorrelationMetrics;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.ReplyOutcome;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the {@link MessageStartProcessInstanceRequestIntent#REJECT_NO_SUBSCRIPTION} command on
 * {@code P_K}, which is the rejection reply from {@code P_B} indicating that no matching start
 * event subscription exists on {@code P_B} (deployment-distribution race).
 *
 * <p>This processor writes the {@link
 * MessageStartProcessInstanceRequestIntent#NO_SUBSCRIPTION_REJECTED} follow-up event whose applier
 * keeps the pending-ask entry and backs it off — incrementing its rejection count, never removing
 * it. The message stays buffered on {@code P_K} and its ask is re-sent to {@code P_B} under capped
 * exponential back-off by the pending-ask scheduler until the subscription reaches {@code P_B} (the
 * reply then flips to {@code STARTED}) or the buffered message reaches its TTL (ADR 0002 D2/D3),
 * preserving the same semantics as when a local start-event subscription is missing.
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

  private static final Logger LOG =
      LoggerFactory.getLogger(
          MessageStartProcessInstanceRequestRejectNoSubscriptionProcessor.class);

  private static final String SUBSCRIPTION_NOT_FOUND =
      "Expected to find subscription for message with name '%s' and correlation key '%s', but none was found.";

  private final StateWriter stateWriter;
  private final MessageCorrelationMetrics metrics;
  private final DeferredMessageStartCorrelationResponse deferredCorrelationResponse;

  public MessageStartProcessInstanceRequestRejectNoSubscriptionProcessor(
      final StateWriter stateWriter,
      final TypedResponseWriter responseWriter,
      final MessageCorrelationState messageCorrelationState,
      final MessageState messageState,
      final MessageCorrelationMetrics metrics) {
    this.stateWriter = stateWriter;
    this.metrics = metrics;
    deferredCorrelationResponse =
        new DeferredMessageStartCorrelationResponse(
            stateWriter, responseWriter, messageCorrelationState, messageState, metrics);
  }

  @Override
  public void processRecord(final TypedRecord<MessageStartProcessInstanceRequestRecord> record) {
    final var reply = record.getValue();
    metrics.stopRoundTrip(
        reply.getMessageKey(),
        reply.getProcessDefinitionKey(),
        ReplyOutcome.REJECTED_NO_SUBSCRIPTION);
    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageStartProcessInstanceRequestIntent.NO_SUBSCRIPTION_REJECTED, reply);
    metrics.crossPartitionReply(ReplyOutcome.REJECTED_NO_SUBSCRIPTION);

    LOG.atDebug()
        .addKeyValue("messageKey", reply.getMessageKey())
        .addKeyValue("processDefinitionKey", reply.getProcessDefinitionKey())
        .addKeyValue("outcome", ReplyOutcome.REJECTED_NO_SUBSCRIPTION.getLabel())
        .log("Applied cross-partition message-start reply");

    deferredCorrelationResponse.writeNotCorrelatedResponse(
        reply,
        RejectionType.NOT_FOUND,
        SUBSCRIPTION_NOT_FOUND.formatted(reply.getMessageName(), reply.getCorrelationKey()));
  }
}
