package org.camunda.tngp.broker.test.util;

import java.util.Arrays;

import org.agrona.DirectBuffer;
import org.assertj.core.api.AbstractAssert;

public class BufferAssert extends AbstractAssert<BufferAssert, DirectBuffer>
{

    protected BufferAssert(DirectBuffer actual)
    {
        super(actual, BufferAssert.class);
    }

    public static BufferAssert assertThatBuffer(DirectBuffer buffer)
    {
        return new BufferAssert(buffer);
    }

    public BufferAssert hasBytes(byte[] expected, int position)
    {
        isNotNull();

        final byte[] actualBytes = new byte[expected.length];

        // TODO: try-catch in case buffer has not expected size
        actual.getBytes(position, actualBytes, 0, actualBytes.length);

        if (!Arrays.equals(expected, actualBytes))
        {
            failWithMessage("Expected byte array match bytes <%s> but was <%s>", Arrays.toString(expected), Arrays.toString(actualBytes));
        }

        return this;
    }

    public BufferAssert hasBytes(byte[] expected)
    {
        return hasBytes(expected, 0);
    }

    public BufferAssert hasBytes(DirectBuffer buffer, int offset, int length)
    {
        final byte[] bytes = new byte[length];
        buffer.getBytes(0, bytes);
        return hasBytes(bytes);
    }

    public BufferAssert hasBytes(DirectBuffer buffer)
    {
        return hasBytes(buffer, 0, buffer.capacity());
    }

    public BufferAssert hasCapacity(int expectedCapacity)
    {
        isNotNull();

        if (expectedCapacity != actual.capacity())
        {
            failWithMessage("Expected capacity " + expectedCapacity + " but was " + actual.capacity());
        }

        return this;
    }

}
