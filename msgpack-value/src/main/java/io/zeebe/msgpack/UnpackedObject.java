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
package io.zeebe.msgpack;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.msgpack.value.ObjectValue;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnpackedObject extends ObjectValue implements Recyclable, BufferReader, BufferWriter
{

    private static final Logger LOG = LoggerFactory.getLogger("io.zeebe.msgpack");

    protected final MsgPackReader reader = new MsgPackReader();
    protected final MsgPackWriter writer = new MsgPackWriter();

    protected final UnsafeBuffer view = new UnsafeBuffer(0, 0);

    public void wrap(DirectBuffer buff)
    {
        wrap(buff, 0, buff.capacity());
    }

    @Override
    public void wrap(DirectBuffer buff, int offset, int length)
    {
        int retries = 0;
        int readerOffset = 0;
        boolean successful = false;

        view.wrap(buff, offset, length);
        final String bufferOnFirstInvocation = BufferUtil.bufferAsHexString(view, 32);

        while (!successful)
        {
            reader.wrap(buff, offset, length);
            try
            {
                read(reader);
                successful = true;

                if (retries > 0)
                {
                    final DirectBuffer buffer = reader.buffer;
                    final String lastReadByteBeforeException = String.format("0x%02x", readerOffset < length ? buffer.getByte(readerOffset) : 0);
                    LOG.warn("Retry {} was successful. Reader previously stuck at offset {} of length {} with last read byte {}.\nBuffer on first invocation:\n{}\nBuffer after last invocation:\n{}",
                            retries, readerOffset, length, lastReadByteBeforeException, bufferOnFirstInvocation, BufferUtil.bufferAsHexString(buffer, 32));

                }
            }
            catch (final Exception e)
            {
                final DirectBuffer buffer = reader.buffer;
                readerOffset = reader.getOffset() - 1;
                final String lastReadByteBeforeException = String.format("0x%02x", readerOffset < length ? buffer.getByte(readerOffset) : 0);
                LOG.error("Retry {} could not deserialize object. Deserialization stuck at offset {} of length {} with last read byte {}.\nBuffer on first invocation:\n{}\nBuffer after last invocation:\n{}",
                        retries, readerOffset, length, lastReadByteBeforeException, bufferOnFirstInvocation, BufferUtil.bufferAsHexString(buffer, 32), e);

                retries++;

                if (retries > 9)
                {
                    throw new RuntimeException("Could not deserialize object. Deserialization stuck at offset " + reader.getOffset() + " of length " + length, e);
                }
            }
        }
    }

    @Override
    public int getLength()
    {
        return getEncodedLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        writer.wrap(buffer, offset);
        write(writer);
    }

}
