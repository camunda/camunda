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
package io.zeebe.test.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.util.ReflectUtil;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.concurrent.UnsafeBuffer;

public class BufferWriterUtil {

  public static <T extends BufferWriter & BufferReader> void assertEqualFieldsAfterWriteAndRead(
      final T writer, String... fieldNames) {
    final T reader = writeAndRead(writer);

    assertThat(reader).isEqualToComparingOnlyGivenFields(writer, fieldNames);
  }

  public static <T extends BufferWriter & BufferReader> T writeAndRead(final T writer) {
    @SuppressWarnings("unchecked")
    final T reader = ReflectUtil.newInstance((Class<T>) writer.getClass());

    wrap(writer, reader);

    return reader;
  }

  public static void wrap(BufferWriter writer, BufferReader reader) {
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[writer.getLength()]);
    writer.write(buffer, 0);

    reader.wrap(buffer, 0, buffer.capacity());
  }

  public static <T extends BufferReader> T wrap(BufferWriter writer, Class<T> readerClass) {
    final T reader = ReflectUtil.newInstance(readerClass);

    wrap(writer, reader);

    return reader;
  }
}
