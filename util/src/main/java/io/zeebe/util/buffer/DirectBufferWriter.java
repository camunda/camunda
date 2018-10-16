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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class DirectBufferWriter implements BufferWriter {
  protected DirectBuffer buffer;
  protected int offset;
  protected int length;

  @Override
  public int getLength() {
    return length;
  }

  @Override
  public void write(MutableDirectBuffer writeBuffer, int writeOffset) {
    writeBuffer.putBytes(writeOffset, buffer, offset, length);
  }

  public DirectBufferWriter wrap(DirectBuffer buffer, int offset, int length) {
    this.buffer = buffer;
    this.offset = offset;
    this.length = length;

    return this;
  }

  public DirectBufferWriter wrap(DirectBuffer buffer) {
    return wrap(buffer, 0, buffer.capacity());
  }

  public void reset() {
    buffer = null;
    offset = -1;
    length = 0;
  }

  public static DirectBufferWriter writerFor(DirectBuffer buffer) {
    return new DirectBufferWriter().wrap(buffer);
  }
}
