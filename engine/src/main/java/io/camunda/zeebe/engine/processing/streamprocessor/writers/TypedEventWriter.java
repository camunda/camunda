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

  /**
   * Append a follow up event to the result builder
   *
   * @param key the key of the event
   * @param intent the intent of the event
   * @param value the record of the event
   * @throws io.camunda.zeebe.stream.api.records.RecordBatch.ExceededBatchRecordSizeException if the
   *     appended event doesn't fit into the RecordBatch
   */
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
  boolean canWriteEventOfLength(final int eventLength);
}
