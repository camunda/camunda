package org.camunda.tngp.transport.impl;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;
import static org.camunda.tngp.transport.impl.TransportControlFrameDescriptor.*;

import java.nio.ByteBuffer;

import org.agrona.concurrent.UnsafeBuffer;

public class StaticControlFrames
{
    public static final ByteBuffer CLOSE_FRAME = ByteBuffer.allocate(alignedLength(0));
    public static final ByteBuffer END_OF_STREAM_FRAME = ByteBuffer.allocate(alignedLength(0));

    static
    {
        final UnsafeBuffer ctrMsgWriter = new UnsafeBuffer(0, 0);

        ctrMsgWriter.wrap(CLOSE_FRAME);
        ctrMsgWriter.putInt(lengthOffset(0), 0);
        ctrMsgWriter.putShort(typeOffset(0), TYPE_CONTROL_CLOSE);

        ctrMsgWriter.wrap(END_OF_STREAM_FRAME);
        ctrMsgWriter.putInt(lengthOffset(0), 0);
        ctrMsgWriter.putShort(typeOffset(0), TYPE_CONTROL_END_OF_STREAM);

    }
}
