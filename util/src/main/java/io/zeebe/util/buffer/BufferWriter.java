/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.util.buffer;

import org.agrona.MutableDirectBuffer;

/**
 * Implementations may add custom setters to specify values that should be written. Values are
 * written/copied when the {@link #write(MutableDirectBuffer, int)} method is called. Calling a
 * call-by-reference setter method (e.g. an Object setter) tells the writer <em>which object</em> to
 * write but not <em>what value</em>. The value is only determined at the time of writing, so that
 * value changes happening between setter and <em>#write</em> invocations affect the writer.
 */
public interface BufferWriter {
  /** @return the number of bytes that this writer is going to write */
  int getLength();

  /**
   * Writes to a buffer.
   *
   * @param buffer the buffer that this writer writes to
   * @param offset the offset in the buffer that the writer begins writing at
   */
  void write(MutableDirectBuffer buffer, int offset);
}
