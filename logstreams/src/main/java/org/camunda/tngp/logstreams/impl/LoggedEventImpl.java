package org.camunda.tngp.logstreams.impl;

import org.agrona.DirectBuffer;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.util.buffer.BufferReader;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.*;

/**
 * Represents the implementation of the logged event.
 */
public class LoggedEventImpl implements ReadableFragment, LoggedEvent
{
    protected int fragmentOffset = -1;
    protected DirectBuffer buffer;

    public void wrap(DirectBuffer buffer, int offset)
    {
        this.fragmentOffset = offset;
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
        return messageOffset(fragmentOffset);
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
        return alignedLength(getMessageLength());
    }

    public int getFragementOffset()
    {
        return fragmentOffset;
    }

    @Override
    public long getPosition()
    {
        return buffer.getLong(positionOffset(messageOffset(fragmentOffset)));
    }

    private int getKeyLength(final int entryHeaderOffset)
    {
        return buffer.getShort(keyLengthOffset(entryHeaderOffset));
    }

    @Override
    public long getLongKey()
    {
        final int entryHeaderOffset = messageOffset(fragmentOffset);

        return buffer.getLong(keyOffset(entryHeaderOffset));
    }

    @Override
    public DirectBuffer getMetadata()
    {
        return buffer;
    }

    @Override
    public int getMetadataOffset()
    {
        final int entryHeaderOffset = messageOffset(fragmentOffset);
        final int keyLength = getKeyLength(entryHeaderOffset);

        return metadataOffset(entryHeaderOffset, keyLength);
    }

    @Override
    public short getMetadataLength()
    {
        final int entryHeaderOffset = messageOffset(fragmentOffset);
        final int keyLength = getKeyLength(entryHeaderOffset);

        return buffer.getShort(metadataLengthOffset(entryHeaderOffset, keyLength));
    }

    @Override
    public void readMetadata(BufferReader reader)
    {
        reader.wrap(buffer, getMetadataOffset(), getMetadataLength());
    }

    @Override
    public DirectBuffer getValueBuffer()
    {
        return buffer;
    }

    @Override
    public int getValueOffset()
    {
        final int entryHeaderOffset = messageOffset(fragmentOffset);
        final int keyLength = getKeyLength(entryHeaderOffset);
        final short metadataLength = getMetadataLength();

        return valueOffset(entryHeaderOffset, keyLength, metadataLength);
    }

    @Override
    public int getValueLength()
    {
        final int entryHeaderOffset = messageOffset(fragmentOffset);
        final int keyLength = getKeyLength(entryHeaderOffset);
        final short metadataLength = getMetadataLength();

        return buffer.getInt(lengthOffset(fragmentOffset)) - headerLength(keyLength, metadataLength);
    }

    @Override
    public void readValue(BufferReader reader)
    {
        reader.wrap(buffer, getValueOffset(), getValueLength());
    }

    @Override
    public int getSourceEventLogStreamId()
    {
        return buffer.getInt(sourceEventLogStreamIdOffset(messageOffset(fragmentOffset)));
    }

    @Override
    public long getSourceEventPosition()
    {
        return buffer.getLong(sourceEventPositionOffset(messageOffset(fragmentOffset)));
    }

    @Override
    public int getProducerId()
    {
        return buffer.getInt(producerIdOffset(messageOffset(fragmentOffset)));
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("LoggedEvent [type=");
        builder.append(getType());
        builder.append(", version=");
        builder.append(getVersion());
        builder.append(", streamId=");
        builder.append(getStreamId());
        builder.append(", position=");
        builder.append(getPosition());
        builder.append(", longKey=");
        builder.append(getLongKey());
        builder.append(", sourceEventLogStreamId=");
        builder.append(getSourceEventLogStreamId());
        builder.append(", sourceEventPosition=");
        builder.append(getSourceEventPosition());
        builder.append(", producerId=");
        builder.append(getProducerId());
        builder.append("]");
        return builder.toString();
    }
}
