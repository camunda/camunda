package org.camunda.tngp.hashindex;

import uk.co.real_logic.agrona.DirectBuffer;
import static uk.co.real_logic.agrona.BitUtil.*;

public interface IndexValueReader
{
    void readValue(DirectBuffer buffer, int offset, int length);

    class LongValueReader implements IndexValueReader
    {
        long theValue;

        @Override
        public void readValue(DirectBuffer buffer, int offset, int length)
        {
            if(length < SIZE_OF_LONG)
            {
                throw new IllegalArgumentException("Cannot get long, length out of bounds.");
            }
            theValue = buffer.getLong(offset);
        }
    }

    class IntValueReader implements IndexValueReader
    {
        int theValue;

        @Override
        public void readValue(DirectBuffer buffer, int offset, int length)
        {
            if(length < SIZE_OF_INT)
            {
                throw new IllegalArgumentException("Cannot get int, length out of bounds.");
            }
            theValue = buffer.getInt(offset);
        }
    }
}