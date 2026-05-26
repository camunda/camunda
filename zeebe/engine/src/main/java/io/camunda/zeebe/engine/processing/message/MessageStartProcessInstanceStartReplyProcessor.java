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
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@link MessageStartProcessInstanceRequestIntent#START} reply command on {@code P_K},
 * which is {@code P_B}'s acknowledgement that a process instance was created in response to a
 * cross-partition message-start ask. This is the commit that flips the cross-partition handshake to
 * "behavior on" — once it runs, a successful ask becomes a fully-correlated message-start on {@code
 * P_K}.
 *
 * <p>The processor writes three follow-up events, in order, so the engine's state mutations remain
 * partitionable into one applier per concern:
 *
 * <ol>
 *   <li>{@link MessageStartEventSubscriptionIntent#CORRELATED} against the originating start-event
 *       subscription, which causes the existing {@code
 *       MessageStartEventSubscriptionCorrelatedApplier} to (a) mark the message as correlated to
 *       this {@code bpmnProcessId} and (b) write the local process-correlation-key lock entry
 *       {@code (bpmnProcessId, correlationKey)} so subsequent publishes with the same correlation
 *       key are buffered exactly as today — regardless of the publish's {@code businessId}. This
 *       preserves the pre-existing lock contract verbatim for PIs created via the cross-partition
 *       path.
 *   <li>{@link MessageStartProcessInstanceRequestIntent#STARTED} so the existing applier clears the
 *       pending-ask entry on {@code P_K} (stopping the retry scheduler) and, when the holder
 *       carries a non-empty {@code (correlationKey, businessId)}, additionally records the holder's
 *       {@code businessId} on a parallel CF. That businessId discriminator is what lets the
 *       pull-based release loop (later increment) identify {@code P_B = hash(businessId)} for this
 *       lock entry — both lock and Business-Id-uniqueness release are driven by the same
 *       PI-completion event on {@code P_B}, so a single pull releases both.
 *   <li>{@link MessageIntent#EXPIRED} for the buffered message itself. The cross-partition
 *       handshake consumed the publish; leaving it in the buffer would let the next polling cycle
 *       re-evaluate it, which is unnecessary work (correlation has already happened) and would
 *       waste TTL budget.
 * </ol>
 *
 * <h3>Retry safety</h3>
 *
 * <p>{@code P_B}'s success-only dedup re-replies the same {@code processInstanceKey} when {@code
 * P_K} retries an ask, so the processor must be idempotent against a re-delivered {@link
 * MessageStartProcessInstanceRequestIntent#START} command. The CORRELATED applier uses {@code
 * insert} for the message-correlation marker, which would throw on a duplicate; this processor
 * guards the CORRELATED write with {@link MessageState#existMessageCorrelation(long,
 * org.agrona.DirectBuffer)} so retries skip it. The EXPIRED write is guarded by {@link
 * MessageState#getMessage(long)} — once the buffer is removed the message no longer exists, so the
 * follow-up event is suppressed to avoid emitting a spurious EXPIRED for a key the engine can no
 * longer resolve. The STARTED applier's writes (pending-ask removal and businessId discriminator)
 * are unconditionally idempotent via {@code deleteIfExists} and {@code upsert}.
 */
@ExcludeAuthorizationCheck
public final class MessageStartProcessInstanceStartReplyProcessor
    implements TypedRecordProcessor<MessageStartProcessInstanceRequestRecord> {

  private final StateWriter stateWriter;
  private final MessageState messageState;
  private final MessageStartEventSubscriptionRecord correlatedSubscriptionRecord =
      new MessageStartEventSubscriptionRecord();
  private final MessageRecord expiredMessageRecord = new MessageRecord();

  public MessageStartProcessInstanceStartReplyProcessor(
      final StateWriter stateWriter, final MessageState messageState) {
    this.stateWriter = stateWriter;
    this.messageState = messageState;
  }

  @Override
  public void processRecord(final TypedRecord<MessageStartProcessInstanceRequestRecord> record) {
    final var reply = record.getValue();

    // Look up the buffered message once: both the CORRELATED applier and the EXPIRED applier need
    // it (CORRELATED writes a foreign-key reference into MESSAGE_KEY; EXPIRED removes the buffered
    // entry). A missing message can happen for two distinct reasons:
    //   1. The buffer TTL fired between the original publish and the (late) reply — legitimate
    //      data loss inherent to the pull-based design; the PI on P_B is already created and the
    //      pending-ask just needs to be cleared on P_K.
    //   2. The reply is a re-delivery: a previous reply's EXPIRED applier already removed the
    //      message; the CORRELATED applier has also run and the message correlation marker is
    //      present.
    // In both cases we skip the CORRELATED + EXPIRED writes but still emit STARTED so the
    // pending-ask applier clears the entry and the retry scheduler stops re-sending.
    final var storedMessage = messageState.getMessage(reply.getMessageKey());

    // (i) Write CORRELATED against the originating message-start-event subscription, guarded by
    // both the buffered-message existence (its applier inserts an FK reference) and the existing-
    // correlation check (its applier uses insert; a retried reply would otherwise duplicate).
    if (storedMessage != null
        && !messageState.existMessageCorrelation(
            reply.getMessageKey(), reply.getBpmnProcessIdBuffer())) {
      correlatedSubscriptionRecord.reset();
      correlatedSubscriptionRecord
          .setProcessDefinitionKey(reply.getProcessDefinitionKey())
          .setBpmnProcessId(reply.getBpmnProcessIdBuffer())
          .setStartEventId(reply.getStartEventIdBuffer())
          .setProcessInstanceKey(reply.getProcessInstanceKey())
          .setCorrelationKey(reply.getCorrelationKeyBuffer())
          .setMessageKey(reply.getMessageKey())
          .setMessageName(reply.getMessageNameBuffer())
          .setVariables(reply.getVariablesBuffer())
          .setTenantId(reply.getTenantId());
      stateWriter.appendFollowUpEvent(
          reply.getMessageStartEventSubscriptionKey(),
          MessageStartEventSubscriptionIntent.CORRELATED,
          correlatedSubscriptionRecord);
    }

    // (ii) Write STARTED so the existing applier clears the pending-ask and records the
    // cross-partition businessId discriminator on the lock entry.
    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageStartProcessInstanceRequestIntent.STARTED, reply);

    // (iii) Remove the buffered message. Guarded by the same lookup: a retried reply may arrive
    // after a previous reply's EXPIRED applier already removed it, and the buffered message may
    // have been expired by its TTL deadline between the original ask and the (late) reply.
    if (storedMessage != null) {
      expiredMessageRecord.reset();
      expiredMessageRecord.wrap(storedMessage.getMessage());
      stateWriter.appendFollowUpEvent(
          reply.getMessageKey(), MessageIntent.EXPIRED, expiredMessageRecord);
    }
  }
}
