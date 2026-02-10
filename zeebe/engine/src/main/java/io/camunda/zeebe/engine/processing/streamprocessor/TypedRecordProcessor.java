/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public interface TypedRecordProcessor<T extends UnifiedRecordValue> {

  void processRecord(final TypedRecord<T> record);

  /**
   * Process the given record and build the processing result using the provided result builder.
   *
   * <p>By default, this method delegates to {@link #processRecord(TypedRecord)}, allowing
   * implementations to override only one of the two methods based on their needs. If an
   * implementation overrides this method, it should use the provided {@code resultBuilder} to
   * construct the processing result, which may include appending follow-up records or setting a
   * response. If it does not override this method, the default implementation will simply call
   * {@link #processRecord(TypedRecord)}, and any processing results would need to be handled within
   * that method.
   *
   * @param record The record to process
   * @param resultBuilder The builder to construct the processing result, allowing to append
   *     follow-up records or set a response
   */
  default void processRecord(
      final TypedRecord<T> record, final ProcessingResultBuilder resultBuilder) {
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

  /**
   * A hook that is called before processing result is built. Can be used to modify the processing
   * result, e.g. set metadata extensions to enhance metadata of produced records.
   *
   * @param processingResultBuilder the processing result builder
   */
  default void onPreProcess(
      final TypedRecord<T> record, final ProcessingResultBuilder processingResultBuilder) {}

  enum ProcessingError {
    EXPECTED_ERROR,
    UNEXPECTED_ERROR
  }
}
