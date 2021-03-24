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
package io.zeebe.journal.file;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Optional;

public final class FrameUtil {

  private static final byte VERSION = 1;
  private static final byte IGNORE = 0;
  private static final int LENGTH = 1;

  private FrameUtil() {}

  public static void writeVersion(final ByteBuffer buffer, final int offset) {
    write(buffer, offset, VERSION);
  }

  public static void markAsIgnored(final ByteBuffer buffer, final int offset) {
    write(buffer, offset, IGNORE);
  }

  /**
   * If the frame is valid, returns an Optional with the frame version (which can span from 1-255)
   * and the buffer's position in incremented. If the frame should be ignored, the returned Optional
   * is empty and the buffer's position is the same.
   */
  public static Optional<Integer> readVersion(final ByteBuffer buffer) {
    buffer.mark();

    try {
      final byte val = buffer.get();

      if (val != IGNORE) {
        return Optional.of((int) val);
      }
    } catch (BufferUnderflowException e) {
      // nothing to read - reset
    }

    buffer.reset();
    return Optional.empty();
  }

  public static int getLength() {
    return LENGTH;
  }

  private static void write(final ByteBuffer buffer, final int offset, final byte value) {
    buffer.put(offset, value);
  }
}
