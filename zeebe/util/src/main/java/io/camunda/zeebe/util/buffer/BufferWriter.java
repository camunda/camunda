/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.buffer;

import org.agrona.MutableDirectBuffer;

/**
 * Implementations may add custom setters to specify values that should be written. Values are
 * written/copied when the {@link #write(MutableDirectBuffer, int)} method is called. Calling a
 * call-by-reference setter method (e.g. an Object setter) tells the writer <em>which object</em> to
 * write but not <em>what value</em>. The value is only determined at the time of writing, so that
 * value changes happening between setter and <em>#write</em> invocations affect the writer.
 */
public interface BufferWriter {
  /**
   * @return the number of bytes that this writer is going to write
   */
  int getLength();

  /**
   * Writes to a buffer.
   *
   * @param buffer the buffer that this writer writes to
   * @param offset the offset in the buffer that the writer begins writing at
   * @return the number of bytes written
   */
  int write(MutableDirectBuffer buffer, int offset);
}
