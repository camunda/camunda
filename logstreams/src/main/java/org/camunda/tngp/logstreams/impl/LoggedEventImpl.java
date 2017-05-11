package org.camunda.tngp.logstreams.impl;

import org.agrona.DirectBuffer;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.util.buffer.BufferReader;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.headerLength;

/**
 * Represents the implementation of the logged event.
 */
public class LoggedEventImpl implements ReadableFragment, LoggedEvent
{
    protected int fragmentOffset = -1;
    protected int messageOffset = -1;
    protected DirectBuffer buffer;

    public void wrap(final DirectBuffer buffer, final int offset)
    {
        this.fragmentOffset = offset;
        this.messageOffset = messageOffset(fragmentOffset);
        this.buffer = buffer;
    }

    @Override
    public int getType()
    {
        return buffer.getShort(typeOffset(fragmentOffset));
    }

    @Override
    public int getVersion()
    {
        return buffer.getShort(versionOffset(fragmentOffset));
    }

    @Override
    public int getMessageLength()
    {
        return buffer.getInt(lengthOffset(fragmentOffset));
    }

    @Override
    public int getMessageOffset()
    {
        return messageOffset;
    }

    @Override
    public int getStreamId()
    {
        return buffer.getInt(streamIdOffset(fragmentOffset));
    }

    @Override
    public DirectBuffer getBuffer()
    {
        return buffer;
    }

    public int getFragmentLength()
    {
        return LogEntryDescriptor.getFragmentLength(buffer, fragmentOffset);
    }

    public int getFragmentOffset()
    {
        return fragmentOffset;
    }

    @Override
    public long getPosition()
    {
        return LogEntryDescriptor.getPosition(buffer, fragmentOffset);
    }

    @Override
    public long getKey()
    {
        return LogEntryDescriptor.getKey(buffer, messageOffset);
    }

    @Override
    public int getSourceEventLogStreamTopicNameOffset()
    {
        return LogEntryDescriptor.sourceEventLogStreamTopicNameOffset(messageOffset);
    }

    @Override
    public short getSourceEventLogStreamTopicNameLength()
    {
        return LogEntryDescriptor.getSourceEventLogStreamTopicNameLength(buffer, messageOffset);
    }

    @Override
    public DirectBuffer getSourceEventLogStreamTopicName()
    {
        return buffer;
    }

    @Override
    public void readSourceEventLogStreamTopicName(final BufferReader reader)
    {
        reader.wrap(buffer, getSourceEventLogStreamTopicNameOffset(), getSourceEventLogStreamTopicNameLength());
    }

    @Override
    public DirectBuffer getMetadata()
    {
        return buffer;
    }

    @Override
    public short getMetadataLength()
    {
        return LogEntryDescriptor.getMetadataLength(buffer, messageOffset);
    }

    @Override
    public int getMetadataOffset()
    {
        final short topicNameLength = getSourceEventLogStreamTopicNameLength();
        return LogEntryDescriptor.metadataOffset(messageOffset, topicNameLength);
    }

    @Override
    public void readMetadata(final BufferReader reader)
    {
        reader.wrap(buffer, getMetadataOffset(), getMetadataLength());
    }

    @Override
    public int getValueOffset()
    {
        final short topicNameLength = getSourceEventLogStreamTopicNameLength();
        final short metadataLength = getMetadataLength();
        return LogEntryDescriptor.valueOffset(messageOffset, topicNameLength, metadataLength);
    }

    @Override
    public int getValueLength()
    {
        final short topicNameLength = getSourceEventLogStreamTopicNameLength();
        final short metadataLength = getMetadataLength();

        return getMessageLength() - headerLength(topicNameLength, metadataLength);
    }

    @Override
    public DirectBuffer getValueBuffer()
    {
        return buffer;
    }

    @Override
    public void readValue(final BufferReader reader)
    {
        reader.wrap(buffer, getValueOffset(), getValueLength());
    }

    @Override
    public int getSourceEventLogStreamPartitionId()
    {
        return LogEntryDescriptor.getSourceEventLogStreamPartitionId(buffer, messageOffset);
    }

    @Override
    public long getSourceEventPosition()
    {
        return LogEntryDescriptor.getSourceEventPosition(buffer, messageOffset);
    }

    @Override
    public int getProducerId()
    {
        return LogEntryDescriptor.getProducerId(buffer, messageOffset);
    }

    @Override
    public String toString()
    {
        return "LoggedEvent [type=" +
            getType() +
            ", version=" +
            getVersion() +
            ", streamId=" +
            getStreamId() +
            ", position=" +
            getPosition() +
            ", key=" +
            getKey() +
            ", sourceEventLogStreamPartitionId=" +
            getSourceEventLogStreamPartitionId() +
            ", sourceEventPosition=" +
            getSourceEventPosition() +
            ", producerId=" +
            getProducerId() +
            "]";
    }
}
