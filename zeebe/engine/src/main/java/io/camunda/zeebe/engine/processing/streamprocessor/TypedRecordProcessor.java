/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public interface TypedRecordProcessor<T extends UnifiedRecordValue> {

  void processRecord(final TypedRecord<T> record);

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

  enum ProcessingError {
    EXPECTED_ERROR,
    UNEXPECTED_ERROR
  }
}
