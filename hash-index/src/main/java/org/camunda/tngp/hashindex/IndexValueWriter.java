package org.camunda.tngp.hashindex;

import uk.co.real_logic.agrona.MutableDirectBuffer;
import static uk.co.real_logic.agrona.BitUtil.*;

public interface IndexValueWriter
{
    void writeValue(MutableDirectBuffer buffer, int offset, int length);

    class LongValueWriter implements IndexValueWriter
    {
        public long theValue;

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

    class IntValueWriter implements IndexValueWriter
    {
        public int theValue;

        @Override
        public void writeValue(MutableDirectBuffer buffer, int offset, int length)
        {
            if(length < SIZE_OF_INT)
            {
                throw new IllegalArgumentException("Cannot write int value: int size out of bounds");
            }
            buffer.putInt(offset, theValue);
        }
    }
}