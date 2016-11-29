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
package org.camunda.tngp.util.buffer;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class BufferUtil
{
    private BufferUtil()
    { // avoid instantiation of util class
    }

    public static String bufferAsString(final DirectBuffer buffer)
    {
        final byte[] bytes = new byte[buffer.capacity()];

        buffer.getBytes(0, bytes);

        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void wrapString(String argument, UnsafeBuffer buffer)
    {
        final byte[] bytes = argument.getBytes(StandardCharsets.UTF_8);
        buffer.wrap(bytes);
    }

    /**
     * byte-by-byte comparison of two buffers
     */
    public static boolean contentsEqual(
            DirectBuffer buffer1,
            DirectBuffer buffer2)
    {

        if (buffer1.capacity() == buffer2.capacity())
        {
            boolean equal = true;

            for (int i = 0; i < buffer1.capacity() && equal; i++)
            {
                equal &= buffer1.getByte(i) == buffer2.getByte(i);
            }

            return equal;
        }
        else
        {
            return false;
        }
    }

}
