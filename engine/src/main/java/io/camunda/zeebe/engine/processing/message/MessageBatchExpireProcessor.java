/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public final class MessageBatchExpireProcessor implements TypedRecordProcessor<MessageBatchRecord> {

  private final StateWriter stateWriter;

  private final MessageRecord emptyDeleteMessageCommand =
      new MessageRecord().setName("").setCorrelationKey("").setTimeToLive(-1L);

  public MessageBatchExpireProcessor(final StateWriter stateWriter) {
    this.stateWriter = stateWriter;
  }

  @Override
  public void processRecord(final TypedRecord<MessageBatchRecord> record) {
    record
        .getValue()
        .getMessageKeys()
        .forEach(
            messageKey ->
                stateWriter.appendFollowUpEvent(
                    messageKey, MessageIntent.EXPIRED, emptyDeleteMessageCommand));
  }
}
