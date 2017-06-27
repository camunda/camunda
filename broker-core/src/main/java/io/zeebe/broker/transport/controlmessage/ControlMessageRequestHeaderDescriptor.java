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
package io.zeebe.broker.transport.controlmessage;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.protocol.Protocol;

public class ControlMessageRequestHeaderDescriptor
{
    public static final int STREAM_ID_OFFSET;
    public static final int REQUEST_ID_OFFSET;

    public static final int HEADER_LENGTH;

    static
    {
        int offset = 0;

        STREAM_ID_OFFSET = offset;
        offset += SIZE_OF_INT;

        REQUEST_ID_OFFSET = offset;
        offset += SIZE_OF_LONG;

        HEADER_LENGTH = offset;
    }

    public static int framedLength(int messageLength)
    {
        return HEADER_LENGTH + messageLength;
    }

    public static int headerLength()
    {
        return HEADER_LENGTH;
    }

    protected final UnsafeBuffer buffer = new UnsafeBuffer(new byte[HEADER_LENGTH]);

    public ControlMessageRequestHeaderDescriptor wrap(DirectBuffer buffer, int offset)
    {
        this.buffer.wrap(buffer, offset, HEADER_LENGTH);
        return this;
    }

    public ControlMessageRequestHeaderDescriptor streamId(int streamId)
    {
        buffer.putInt(STREAM_ID_OFFSET, streamId, Protocol.ENDIANNESS);
        return this;
    }

    public ControlMessageRequestHeaderDescriptor requestId(long requestId)
    {
        buffer.putLong(REQUEST_ID_OFFSET, requestId, Protocol.ENDIANNESS);
        return this;
    }

    public int streamId()
    {
        return buffer.getInt(STREAM_ID_OFFSET, Protocol.ENDIANNESS);
    }

    public long requestId()
    {
        return buffer.getLong(REQUEST_ID_OFFSET, Protocol.ENDIANNESS);
    }

}
