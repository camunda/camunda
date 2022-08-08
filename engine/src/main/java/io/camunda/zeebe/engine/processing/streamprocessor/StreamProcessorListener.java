/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.streamprocessor.StreamProcessor;

/**
 * A listener for the {@link StreamProcessor}. Allows retrieving insides of the processing and
 * skipping of records. It can be especially useful for testing purposes. Note that the listener is
 * invoked inside the context of the stream processor and should not block its execution.
 */
public interface StreamProcessorListener {

  /**
   * Is called when a command is processed.
   *
   * @param processedCommand the command that is processed
   */
  void onProcessed(TypedRecord<?> processedCommand);

  /**
   * Is called when a record is skipped and not processed.
   *
   * @param skippedRecord the record that is skipped
   */
  void onSkipped(final LoggedEvent skippedRecord);
}
