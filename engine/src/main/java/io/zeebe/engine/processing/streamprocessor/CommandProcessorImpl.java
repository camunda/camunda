/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.engine.processing.streamprocessor.CommandProcessor.CommandControl;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;

public final class CommandProcessorImpl<T extends UnifiedRecordValue>
    implements TypedRecordProcessor<T>, CommandControl<T> {

  private final CommandProcessor<T> wrappedProcessor;

  private final KeyGenerator keyGenerator;

  private boolean isAccepted;
  private long entityKey;

  private Intent newState;
  private T updatedValue;

  private RejectionType rejectionType;
  private String rejectionReason;

  public CommandProcessorImpl(
      final KeyGenerator keyGenerator, final CommandProcessor<T> commandProcessor) {
    this.keyGenerator = keyGenerator;
    wrappedProcessor = commandProcessor;
  }

  @Override
  public void processRecord(
      final TypedRecord<T> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    entityKey = command.getKey();
    final boolean shouldRespond = wrappedProcessor.onCommand(command, this, streamWriter);

    final boolean respond = shouldRespond && command.hasRequestMetadata();

    if (isAccepted) {
      streamWriter.appendFollowUpEvent(entityKey, newState, updatedValue);
      if (respond) {
        responseWriter.writeEventOnCommand(entityKey, newState, updatedValue, command);
      }
    } else {
      streamWriter.appendRejection(command, rejectionType, rejectionReason);
      if (respond) {
        responseWriter.writeRejectionOnCommand(command, rejectionType, rejectionReason);
      }
    }
  }

  @Override
  public long accept(final Intent newState, final T updatedValue) {
    if (entityKey < 0) {
      entityKey = keyGenerator.nextKey();
    }

    isAccepted = true;
    this.newState = newState;
    this.updatedValue = updatedValue;
    return entityKey;
  }

  @Override
  public void reject(final RejectionType type, final String reason) {
    isAccepted = false;
    rejectionType = type;
    rejectionReason = reason;
  }
}
