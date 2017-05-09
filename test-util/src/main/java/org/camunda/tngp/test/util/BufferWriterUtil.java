/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.test.util;

import static org.junit.Assert.*;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.util.ReflectUtil;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class BufferWriterUtil
{

    public static <T extends BufferWriter & BufferReader> void assertThatWriteAndReadEquals(T writer)
    {
        @SuppressWarnings("unchecked")
        final T reader = ReflectUtil.newInstance((Class<T>) writer.getClass());

        wrap(writer, reader);

        assertEquals(reader, writer);
    }


    public static void wrap(BufferWriter writer, BufferReader reader)
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[writer.getLength()]);
        writer.write(buffer, 0);

        reader.wrap(buffer, 0, buffer.capacity());
    }

    public static <T extends BufferReader> T wrap(BufferWriter writer, Class<T> readerClass)
    {
        final T reader = ReflectUtil.newInstance(readerClass);

        wrap(writer, reader);

        return reader;
    }

}
