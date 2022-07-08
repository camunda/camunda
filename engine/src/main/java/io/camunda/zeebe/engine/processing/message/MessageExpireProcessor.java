/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.api.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;

public final class MessageExpireProcessor implements TypedRecordProcessor<MessageRecord> {

  private final StateWriter stateWriter;

  public MessageExpireProcessor(final StateWriter stateWriter) {
    this.stateWriter = stateWriter;
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    stateWriter.appendFollowUpEvent(record.getKey(), MessageIntent.EXPIRED, record.getValue());
  }
}
