package io.zeebe.logstreams.log;

import static org.agrona.BitUtil.SIZE_OF_LONG;
import static io.zeebe.dispatcher.impl.log.LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.*;

import org.agrona.*;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.dispatcher.*;
import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.util.EnsureUtil;
import io.zeebe.util.buffer.*;

public class LogStreamWriterImpl implements LogStreamWriter
{
    protected final DirectBufferWriter metadataWriterInstance = new DirectBufferWriter();
    protected final DirectBufferWriter bufferWriterInstance = new DirectBufferWriter();
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();
    protected Dispatcher logWriteBuffer;
    protected int logId;

    protected boolean positionAsKey;
    protected long key;

    protected long sourceEventPosition = -1L;
    protected DirectBuffer sourceEventLogStreamTopicName = new UnsafeBuffer(0, 0);
    protected int sourceEventLogStreamPartitionId = -1;

    protected int producerId = -1;

    protected final short keyLength = SIZE_OF_LONG;

    protected BufferWriter metadataWriter;

    protected BufferWriter valueWriter;

    public LogStreamWriterImpl()
    {
    }

    public LogStreamWriterImpl(LogStream log)
    {
        wrap(log);
    }

    @Override
    public void wrap(LogStream log)
    {
        this.logWriteBuffer = log.getWriteBuffer();
        this.logId = log.getPartitionId();

        reset();
    }

    @Override
    public LogStreamWriter positionAsKey()
    {
        positionAsKey = true;
        return this;
    }

    @Override
    public LogStreamWriter key(long key)
    {
        this.key = key;
        return this;
    }

    @Override
    public LogStreamWriter sourceEvent(final DirectBuffer logStreamTopicName, int logStreamPartitionId, long position)
    {
        this.sourceEventLogStreamTopicName.wrap(logStreamTopicName);
        this.sourceEventLogStreamPartitionId = logStreamPartitionId;
        this.sourceEventPosition = position;
        return this;
    }

    @Override
    public LogStreamWriter producerId(int producerId)
    {
        this.producerId = producerId;
        return this;
    }

    @Override
    public LogStreamWriter metadata(DirectBuffer buffer, int offset, int length)
    {
        metadataWriterInstance.wrap(buffer, offset, length);
        return this;
    }

    @Override
    public LogStreamWriter metadata(DirectBuffer buffer)
    {
        return metadata(buffer, 0, buffer.capacity());
    }

    @Override
    public LogStreamWriter metadataWriter(BufferWriter writer)
    {
        this.metadataWriter = writer;
        return this;
    }

    @Override
    public LogStreamWriter value(DirectBuffer value, int valueOffset, int valueLength)
    {
        return valueWriter(bufferWriterInstance.wrap(value, valueOffset, valueLength));
    }

    @Override
    public LogStreamWriter value(DirectBuffer value)
    {
        return value(value, 0, value.capacity());
    }

    @Override
    public LogStreamWriter valueWriter(BufferWriter writer)
    {
        this.valueWriter = writer;
        return this;
    }

    @Override
    public void reset()
    {
        positionAsKey = false;
        key = -1L;
        metadataWriter = metadataWriterInstance;
        valueWriter = null;
        sourceEventLogStreamTopicName.wrap(0, 0);
        sourceEventLogStreamPartitionId = -1;
        sourceEventPosition = -1L;
        producerId = -1;

        bufferWriterInstance.reset();
        metadataWriterInstance.reset();
    }

    @Override
    public long tryWrite()
    {
        EnsureUtil.ensureNotNull("value", valueWriter);
        if (!positionAsKey)
        {
            EnsureUtil.ensureGreaterThanOrEqual("key", key, 0);
        }

        long result = -1;

        final int valueLength = valueWriter.getLength();
        final int topicNameLength = sourceEventLogStreamTopicName.capacity();
        final int metadataLength = metadataWriter.getLength();

        // claim fragment in log write buffer
        final long claimedPosition = claimLogEntry(valueLength, topicNameLength, metadataLength);

        if (claimedPosition >= 0)
        {
            try
            {
                final MutableDirectBuffer writeBuffer = claimedFragment.getBuffer();
                final int bufferOffset = claimedFragment.getOffset();

                final long keyToWrite = positionAsKey ? claimedPosition : key;

                // write log entry header
                setPosition(writeBuffer, bufferOffset, claimedPosition);
                setProducerId(writeBuffer, bufferOffset, producerId);
                setSourceEventLogStreamPartitionId(writeBuffer, bufferOffset, sourceEventLogStreamPartitionId);
                setSourceEventPosition(writeBuffer, bufferOffset, sourceEventPosition);
                setKey(writeBuffer, bufferOffset, keyToWrite);
                setSourceEventLogStreamTopicNameLength(writeBuffer, bufferOffset, (short) topicNameLength);
                setMetadataLength(writeBuffer, bufferOffset, (short) metadataLength);

                if (topicNameLength > 0)
                {
                    writeBuffer.putBytes(sourceEventLogStreamTopicNameOffset(bufferOffset), sourceEventLogStreamTopicName, 0, topicNameLength);
                }

                if (metadataLength > 0)
                {
                    metadataWriter.write(writeBuffer, metadataOffset(bufferOffset, topicNameLength));
                }

                // write log entry
                valueWriter.write(writeBuffer, valueOffset(bufferOffset, topicNameLength, metadataLength));

                result = claimedPosition;
                claimedFragment.commit();
            }
            catch (Exception e)
            {
                claimedFragment.abort();
                LangUtil.rethrowUnchecked(e);
            }
            finally
            {
                reset();
            }
        }

        return result;
    }

    private long claimLogEntry(final int valueLength, final int topicNameLength, final int metadataLength)
    {
        final int framedLength = valueLength + headerLength(topicNameLength, metadataLength);

        long claimedPosition = -1;

        do
        {
            claimedPosition = logWriteBuffer.claim(claimedFragment, framedLength, logId);
        }
        while (claimedPosition == RESULT_PADDING_AT_END_OF_PARTITION);

        return claimedPosition - DataFrameDescriptor.alignedLength(framedLength);
    }

}
