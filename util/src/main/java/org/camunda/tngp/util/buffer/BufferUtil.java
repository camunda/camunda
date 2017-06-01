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

import static org.camunda.tngp.util.StringUtil.getBytes;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;


public final class BufferUtil
{
    public static final int NO_WRAP = 0;
    public static final int DEFAULT_WRAP = 4; // 4 bytes == 32 bits == default wide of a frame diagram

    private static final char[] HEX_CODE = "0123456789ABCDEF".toCharArray();

    private BufferUtil()
    { // avoid instantiation of util class
    }

    public static String bufferAsString(final DirectBuffer buffer)
    {
        final byte[] bytes = new byte[buffer.capacity()];

        buffer.getBytes(0, bytes);

        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static DirectBuffer wrapString(String argument)
    {
        return new UnsafeBuffer(getBytes(argument));
    }

    /**
     * Compare the given buffers.
     */
    public static boolean equals(DirectBuffer buffer1, DirectBuffer buffer2)
    {
        if (buffer1 instanceof UnsafeBuffer && buffer2 instanceof UnsafeBuffer)
        {
            return buffer1.equals(buffer2);
        }
        else if (buffer1 instanceof ExpandableArrayBuffer && buffer2 instanceof ExpandableArrayBuffer)
        {
            return buffer1.equals(buffer2);
        }
        else
        {
            return contentsEqual(buffer1, buffer2);
        }
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

    /**
     * Creates a new instance of the src buffer class and copies the underlying bytes.
     *
     * @param src the buffer to copy from
     * @return the new buffer instance
     */
    public static DirectBuffer cloneBuffer(final DirectBuffer src)
    {
        final int capacity = src.capacity();

        if (src instanceof UnsafeBuffer)
        {
            final byte[] dst = new byte[capacity];
            src.getBytes(0, dst);
            return new UnsafeBuffer(dst);
        }
        else if (src instanceof ExpandableArrayBuffer)
        {
            final ExpandableArrayBuffer dst = new ExpandableArrayBuffer(capacity);
            src.getBytes(0, dst, 0, capacity);
            return dst;
        }
        else
        {
            throw new RuntimeException("Unable to clone buffer of class " + src.getClass().getSimpleName());
        }
    }

    public static String bufferAsHexString(final BufferWriter writer)
    {
        return bufferAsHexString(writer, DEFAULT_WRAP);
    }

    public static String bufferAsHexString(final BufferWriter writer, final int wrap)
    {
        final byte[] bytes = new byte[writer.getLength()];
        final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

        writer.write(buffer, 0);

        return bytesAsHexString(bytes, wrap);
    }

    public static String bufferAsHexString(final DirectBuffer buffer)
    {
        return bufferAsHexString(buffer, DEFAULT_WRAP);
    }

    public static String bufferAsHexString(final DirectBuffer buffer, final int wrap)
    {
        return bufferAsHexString(buffer, 0, buffer.capacity(), wrap);
    }

    public static String bufferAsHexString(final DirectBuffer buffer, final int offset, final int length)
    {
        return bufferAsHexString(buffer, offset, length, DEFAULT_WRAP);
    }

    public static String bufferAsHexString(final DirectBuffer buffer, final int offset, final int length, final int wrap)
    {
        final byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes, 0, length);

        return bytesAsHexString(bytes, wrap);
    }

    public static String bytesAsHexString(final byte[] bytes)
    {
        return bytesAsHexString(bytes, DEFAULT_WRAP);
    }

    public static String bytesAsHexString(final byte[] bytes, final int wrap)
    {
        final StringBuilder builder = new StringBuilder(bytes.length * 3);

        int position = 0;
        for (final byte b : bytes)
        {
            builder
                .append(HEX_CODE[(b >> 4) & 0xF])
                .append(HEX_CODE[(b & 0xF)]);

            position++;

            if (wrap > 0 && position % wrap == 0)
            {
                builder.append('\n');
            }
            else
            {
                builder.append(' ');
            }
        }

        return builder.toString();
    }

    public static byte[] bufferAsArray(DirectBuffer buffer)
    {
        byte[] array = null;

        if (buffer.byteArray() != null)
        {
            array = buffer.byteArray();
        }
        else
        {
            array = new byte[buffer.capacity()];
            buffer.getBytes(0, array);
        }
        return array;
    }

    public static DirectBuffer wrapArray(byte[] array)
    {
        return new UnsafeBuffer(array);
    }

}
