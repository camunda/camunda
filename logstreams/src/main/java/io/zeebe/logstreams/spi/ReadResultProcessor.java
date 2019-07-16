/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.spi;

import java.nio.ByteBuffer;

/**
 * Represents an processor, which process the given byte buffer and the read result. The read result
 * corresponds to the bytes which was read before.
 *
 * <p>Is used in the {@link LogStorage#read(ByteBuffer, long, ReadResultProcessor)} method to
 * process the bytes, which are read and return the count of the appropriated bytes which should be
 * only been read. Can for example be used to throw away bytes, because they belong to another
 * fragment which was not read completely into the buffer.
 *
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public interface ReadResultProcessor {
  /**
   * Process the given buffer and use the readResult to return the right count of bytes, which
   * should been read. If some bytes are part of another fragment, which is not completely read into
   * the buffer, the buffer can be cleaned up and the returned count can be for example ${readResult
   * - fragmentBytes}.
   *
   * @param buffer the buffer, which should been processed
   * @param readResult the count of bytes,which are read before
   * @return the count of bytes, which should only been read
   */
  int process(ByteBuffer buffer, int readResult);
}
