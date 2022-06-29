/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateBuilder;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;

public final class MessageExpireProcessor implements TypedRecordProcessor<MessageRecord> {

  private final StateBuilder stateBuilder;

  public MessageExpireProcessor(final StateBuilder stateBuilder) {
    this.stateBuilder = stateBuilder;
  }

  @Override
  public void processRecord(final TypedRecord<MessageRecord> record) {

    stateBuilder.appendFollowUpEvent(record.getKey(), MessageIntent.EXPIRED, record.getValue());
  }
}
