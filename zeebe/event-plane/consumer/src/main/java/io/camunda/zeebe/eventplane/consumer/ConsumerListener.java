/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.segment.LogId;
import java.util.UUID;

/**
 * Hears what happens to a log besides its records. Each method is called on the log's reader
 * thread, before the reader reads on.
 */
public interface ConsumerListener {

  /** A listener that ignores everything. */
  ConsumerListener NONE = new ConsumerListener() {};

  /**
   * Entries the reader needed are gone for good, and it reads on after them: a retention run
   * removed them, or they never arrived in the store and a later segment did.
   *
   * @param log the log
   * @param fromIndex the first index that is missing
   * @param toIndex the last index that is missing
   */
  default void onGap(final LogId log, final long fromIndex, final long toIndex) {}

  /**
   * The log's current history no longer holds what the reader has read: a restore started a new
   * history from an earlier checkpoint. The reader goes on from the last entry both share, in the
   * current history.
   *
   * @param log the log
   * @param cursor how far the reader had read, in its previous history
   * @param shared the last entry that the cursor's history and the current one share
   * @param currentHistory the current history, which the reader goes on in
   */
  default void onHistoryChange(
      final LogId log, final Cursor cursor, final Base shared, final UUID currentHistory) {}
}
