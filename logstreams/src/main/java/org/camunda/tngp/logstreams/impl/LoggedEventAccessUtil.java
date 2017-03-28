package org.camunda.tngp.logstreams.impl;

import org.agrona.DirectBuffer;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.positionOffset;

/**
 * Contains some utilities to access information's about the logged event, which are stored in the given buffer.
 */
public final class LoggedEventAccessUtil
{
    public static long getPosition(DirectBuffer buffer, int offset)
    {
        return buffer.getLong(positionOffset(messageOffset(offset)));
    }

    public static int getFragmentLength(DirectBuffer buffer, int offset)
    {
        return alignedLength(buffer.getInt(lengthOffset(offset)));
    }
}
