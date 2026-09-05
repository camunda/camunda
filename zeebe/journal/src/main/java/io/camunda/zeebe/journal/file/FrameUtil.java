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
package io.camunda.zeebe.journal.file;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;

final class FrameUtil {

  private static final byte VERSION = 1;
  private static final byte IGNORE = 0;
  private static final int LENGTH = 1;

  private FrameUtil() {}

  static void writeVersion(final ByteBuffer buffer, final int offset) {
    // The version is what makes a record visible to readers, and a reader may run on another
    // thread than the writer. Without this fence, such a reader can observe the version before the
    // record it belongs to, and read a record that is still being written.
    VarHandle.releaseFence();
    write(buffer, offset, VERSION);
  }

  static void markAsIgnored(final ByteBuffer buffer, final int offset) {
    write(buffer, offset, IGNORE);
  }

  /**
   * Reads the version at buffer's current position. The position of the buffer will be advanced.
   */
  static int readVersion(final ByteBuffer buffer) {
    return buffer.get();
  }

  /**
   * Returns true if there is a valid version at buffer's current position. The position of the
   * buffer will be unchanged.
   */
  static boolean hasValidVersion(final ByteBuffer buffer) {
    if (buffer.capacity() < buffer.position() + LENGTH) {
      return false;
    }
    if (buffer.get(buffer.position()) == IGNORE) {
      return false;
    }
    // Pairs with the release fence in writeVersion: having seen the version, we may now read the
    // record it publishes.
    VarHandle.acquireFence();
    return true;
  }

  static int getLength() {
    return LENGTH;
  }

  private static void write(final ByteBuffer buffer, final int offset, final byte value) {
    buffer.put(offset, value);
  }
}
