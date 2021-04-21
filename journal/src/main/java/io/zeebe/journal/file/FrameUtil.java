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

import java.nio.ByteBuffer;

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
   * Reads the version at buffer's current position. The position of the buffer will be advanced.
   */
  public static int readVersion(final ByteBuffer buffer) {
    return buffer.get();
  }

  /**
   * Returns true if there is a valid version at buffer's current position. The position of the
   * buffer will be unchanged.
   */
  public static boolean hasValidVersion(final ByteBuffer buffer) {
    if (buffer.capacity() < buffer.position() + LENGTH) {
      return false;
    }
    return buffer.get(buffer.position()) != IGNORE;
  }

  public static int getLength() {
    return LENGTH;
  }

  private static void write(final ByteBuffer buffer, final int offset, final byte value) {
    buffer.put(offset, value);
  }
}
