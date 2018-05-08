/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.benchmarks.msgpack;

import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import io.zeebe.broker.job.data.JobEvent;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.util.buffer.BufferUtil;

@State(Scope.Thread)
public class POJOMappingContext
{

    protected JobEvent jobEvent = new JobEvent();

    /*
     * values encoded in the way TaskEvent declares them
     */
    protected MutableDirectBuffer optimalOrderMsgPack;
    protected DirectBuffer reverseOrderMsgPack;

    protected MutableDirectBuffer writeBuffer;


    @Setup
    public void setUp()
    {

        jobEvent.setLockTime(System.currentTimeMillis());
        jobEvent.setType(BufferUtil.wrapString("someTaskType"));

        final DirectBuffer payload = write((w) ->
        {
            w.writeString(BufferUtil.wrapString("key1"));
            w.writeString(BufferUtil.wrapString("aValue"));
            w.writeString(BufferUtil.wrapString("key2"));
            w.writeString(BufferUtil.wrapString("alsoaValue"));
            w.writeString(BufferUtil.wrapString("key3"));
            w.writeString(BufferUtil.wrapString("anotherValue"));
            w.writeString(BufferUtil.wrapString("key4"));
            w.writeString(BufferUtil.wrapString("yetAnotherValue"));
        });
        jobEvent.setPayload(payload);

        final DirectBuffer headers = write((w) ->
        {
            w.writeMapHeader(2);
            w.writeString(BufferUtil.wrapString("key1"));
            w.writeString(BufferUtil.wrapString("value"));
            w.writeString(BufferUtil.wrapString("key2"));
            w.writeString(BufferUtil.wrapString("value"));
        });
        jobEvent.setCustomHeaders(headers);

        optimalOrderMsgPack = new UnsafeBuffer(new byte[jobEvent.getLength()]);
        jobEvent.write(optimalOrderMsgPack, 0);

        this.reverseOrderMsgPack = revertMapProperties(optimalOrderMsgPack);

        this.writeBuffer = new UnsafeBuffer(new byte[optimalOrderMsgPack.capacity()]);
    }

    protected DirectBuffer write(Consumer<MsgPackWriter> arg)
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);
        final MsgPackWriter writer = new MsgPackWriter();
        writer.wrap(buffer, 0);
        arg.accept(writer);
        buffer.wrap(buffer, 0, writer.getOffset());
        return buffer;
    }

    public DirectBuffer getOptimalOrderEncodedJobEvent()
    {
        return optimalOrderMsgPack;
    }

    public DirectBuffer getReverseOrderEncodedJobEvent()
    {
        return reverseOrderMsgPack;
    }

    public MutableDirectBuffer getWriteBuffer()
    {
        return writeBuffer;
    }

    public JobEvent getJobEvent()
    {
        return jobEvent;
    }

    protected DirectBuffer revertMapProperties(DirectBuffer msgPack)
    {
        MsgPackReader reader = new MsgPackReader();
        reader.wrap(msgPack, 0, msgPack.capacity());
        int size = reader.readMapHeader();

        UnsafeBuffer buf = new UnsafeBuffer(new byte[msgPack.capacity()]);

        MsgPackWriter writer = new MsgPackWriter();
        writer.wrap(buf, 0);
        writer.writeMapHeader(size);

        int targetOffset = msgPack.capacity();

        for (int i = 0; i < size; i++)
        {
            int keySourceOffset = reader.getOffset();
            reader.skipValue();
            int valueSourceOffset = reader.getOffset();
            int keyLength = valueSourceOffset - keySourceOffset;
            reader.skipValue();
            int valueLength = reader.getOffset() - valueSourceOffset;

            targetOffset -= keyLength + valueLength;
            buf.putBytes(targetOffset, msgPack, keySourceOffset, keyLength + valueLength);
        }

        return buf;
    }
}
