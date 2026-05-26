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
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@link MessageStartProcessInstanceRequestIntent#REJECT_UNIQUENESS} command on {@code
 * P_K}, which is the rejection reply from {@code P_B} indicating that an active process instance
 * with the same {@code businessId} already exists.
 *
 * <p>This processor writes the {@link MessageStartProcessInstanceRequestIntent#UNIQUENESS_REJECTED}
 * follow-up event whose applier does the bookkeeping cleanup (pending-ask state removal). The
 * message stays buffered on {@code P_K}, waiting for the pull-based release mechanism in Increment
 * 4.
 */
@ExcludeAuthorizationCheck
public final class MessageStartProcessInstanceUniquenessRejectReplyProcessor
    implements TypedRecordProcessor<MessageStartProcessInstanceRequestRecord> {

  private final StateWriter stateWriter;

  public MessageStartProcessInstanceUniquenessRejectReplyProcessor(final StateWriter stateWriter) {
    this.stateWriter = stateWriter;
  }

  @Override
  public void processRecord(final TypedRecord<MessageStartProcessInstanceRequestRecord> record) {
    stateWriter.appendFollowUpEvent(
        record.getKey(),
        MessageStartProcessInstanceRequestIntent.UNIQUENESS_REJECTED,
        record.getValue());
  }
}
