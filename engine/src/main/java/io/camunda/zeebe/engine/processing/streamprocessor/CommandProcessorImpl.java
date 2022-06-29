/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor.CommandControl;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Builders;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;

/**
 * Decorates a command processor with simple accept and reject logic.
 *
 * <p>On accept it writes the state corresponding to successfully processing the command (e.g.
 * process instance creation: CREATE => CREATED); and responds if it was a client command that
 * should be responded to.
 *
 * <p>On reject it writes a command rejection
 *
 * @param <T> the record value type
 */
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
  private final Builders builders;

  public CommandProcessorImpl(
      final CommandProcessor<T> commandProcessor,
      final KeyGenerator keyGenerator,
      final Builders builders) {
    wrappedProcessor = commandProcessor;
    this.keyGenerator = keyGenerator;
    this.builders = builders;
  }

  @Override
  public void processRecord(final TypedRecord<T> command) {

    entityKey = command.getKey();

    final boolean shouldRespond = wrappedProcessor.onCommand(command, this);

    final boolean respond = shouldRespond && command.hasRequestMetadata();

    if (isAccepted) {
      builders.state().appendFollowUpEvent(entityKey, newState, updatedValue);
      wrappedProcessor.afterAccept(
          builders.command(), builders.state(), entityKey, newState, updatedValue);
      if (respond) {
        builders.response().writeEventOnCommand(entityKey, newState, updatedValue, command);
      }
    } else {
      builders.rejection().appendRejection(command, rejectionType, rejectionReason);
      if (respond) {
        builders.response().writeRejectionOnCommand(command, rejectionType, rejectionReason);
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
