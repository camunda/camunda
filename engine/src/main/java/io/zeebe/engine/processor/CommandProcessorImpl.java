/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.engine.processor.CommandProcessor.CommandControl;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;

public class CommandProcessorImpl<T extends UnifiedRecordValue>
    implements TypedRecordProcessor<T>, CommandControl<T> {

  private final CommandProcessor<T> wrappedProcessor;

  private KeyGenerator keyGenerator;

  private boolean isAccepted;
  private long entityKey;

  private Intent newState;
  private T updatedValue;

  private RejectionType rejectionType;
  private String rejectionReason;

  public CommandProcessorImpl(final CommandProcessor<T> commandProcessor) {
    this.wrappedProcessor = commandProcessor;
  }

  @Override
  public void onOpen(ReadonlyProcessingContext context) {
    this.keyGenerator = context.getZeebeState().getKeyGenerator();
  }

  @Override
  public void processRecord(
      final TypedRecord<T> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    entityKey = command.getKey();
    wrappedProcessor.onCommand(command, this, streamWriter);

    final boolean respond = command.hasRequestMetadata();

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
    this.rejectionType = type;
    this.rejectionReason = reason;
  }
}
