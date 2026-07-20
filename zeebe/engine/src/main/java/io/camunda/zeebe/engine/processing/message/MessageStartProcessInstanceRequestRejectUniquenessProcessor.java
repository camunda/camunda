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
import java.util.Set;

/**
 * Handles the {@link MessageStartProcessInstanceRequestIntent#REJECT_UNIQUENESS} command on {@code
 * P_K}, which is the rejection reply from {@code P_B} indicating that an active process instance
 * with the same {@code businessId} already exists.
 *
 * <p>This processor writes the {@link MessageStartProcessInstanceRequestIntent#UNIQUENESS_REJECTED}
 * follow-up event whose applier does the bookkeeping cleanup (pending-ask state removal). The
 * message stays buffered on {@code P_K}, waiting for the pull-based release mechanism in Increment
 * 4.
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

  private static final String BLOCKED_BY_ACTIVE_INSTANCE =
      "Expected to correlate message with name '%s' to a message start event, but a process instance"
          + " is already active for business ID '%s' in process IDs %s. Only one active process"
          + " instance per business ID is allowed for message start events.";

  private final StateWriter stateWriter;
  private final DeferredMessageStartCorrelationResponse deferredCorrelationResponse;

  public MessageStartProcessInstanceRequestRejectUniquenessProcessor(
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
        record.getKey(), MessageStartProcessInstanceRequestIntent.UNIQUENESS_REJECTED, reply);

    deferredCorrelationResponse.writeNotCorrelatedResponse(
        reply,
        RejectionType.NOT_FOUND,
        BLOCKED_BY_ACTIVE_INSTANCE.formatted(
            reply.getMessageName(), reply.getBusinessId(), Set.of(reply.getBpmnProcessId())));
  }
}
