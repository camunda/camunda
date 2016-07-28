package org.camunda.tngp.log;

import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.MutableDirectBuffer;

/**
 * Used to write a log entry. Reusable but not thradsafe.
 *
 */
public class LogEntryWriter
{
    protected ClaimedFragment claimedFragment = new ClaimedFragment();

    public long write(final Log log, final BufferWriter writer, final LogEntryWriteListener writeListener)
    {
        final Dispatcher writeBuffer = log.getWriteBuffer();
        final int length = writer.getLength();

        long claimedOffset = -1;

        do
        {
            claimedOffset = writeBuffer.claim(claimedFragment, length, log.getId());
        }
        while (claimedOffset == -2);

        if (claimedOffset >= 0)
        {
            final MutableDirectBuffer buffer = claimedFragment.getBuffer();
            final int offset = claimedFragment.getOffset();

            writer.write(buffer, claimedFragment.getOffset());

            if (writeListener != null)
            {
                writeListener.beforeCommit(buffer, offset, length);
            }

            claimedFragment.commit();

            claimedOffset -= DataFrameDescriptor.alignedLength(length);
        }

        return claimedOffset;
    }
}