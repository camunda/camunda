/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.state.immutable.MessageCorrelationState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;

/**
 * Resolves the synchronous response of a {@code POST /messages/correlation} command that was
 * delegated to {@code P_B = hash(businessId)} for a message start event.
 *
 * <p>When a correlate command targets a message-start event whose {@code businessId} hashes to a
 * different partition than {@code P_K = hash(correlationKey)}, {@link
 * MessageCorrelationCorrelateProcessor} cannot answer the client synchronously: the instance is
 * created (or the request rejected) asynchronously on {@code P_B}. The processor therefore leaves
 * the {@link MessageCorrelationIntent#CORRELATING} event in place, whose applier stored the
 * originating request's {@code requestId}/{@code requestStreamId} keyed by {@code messageKey} in
 * {@link MessageCorrelationState} — the same deferral used for cross-partition catch-event
 * correlation.
 *
 * <p>The cross-partition reply processors on {@code P_K} ({@code STARTED}, {@code
 * UNIQUENESS_REJECTED}, {@code NO_SUBSCRIPTION_REJECTED}) call into this helper to flush that
 * pending request into the terminal {@link MessageCorrelationIntent#CORRELATED} / {@link
 * MessageCorrelationIntent#NOT_CORRELATED} event plus the matching gateway response.
 *
 * <p>The helper is a no-op when no request data exists for the {@code messageKey}. That is the
 * expected case for a {@code publish} that was delegated (publishes are asynchronous and never
 * carry a pending correlate request) and also makes the reply processors idempotent against a
 * re-delivered reply: writing the terminal event removes the request data, so a retried reply finds
 * none and does not emit a duplicate response.
 */
final class DeferredMessageStartCorrelationResponse {

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final MessageCorrelationState messageCorrelationState;
  private final MessageState messageState;
  private final MessageCorrelationRecord correlationRecord = new MessageCorrelationRecord();
  private final MessageRecord expiredMessageRecord = new MessageRecord();

  DeferredMessageStartCorrelationResponse(
      final StateWriter stateWriter,
      final TypedResponseWriter responseWriter,
      final MessageCorrelationState messageCorrelationState,
      final MessageState messageState) {
    this.stateWriter = stateWriter;
    this.responseWriter = responseWriter;
    this.messageCorrelationState = messageCorrelationState;
    this.messageState = messageState;
  }

  /**
   * Flushes a successful cross-partition start into {@link MessageCorrelationIntent#CORRELATED}
   * plus an accepted response carrying the created {@code processInstanceKey}. No-op when the reply
   * did not originate from a synchronous correlate (no pending request data).
   */
  void writeCorrelatedResponse(final MessageStartProcessInstanceRequestRecord reply) {
    final var messageKey = reply.getMessageKey();
    if (!messageCorrelationState.existsRequestDataForMessageKey(messageKey)) {
      return;
    }
    final var requestData = messageCorrelationState.getRequestData(messageKey);

    correlationRecord.reset();
    correlationRecord
        .setName(reply.getMessageName())
        .setCorrelationKey(reply.getCorrelationKey())
        .setVariables(reply.getVariablesBuffer())
        .setTenantId(reply.getTenantId())
        .setBusinessId(reply.getBusinessId())
        .setMessageKey(messageKey)
        .setProcessInstanceKey(reply.getProcessInstanceKey())
        .setProcessDefinitionKey(reply.getProcessDefinitionKey())
        .setRequestId(requestData.getRequestId())
        .setRequestStreamId(requestData.getRequestStreamId());

    stateWriter.appendFollowUpEvent(
        messageKey, MessageCorrelationIntent.CORRELATED, correlationRecord);
    responseWriter.writeAcceptedResponse(
        messageKey,
        MessageCorrelationIntent.CORRELATED,
        correlationRecord,
        ValueType.MESSAGE_CORRELATION,
        requestData.getRequestId(),
        requestData.getRequestStreamId());
  }

  /**
   * Flushes a cross-partition rejection into {@link MessageCorrelationIntent#NOT_CORRELATED} plus a
   * rejected response with the given type and reason. No-op when the reply did not originate from a
   * synchronous correlate (no pending request data).
   *
   * <p>A synchronous correlate is fire-and-forget (its message carries {@code TTL = -1}): unlike a
   * buffered publish it must not linger in state nor be retried. On a rejection this therefore also
   * expires the buffered message, whose {@code EXPIRED} applier removes it and clears the pending
   * cross-partition ask — stopping the retry scheduler from starting a late instance after the
   * client has already received {@code NOT_FOUND}. A publish keeps its buffered message for the
   * retry mechanism (no request data, so this method is a no-op for it).
   */
  void writeNotCorrelatedResponse(
      final MessageStartProcessInstanceRequestRecord reply,
      final RejectionType rejectionType,
      final String reason) {
    final var messageKey = reply.getMessageKey();
    if (!messageCorrelationState.existsRequestDataForMessageKey(messageKey)) {
      return;
    }
    final var requestData = messageCorrelationState.getRequestData(messageKey);

    correlationRecord.reset();
    correlationRecord
        .setName(reply.getMessageName())
        .setCorrelationKey(reply.getCorrelationKey())
        .setVariables(reply.getVariablesBuffer())
        .setTenantId(reply.getTenantId())
        .setBusinessId(reply.getBusinessId())
        .setMessageKey(messageKey)
        .setRequestId(requestData.getRequestId())
        .setRequestStreamId(requestData.getRequestStreamId());

    stateWriter.appendFollowUpEvent(
        messageKey, MessageCorrelationIntent.NOT_CORRELATED, correlationRecord);
    responseWriter.writeRejectedResponse(
        messageKey,
        MessageCorrelationIntent.CORRELATE,
        correlationRecord,
        ValueType.MESSAGE_CORRELATION,
        rejectionType,
        reason,
        requestData.getRequestId(),
        requestData.getRequestStreamId());

    expireBufferedCorrelateMessage(messageKey);
  }

  /**
   * Expires the buffered message created by a synchronous correlate so its {@code EXPIRED} applier
   * removes it and clears the pending cross-partition ask. Guarded by the message's existence: it
   * may already be gone because its {@code TTL = -1} deadline was swept by the time-to-live
   * checker, or because an earlier re-delivered reply already expired it.
   */
  private void expireBufferedCorrelateMessage(final long messageKey) {
    final var storedMessage = messageState.getMessage(messageKey);
    if (storedMessage == null) {
      return;
    }
    expiredMessageRecord.reset();
    expiredMessageRecord.wrap(storedMessage.getMessage());
    stateWriter.appendFollowUpEvent(messageKey, MessageIntent.EXPIRED, expiredMessageRecord);
  }
}
