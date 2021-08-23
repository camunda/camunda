/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.log;

import io.camunda.zeebe.logstreams.log.LogStreamBatchReader.Batch;
import io.camunda.zeebe.util.CloseableSilently;
import java.util.Iterator;

/**
 * Reads the log stream in batches. Similar to {@link LogStreamReader} but groups events with the
 * same source event position in batches. Can be used to read all follow-up events at once.
 *
 * <pre>
 * <code>
 *
 * // optionally
 * reader.seekToNextBatch(position);
 *
 * while (reader.hasNext()) {
 *     final Batch batch = reader.next();
 *     while (batch.hasNext()) {
 *         final LoggedEvent event = batch.next();
 *         // ...
 *     }
 * }
 * </code>
 * </pre>
 */
public interface LogStreamBatchReader extends Iterator<Batch>, CloseableSilently {

  /**
   * Seeks to the next batch after the given position. If the position is negative then it seeks to
   * the first event.
   *
   * @param position the position to seek for the next event
   * @return <code>true</code>, if the given position exists, or if it is negative
   */
  boolean seekToNextBatch(long position);

  /**
   * A batch of events that share the same source record position. Events with no source event
   * position are in a singleton batch.
   *
   * <pre>
   * <code>
   *
   * while (batch.hasNext()) {
   *     final LoggedEvent event = batch.next();
   *     // ...
   * }
   * </code>
   * </pre>
   */
  interface Batch extends Iterator<LoggedEvent> {

    /**
     * Move to the head of the batch. Reads the first event of the batch next. Can be used to read
     * the whole batch again.
     */
    void head();
  }
}
