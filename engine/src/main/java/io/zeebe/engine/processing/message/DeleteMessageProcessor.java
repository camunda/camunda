/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.message;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.mutable.MutableMessageState;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.record.intent.MessageIntent;

public final class DeleteMessageProcessor implements TypedRecordProcessor<MessageRecord> {

  private final MutableMessageState messageState;

  public DeleteMessageProcessor(final MutableMessageState messageState) {
    this.messageState = messageState;
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    streamWriter.appendFollowUpEvent(record.getKey(), MessageIntent.DELETED, record.getValue());

    messageState.remove(record.getKey());
  }
}
