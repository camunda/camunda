/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.buffer;

import org.agrona.DirectBuffer;

/**
 * Implementations may expose methods for access to properties from the buffer that is read. The
 * reader is a <em>view</em> on the buffer Any concurrent changes to the underlying buffer become
 * immediately visible to the reader.
 */
public interface BufferReader {
  /**
   * Wraps a buffer for read access.
   *
   * @param buffer the buffer to read from
   * @param offset the offset at which to start reading
   * @param length the length of the values to read
   */
  void wrap(DirectBuffer buffer, int offset, int length);
}
