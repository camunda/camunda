/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.record.intent.MessageIntent;

public class DeleteMessageProcessor implements TypedRecordProcessor<MessageRecord> {

  private final MessageState messageState;

  public DeleteMessageProcessor(final MessageState messageState) {
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
