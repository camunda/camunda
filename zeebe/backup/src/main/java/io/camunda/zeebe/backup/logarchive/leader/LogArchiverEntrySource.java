/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.camunda.zeebe.backup.logarchive.format.LogArchiveFrame;

/**
 * The committed journal entries the {@link LogArchiver} reads. Only used on the archiver's actor.
 * Abstracts the committed Raft log reader so that the archiver can be tested without Raft.
 */
public interface LogArchiverEntrySource extends AutoCloseable {

  /** Returns the index of the first entry in the log. */
  long firstIndex();

  /**
   * Positions the source so that {@link #next()} returns the entry with the given index, or the
   * first entry after it if the index is below the first index.
   */
  void seek(long index);

  /** Returns true if a committed entry is available at the current position. */
  boolean hasNext();

  /** Returns the committed entry at the current position and advances to the next one. */
  LogArchiveFrame next();

  @Override
  void close();
}
