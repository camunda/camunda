/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;

public interface TypedRecordProcessor<T extends UnifiedRecordValue> {

  default void processRecord(final TypedRecord<T> record) {}

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
