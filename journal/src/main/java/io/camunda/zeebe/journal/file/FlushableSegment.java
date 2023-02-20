/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

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
   * @return true if the segment flushed successfully, false otherwise
   */
  boolean flush();
}
