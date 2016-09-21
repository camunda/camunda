package org.camunda.tngp.log;

import static org.agrona.BitUtil.*;
import static org.camunda.tngp.log.impl.LogEntryDescriptor.*;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.status.AtomicCounter;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.impl.LogContext;
import org.camunda.tngp.log.impl.LogImpl;
import org.camunda.tngp.util.buffer.BufferWriter;

public class LogEntryWriter
{
    protected DirectBufferWriter bufferWriterInstance = new DirectBufferWriter();
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();
    protected Dispatcher logWriteBuffer;
    protected AtomicCounter positionCounter;
    protected int logId;

    protected boolean positionAsKey;
    protected long key;

    protected BufferWriter valueWriter;


    public LogEntryWriter()
    {
    }

    public LogEntryWriter(Log log)
    {
        wrap(log);
    }

    public void wrap(Log log)
    {
        final LogImpl logImpl = (LogImpl) log;

        final LogContext logContext = logImpl.getLogContext();

        this.logWriteBuffer = logContext.getWriteBuffer();
        this.logId = logContext.getLogId();
        this.positionCounter = logContext.getPositionCounter();

        reset();
    }

    public LogEntryWriter positionAsKey()
    {
        positionAsKey = true;
        return this;
    }

    public LogEntryWriter key(long key)
    {
        this.key = key;
        return this;
    }

    public LogEntryWriter value(DirectBuffer value, int valueOffset, int valueLength)
    {
        return valueWriter(bufferWriterInstance.init(value, valueOffset, valueLength));
    }

    public LogEntryWriter value(DirectBuffer value)
    {
        return value(value, 0, value.capacity());
    }

    public LogEntryWriter valueWriter(BufferWriter writer)
    {
        this.valueWriter = writer;
        return this;
    }

    public void reset()
    {
        positionAsKey = false;
        key = -1L;
        bufferWriterInstance.buffer = null;
        bufferWriterInstance.offset = -1;
        bufferWriterInstance.length = 0;
        valueWriter = null;
    }

    public long tryWrite()
    {
        long result = -1;

        final short keyLength = SIZE_OF_LONG;

        // claim fragment in log write buffer
        final long claimedPosition = claimLogEntry(valueWriter.getLength(), keyLength);

        if (claimedPosition >= 0)
        {
            try
            {
                final long position = positionCounter.orderedIncrement();

                final MutableDirectBuffer writeBuffer = claimedFragment.getBuffer();
                final int bufferOffset = claimedFragment.getOffset();
                final int keyOffset = keyOffset(bufferOffset);
                final int valueWriteOffset = keyOffset + keyLength;
                final long keyToWrite = positionAsKey ? position : key;

                // write log entry header
                writeBuffer.putLong(positionOffset(bufferOffset), position);
                writeBuffer.putShort(keyTypeOffset(bufferOffset), KEY_TYPE_UINT64);
                writeBuffer.putShort(keyLengthOffset(bufferOffset), keyLength);
                writeBuffer.putLong(keyOffset, keyToWrite);

                // write log entry
                valueWriter.write(writeBuffer, valueWriteOffset);

                result = claimedPosition;
                claimedFragment.commit();
                reset();
            }
            catch (Exception e)
            {
                claimedFragment.abort();
                reset();
                LangUtil.rethrowUnchecked(e);
            }
        }

        return result;
    }

    private long claimLogEntry(final int valueLength, final short keyLength)
    {
        final int framedLength = valueLength + headerLength(keyLength);

        long claimedPosition = -2;

        do
        {
            claimedPosition = logWriteBuffer.claim(claimedFragment, framedLength, logId);
        }
        while (claimedPosition == -2);

        return claimedPosition;
    }

    class DirectBufferWriter implements BufferWriter
    {
        DirectBuffer buffer;
        int offset;
        int length;

        @Override
        public int getLength()
        {
            return length;
        }

        @Override
        public void write(MutableDirectBuffer writeBuffer, int writeOffset)
        {
            writeBuffer.putBytes(writeOffset, buffer, offset, length);
        }

        public DirectBufferWriter init(DirectBuffer buffer, int offset, int length)
        {
            this.buffer = buffer;
            this.offset = offset;
            this.length = length;
            return this;
        }
    }

}
