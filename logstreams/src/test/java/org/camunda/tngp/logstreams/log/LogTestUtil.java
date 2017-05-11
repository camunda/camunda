package org.camunda.tngp.logstreams.log;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.nio.ByteBuffer;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.positionOffset;
import static org.camunda.tngp.util.buffer.BufferUtil.wrapString;

public class LogTestUtil
{
    public static final String TOPIC_NAME = "test-topic";
    public static final DirectBuffer TOPIC_NAME_BUFFER = wrapString(TOPIC_NAME);
    public static final int PARTITION_ID = 0;
    public static final String LOG_NAME = String.format("%s.%d", TOPIC_NAME, PARTITION_ID);

    public static final long LOG_POSITION = 100L;
    public static final long LOG_ADDRESS = 456L;
    public static final int EVENT_LENGTH = 100;

    public static Answer<Object> readTwoEvents(long nextAddress, int blockSize)
    {
        return (InvocationOnMock invocationOnMock) ->
        {
            final ByteBuffer argBuffer = (ByteBuffer) invocationOnMock.getArguments()[0];
            final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(0, 0);
            unsafeBuffer.wrap(argBuffer);

            // set position
            // first event
            unsafeBuffer.putLong(lengthOffset(0), 911);
            unsafeBuffer.putLong(positionOffset(messageOffset(0)), LOG_POSITION);

            // second event
            final int alignedLength = alignedLength(911);
            unsafeBuffer.putLong(lengthOffset(alignedLength), 911);
            unsafeBuffer.putLong(positionOffset(messageOffset(alignedLength)), LOG_POSITION + 1);

            argBuffer.position(blockSize);
            return nextAddress;
        };
    }
}
