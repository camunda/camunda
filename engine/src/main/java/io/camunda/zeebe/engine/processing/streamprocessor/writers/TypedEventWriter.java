/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;

public interface TypedEventWriter {

  void appendFollowUpEvent(long key, Intent intent, RecordValue value);

  /**
   * Use this to know whether you can write an event of this length.
   *
   * <p>Example:
   *
   * <pre>{@code
   * final TypedEventWriter writer;
   * // ... assign the writer
   * final TypedRecord<?> record;
   * // ... assign record
   * if (!writer.canWriteEventOfLength(record.getLength())) {
   *   // raise an incident or some such
   *   return;
   * }
   * }</pre>
   *
   * @param eventLength the length of the event that will be written
   * @return true if an event of length {@code eventLength} can be written
   */
  default boolean canWriteEventOfLength(final int eventLength) {
    return eventLength <= getMaxEventLength();
  }

  /** @return the maximum event length */
  int getMaxEventLength();
}
