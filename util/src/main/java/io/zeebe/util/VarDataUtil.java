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
package io.zeebe.util;

public final class VarDataUtil {

  public static byte[] readBytes(
      final VarDataReader reader, final VarDataLengthProvider lengthProvider) {
    return readBytes(reader, lengthProvider.length());
  }

  public static byte[] readBytes(final VarDataReader reader, final int length) {
    return readBytes(reader, 0, length);
  }

  public static byte[] readBytes(final VarDataReader reader, final int offset, final int length) {
    final byte[] buffer = new byte[length];
    reader.decode(buffer, offset, length);
    return buffer;
  }

  @FunctionalInterface
  public interface VarDataLengthProvider {
    int length();
  }

  @FunctionalInterface
  public interface VarDataReader {
    int decode(byte[] buffer, int offset, int length);
  }
}
