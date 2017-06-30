package io.zeebe.transport.impl;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.typeOffset;
import static io.zeebe.transport.impl.TransportControlFrameDescriptor.TYPE_CONTROL_CLOSE;
import static io.zeebe.transport.impl.TransportControlFrameDescriptor.TYPE_CONTROL_END_OF_STREAM;
import static io.zeebe.transport.impl.TransportControlFrameDescriptor.TYPE_CONTROL_KEEP_ALIVE;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class StaticControlFrames
{
    public static final DirectBuffer CLOSE_FRAME;
    public static final DirectBuffer END_OF_STREAM_FRAME;
    public static final DirectBuffer KEEP_ALIVE_FRAME;

    static
    {
        final UnsafeBuffer ctrMsgWriter = new UnsafeBuffer(0, 0);

        ctrMsgWriter.wrap(ByteBuffer.allocate(alignedLength(0)));
        ctrMsgWriter.putInt(lengthOffset(0), 0);
        ctrMsgWriter.putShort(typeOffset(0), TYPE_CONTROL_CLOSE);

        CLOSE_FRAME = new UnsafeBuffer(ctrMsgWriter, 0, ctrMsgWriter.capacity());

        ctrMsgWriter.wrap(ByteBuffer.allocate(alignedLength(0)));
        ctrMsgWriter.putInt(lengthOffset(0), 0);
        ctrMsgWriter.putShort(typeOffset(0), TYPE_CONTROL_END_OF_STREAM);

        END_OF_STREAM_FRAME = new UnsafeBuffer(ctrMsgWriter, 0, ctrMsgWriter.capacity());

        ctrMsgWriter.wrap(ByteBuffer.allocate(alignedLength(0)));
        ctrMsgWriter.putInt(lengthOffset(0), 0);
        ctrMsgWriter.putShort(typeOffset(0), TYPE_CONTROL_KEEP_ALIVE);

        KEEP_ALIVE_FRAME = new UnsafeBuffer(ctrMsgWriter, 0, ctrMsgWriter.capacity());

    }
}
