/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.stream.api.ProcessingSession;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public interface TypedRecordProcessor<T extends UnifiedRecordValue> {

  default void processRecord(final TypedRecord<T> record) {
    throw new UnsupportedOperationException(
        "A TypedRecordProcessor must implement at least one of the processRecord methods");
  }

  default void processRecord(final TypedRecord<T> record, final ProcessingSession session) {
    processRecord(record);
  }

  /**
   * Try to handle an error that occurred during processing.
   *
   * @param command The command that was being processed when the error occurred
   * @param error The error that occurred, and the processor should attempt to handle
   * @return The type of the processing error. Default: {@link ProcessingError#UNEXPECTED_ERROR}.
   */
  default ProcessingError tryHandleError(final TypedRecord<T> command, final Throwable error) {
    return ProcessingError.UNEXPECTED_ERROR;
  }

  /**
   * Indicates whether follow-up records (commands produced during processing) should be scheduled
   * for processing in separate, new command batches instead of in the current batch.
   *
   * <p>This can be useful when follow-up commands are independent, and isolating their execution
   * improves consistency and error isolation (e.g., a failure in one follow-up command does not
   * affect others). While this may reduce overall throughput, it allows for finer control of batch
   * boundaries, potential interleaving of other user or system operations, and clearer error
   * handling.
   *
   * @return {@code true} if the results/follow-up commands should be processed in separate command
   *     batches; {@code false} otherwise
   */
  default boolean shouldProcessResultsInSeparateBatches() {
    return false;
  }

  enum ProcessingError {
    EXPECTED_ERROR,
    UNEXPECTED_ERROR
  }
}
