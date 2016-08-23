package org.camunda.tngp.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.camunda.tngp.util.buffer.BufferReader;

import org.agrona.concurrent.UnsafeBuffer;

public class LogEntryReader
{
    protected final int readBufferSize;

    protected final ByteBuffer readBuffer;
    protected final UnsafeBuffer readBufferView;

    protected final LogPollHandler pollHandler = new LogPollHandler();

    public LogEntryReader(int readBufferSize)
    {
        this.readBufferSize = readBufferSize;
        this.readBuffer = ByteBuffer.allocate(readBufferSize);
        this.readBufferView = new UnsafeBuffer(readBuffer);
    }

    public long read(final Log log, final long position, final BufferReader fragmentReader)
    {
        final long nextFragmentOffset = log.pollFragment(position, pollHandler);
        final int entryLength = pollHandler.bytesRead;

        if (entryLength > 0)
        {
            fragmentReader.wrap(readBufferView, 0, entryLength);
        }

        return nextFragmentOffset;
    }

    protected class LogPollHandler implements LogFragmentHandler
    {
        protected boolean fragmentRead = false;
        protected int bytesRead;

        public void onFragment(long position, FileChannel fileChannel, int offset, int length)
        {
            readBuffer.clear();
            readBuffer.limit(Math.min(readBuffer.capacity(), length));

            try
            {
                bytesRead = fileChannel.read(readBuffer, offset);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
}
