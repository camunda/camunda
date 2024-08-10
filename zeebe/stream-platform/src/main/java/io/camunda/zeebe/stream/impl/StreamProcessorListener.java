/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * A listener for the {@link StreamProcessor}. Allows retrieving insides of the processing and
 * skipping of records. It can be especially useful for testing purposes. Note that the listener is
 * invoked inside the context of the stream processor and should not block its execution.
 */
@FunctionalInterface
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
  default void onSkipped(final LoggedEvent skippedRecord) {}
}
