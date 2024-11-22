/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.CheckedJournalException.FlushException;

/** The minimum API a segment has to implement to be flush-able. */
interface FlushableSegment {

  /**
   * The current last written index of the segment, giving a guaranteed lower bound for a flush
   * index
   */
  long lastIndex();

  /**
   * Flushes any changes from the segment to its underlying storage.
   *
   * <p>If the method returns true, then it is guaranteed that the modified pages for this segment *
   * have been flushed to disk (according to the underlying file system).
   *
   * @throws FlushException if for any reason the flush failed
   */
  void flush() throws FlushException;
}
