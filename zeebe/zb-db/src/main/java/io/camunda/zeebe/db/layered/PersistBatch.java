/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

/**
 * One atomic durable write spanning all named stores of a database plus the recovery anchor. Either
 * everything in the batch lands or nothing does — this is the invariant that keeps the anchor
 * position and the state it describes moving as one cut, so recovery never re-applies records the
 * state already contains (double application) and never skips records the state is missing (a
 * hole). See {@link LayeredStoreCoordinator} for how a persist round fills a batch.
 *
 * <p><b>Threading:</b> a batch is filled and committed by the persist IO thread; it is not shared
 * across threads.
 */
public interface PersistBatch extends AutoCloseable {

  void put(String storeName, byte[] key, byte[] value);

  void delete(String storeName, byte[] key);

  /**
   * Stages the recovery anchor — "the state in this batch reflects the log up to {@code position}"
   * — into the same atomic write as the state entries. Must be called at most once per batch.
   */
  void putAnchor(long position);

  /** Atomically commits everything staged in this batch. Throws on failure; nothing lands. */
  void commit() throws Exception;

  /** Releases resources without committing anything that was not yet committed. */
  @Override
  void close();
}
