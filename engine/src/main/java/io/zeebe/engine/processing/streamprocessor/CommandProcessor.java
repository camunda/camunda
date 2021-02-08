/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;

/**
 * High-level record processor abstraction that implements the common behavior of most
 * command-handling processors.
 */
public interface CommandProcessor<T extends UnifiedRecordValue> {

  default boolean onCommand(final TypedRecord<T> command, final CommandControl<T> commandControl) {
    return true;
  }

  interface CommandControl<T> {
    /** @return the key of the entity */
    long accept(Intent newState, T updatedValue);

    void reject(RejectionType type, String reason);
  }
}
