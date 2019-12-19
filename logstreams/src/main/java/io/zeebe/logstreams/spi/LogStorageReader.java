/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.spi;

import java.io.Closeable;
import org.agrona.DirectBuffer;

public interface LogStorageReader extends Closeable {

  /**
   * Returns true if nothing could be read from the log, regardless of the current position of the
   * reader - meaning any seek operations would not change the result of this call.
   *
   * @return true no records are readable, false otherwise
   */
  boolean isEmpty();

  /**
   * Returns an operation result status code which is either
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
   * If this method returns with a positive status code, the read buffer will wrap a sequence of
   * bytes representing the block that was read, where its {@link DirectBuffer#capacity()} is the
   * length of the block.
   *
   * @param readBuffer the buffer to read into
   * @param address the address in the underlying storage from which bytes should be read
   * @return the next address from which bytes can be read or error status code.
   */
  long read(DirectBuffer readBuffer, long address);

  /**
   * The given read buffer will read and wrap the last block.
   *
   * @param readBuffer the buffer which will wrap the last block after this method returns
   * @return the address of the last block
   */
  long readLastBlock(DirectBuffer readBuffer);

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
