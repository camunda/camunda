/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.storage;

import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Storage abstraction for the log stream API. The storage is expected to store the given blocks of
 * data atomically (i.e. a block is fully written or not at all), in the order in which they were
 * appended.
 *
 * <p>The main access pattern is via the {@link LogStorageReader}, and is expected to be sequential.
 * The reader should support seek as efficiently as possible, but random access is not the common
 * case.
 *
 * <p>The lifecycle of the storage is expected to be independent of the log stream, and the storage
 * is simply passed along to the stream.
 */
public interface LogStorage {
  /**
   * Creates a new reader initialized at the given address.
   *
   * @return a new stateful storage reader
   */
  LogStorageReader newReader();

  /**
   * Writes a block containing one or multiple log entries in the storage and returns the address at
   * which the block has been written.
   *
   * <p>Storage implementations must guarantee eventually atomicity. When this method completes,
   * either all the bytes must be written or none at all.
   *
   * <p>The caller of this method must guarantee that the provided block contains unfragmented log
   * entries.
   *
   * @param lowestPosition the lowest record position of all records in the block buffer
   * @param highestPosition the highest record position of all records in the block buffer
   * @param bufferWriter the buffer containing a block of log entries to be written into storage
   */
  void append(
      long lowestPosition,
      long highestPosition,
      BufferWriter bufferWriter,
      AppendListener listener);

  /**
   * Writes a block containing one or multiple log entries in the storage and returns the address at
   * which the block has been written.
   *
   * <p>Storage implementations must guarantee eventually atomicity. When this method completes,
   * either all the bytes must be written or none at all.
   *
   * <p>The caller of this method must guarantee that the provided block contains unfragmented log
   * entries.
   *
   * @param lowestPosition the lowest record position of all records in the block buffer
   * @param highestPosition the highest record position of all records in the block buffer
   * @param blockBuffer the buffer containing a block of log entries to be written into storage
   */
  default void append(
      final long lowestPosition,
      final long highestPosition,
      final ByteBuffer blockBuffer,
      final AppendListener listener) {
    append(
        lowestPosition,
        highestPosition,
        new DirectBufferWriter().wrap(new UnsafeBuffer(blockBuffer)),
        listener);
  }

  /**
   * Register a commit listener
   *
   * @param listener the listener which will be notified when a new record is committed.
   */
  void addCommitListener(CommitListener listener);

  /**
   * Remove a commit listener
   *
   * @param listener the listener to remove
   */
  void removeCommitListener(CommitListener listener);

  public void updateCompactionBound(final long compactionBound);

  /**
   * An append listener can be added to an append call to be notified of different events that can
   * occur during the append operation.
   */
  interface AppendListener {

    /**
     * Called when the entry has been successfully written to the local storage.
     *
     * @param index the index of the written entry
     */
    default void onWrite(final long index, final long highestPosition) {}

    /**
     * Called when the entry has been successfully committed.
     *
     * @param index the index of the committed entry
     * @param highestPosition the highest position (i.e. last position) of the records within the
     *     entry that was committed.
     */
    default void onCommit(final long index, final long highestPosition) {}
  }
  /**
   * Consumers of LogStorage can use this listener to get notified when new records are committed.
   * The difference between this and {@link AppendListener} is that {@link AppendListener} can only
   * be used by the writer of a record. {@link CommitListener} can be used by any consumers of
   * LogStorage, and get notified of new records committed even if it did not write the record
   * itself.
   */
  interface CommitListener {

    /** Called when a new record is committed in the storage */
    void onCommit();
  }
}
