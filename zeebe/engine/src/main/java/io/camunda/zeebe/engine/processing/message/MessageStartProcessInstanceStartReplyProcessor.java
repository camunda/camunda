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
 * Handles the {@link MessageStartProcessInstanceRequestIntent#START} command on {@code P_K}, which
 * is the successful reply from {@code P_B} indicating that a process instance was created.
 *
 * <p>This processor writes the {@link MessageStartProcessInstanceRequestIntent#STARTED} follow-up
 * event whose applier does the bookkeeping cleanup (pending-ask state removal). The actual buffer
 * removal, lock-entry write, and CORRELATED event are part of commit 9's behavioral payload and are
 * not included here.
 */
@ExcludeAuthorizationCheck
public final class MessageStartProcessInstanceStartReplyProcessor
    implements TypedRecordProcessor<MessageStartProcessInstanceRequestRecord> {

  private final StateWriter stateWriter;

  public MessageStartProcessInstanceStartReplyProcessor(final StateWriter stateWriter) {
    this.stateWriter = stateWriter;
  }

  @Override
  public void processRecord(final TypedRecord<MessageStartProcessInstanceRequestRecord> record) {
    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageStartProcessInstanceRequestIntent.STARTED, record.getValue());
  }
}
