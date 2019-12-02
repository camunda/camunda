/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.spi;

import java.io.Closeable;
import java.nio.ByteBuffer;

public interface LogStorageReader extends Closeable {
  /**
   * Returns the address of the first block in the storage or {@link
   * LogStorage#OP_RESULT_INVALID_ADDR} if the storage is empty.
   */
  long getFirstBlockAddress();

  /**
   * Naive implementation of the {@link #read(ByteBuffer, long, ReadResultProcessor)} method. Does
   * not process the bytes which are read.
   *
   * <p>Returns an operation result status code which is either
   *
   * <ul>
   *   <li>positive long representing the next address at which the next block of data can be read
   *   <li>{@link LogStorage#OP_RESULT_INVALID_ADDR}: in case the provided address does not exist
   *   <li>{@link LogStorage#OP_RESULT_NO_DATA}: in case no data is (yet) available at that address
   *   <li>{@link LogStorage#OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY}: in case the storage is block
   *       addressable and the provided buffer does not have sufficient capacity to read a whole
   *       block
   * </ul>
   *
   * If this method returns with a positive status code, bytes will be written between the given
   * readbuffer's {@link ByteBuffer#position()} and {@link ByteBuffer#limit()}.
   *
   * @param readBuffer the buffer to read into
   * @param address the address in the underlying storage from which bytes should be read
   * @return the next address from which bytes can be read or error status code.
   */
  long read(ByteBuffer readBuffer, long address);

  /**
   * Reads bytes into the read buffer starting at address and process the read bytes with the help
   * of the processor.
   *
   * <p>Returns an operation result status code which is either
   *
   * <ul>
   *   <li>positive long representing the next address at which the next block of data can be read
   *   <li>{@link LogStorage#OP_RESULT_INVALID_ADDR}: in case the provided address does not exist
   *   <li>{@link LogStorage#OP_RESULT_NO_DATA}: in case no data is (yet) available at that address
   *   <li>{@link LogStorage#OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY}: in case the storage is block
   *       addressable and the provided buffer does not have sufficient capacity to read a whole
   *       block
   * </ul>
   *
   * If this method returns with a positive status code, bytes will be written between the given
   * readbuffer's {@link ByteBuffer#position()} and {@link ByteBuffer#limit()}.
   *
   * @param readBuffer the buffer to read into
   * @param address the address in the underlying storage from which bytes should be read
   * @param processor the processor to process the buffer and the read result
   * @return the next address from which bytes can be read or error status code.
   */
  long read(ByteBuffer readBuffer, long address, ReadResultProcessor processor);

  /**
   * Reads bytes into the given read buffer, starts with the last written blocks and iterates with
   * help of the given processor.
   *
   * @param readBuffer the buffer which will contain the last block after this method returns
   * @param processor the processor process the read bytes
   * @return the address of the last block
   */
  long readLastBlock(ByteBuffer readBuffer, ReadResultProcessor processor);

  /**
   * Returns an address of the block that may contain the position. The exact address returned can
   * be implementation-dependent. For example, a segmented storage can return the address of the
   * first byte in the segment.
   *
   * @return address in the underlying storage for which positionReader returns a value <= position
   */
  long lookUpApproximateAddress(long position);

  @Override
  void close();
}
