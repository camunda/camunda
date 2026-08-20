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
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the {@link MessageStartProcessInstanceRequestIntent#REJECT_UNIQUENESS} command on {@code
 * P_K}, which is the rejection reply from {@code P_B} indicating that an active process instance
 * with the same {@code businessId} already exists.
 *
 * <p>This processor writes the {@link MessageStartProcessInstanceRequestIntent#UNIQUENESS_REJECTED}
 * follow-up event whose applier keeps the pending-ask entry and backs it off — incrementing its
 * rejection count, never removing it. The message stays buffered on {@code P_K} and its ask is
 * re-sent to {@code P_B} under capped exponential back-off by the pending-ask scheduler until the
 * holder frees the {@code businessId} (the reply then flips to {@code STARTED}) or the buffered
 * message reaches its TTL (ADR 0002 D2/D3). The cross-partition correlation-key lock release is a
 * separate mechanism and does not retry a uniqueness rejection (ADR 0002 D4).
 *
 * <p>When the delegating command was a synchronous {@code correlate} (rather than a {@code
 * publish}), the client is still awaiting a response; this processor additionally flushes the
 * deferred {@link io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent#NOT_CORRELATED}
 * event and a {@code NOT_FOUND} rejected response reporting the active-instance conflict, and
 * expires the fire-and-forget correlate message so it is neither retried nor left buffered.
 */
@ExcludeAuthorizationCheck
public final class MessageStartProcessInstanceRequestRejectUniquenessProcessor
    implements TypedRecordProcessor<MessageStartProcessInstanceRequestRecord> {

  private static final Logger LOG =
      LoggerFactory.getLogger(MessageStartProcessInstanceRequestRejectUniquenessProcessor.class);

  private static final String BLOCKED_BY_ACTIVE_INSTANCE =
      "Expected to correlate message with name '%s' to a message start event, but a process instance"
          + " is already active for business ID '%s' in process IDs %s. Only one active process"
          + " instance per business ID is allowed for message start events.";

  private final StateWriter stateWriter;
  private final MessageCorrelationMetrics metrics;
  private final DeferredMessageStartCorrelationResponse deferredCorrelationResponse;

  public MessageStartProcessInstanceRequestRejectUniquenessProcessor(
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
        reply.getMessageKey(), reply.getProcessDefinitionKey(), ReplyOutcome.REJECTED_UNIQUENESS);
    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageStartProcessInstanceRequestIntent.UNIQUENESS_REJECTED, reply);
    metrics.crossPartitionReply(ReplyOutcome.REJECTED_UNIQUENESS);

    LOG.atDebug()
        .addKeyValue("messageKey", reply.getMessageKey())
        .addKeyValue("processDefinitionKey", reply.getProcessDefinitionKey())
        .addKeyValue("outcome", ReplyOutcome.REJECTED_UNIQUENESS.getLabel())
        .log("Applied cross-partition message-start reply");

    deferredCorrelationResponse.writeNotCorrelatedResponse(
        reply,
        RejectionType.NOT_FOUND,
        BLOCKED_BY_ACTIVE_INSTANCE.formatted(
            reply.getMessageName(), reply.getBusinessId(), Set.of(reply.getBpmnProcessId())));
  }
}
