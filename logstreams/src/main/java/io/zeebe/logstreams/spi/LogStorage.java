/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.spi;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Log structured storage abstraction */
public interface LogStorage {
  /**
   * Status code returned by the {@link LogStorageReader#read(ByteBuffer, long)} operation in case
   * the provided address is invalid and does not exist.
   */
  long OP_RESULT_INVALID_ADDR = -1L;

  /**
   * Status code returned by the {@link LogStorageReader#read(ByteBuffer, long)} operation in case
   * the provided address does exist but data is not available yet. This indicates that retrying the
   * operation with the same parameters will eventually return data assuming that more data will be
   * written to the log.
   */
  long OP_RESULT_NO_DATA = -2L;

  /**
   * Status code returned by the {@link LogStorageReader#read(ByteBuffer, long)} operation only if
   * underlying storage is block-addressable (in contrast to byte addressable). If the storage is
   * block addressable, consumers of this API can only read complete addressable blocks of data at a
   * time. In order to read a block, the provided read buffer must provide sufficient capacity to
   * read at least one complete block. If sufficient capacity is not available in the read buffer to
   * fit at least a complete block, this status code is returned.
   */
  long OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY = -3L;

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
   * @param blockBuffer the buffer containing a block of log entries to be written into storage
   */
  void append(
      long lowestPosition, long highestPosition, ByteBuffer blockBuffer, AppendListener listener);

  /**
   * Open the storage. Called in the log conductor thread.
   *
   * @throws IOException on I/O errors during allocating the first segments
   */
  void open() throws IOException;

  /** Close the storage. Called in the log conductor thread. */
  void close();

  /** @return <code>true</code>, if the storage is open. */
  boolean isOpen();

  boolean isClosed();

  /**
   * Flushes all appended blocks to ensure that all blocks are written completely. Note that a
   * storage implementation may do nothing if {@link #append(long, long, ByteBuffer,
   * AppendListener)} guarantees that all blocks are written immediately.
   *
   * @throws Exception if fails to flush all blocks
   */
  void flush() throws Exception;

  /**
   * An append listener can be added to an append call to be notified of different events that can
   * occur during the append operation.
   */
  interface AppendListener {

    /**
     * Called when the entry has been successfully written to the local storage.
     *
     * @param address the address of the written entry
     */
    void onWrite(long address);

    /**
     * Called when an error occurred while writing to the entry.
     *
     * @param error the error that occurred
     */
    void onWriteError(Throwable error);

    /**
     * Called when the entry has been successfully committed.
     *
     * @param address the address of the committed entry
     */
    void onCommit(long address);

    /**
     * Called when an error occurs while committing an entry.
     *
     * @param address the address of the entry to be committed
     * @param error the error that occurred
     */
    void onCommitError(long address, Throwable error);
  }
}
