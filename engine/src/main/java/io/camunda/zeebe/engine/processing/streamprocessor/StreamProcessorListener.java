/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.logstreams.log.LoggedEvent;

/**
 * A listener for the {@link StreamProcessor}. Allows retrieving insides of the processing and
 * replay of records. It can be especially useful for testing purposes. Note that the listener is
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
  default void onSkipped(final LoggedEvent skippedRecord) {}

  /**
   * Is called when one or more events are replayed. Even if the state changes are not applied.
   *
   * @param lastReplayedEventPosition the position of the event that is replayed last
   * @param lastReadRecordPosition the position of the record that is read last
   */
  default void onReplayed(
      final long lastReplayedEventPosition, final long lastReadRecordPosition) {}
}
