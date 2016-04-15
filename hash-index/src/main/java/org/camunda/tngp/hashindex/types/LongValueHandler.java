package org.camunda.tngp.hashindex.types;

import static uk.co.real_logic.agrona.BitUtil.*;

import org.camunda.tngp.hashindex.IndexValueHandler;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class LongValueHandler implements IndexValueHandler
{
    public long theValue;

    @Override
    public void readValue(DirectBuffer buffer, int offset, int length)
    {
        if(length < SIZE_OF_LONG)
        {
            throw new IllegalArgumentException("Cannot get long, length out of bounds.");
        }
        theValue = buffer.getLong(offset);
    }

    @Override
    public void writeValue(MutableDirectBuffer buffer, int offset, int length)
    {
        if(length < SIZE_OF_LONG)
        {
            throw new IllegalArgumentException("Cannot write long value: long size out of bounds");
        }
        buffer.putLong(offset, theValue);
    }
}